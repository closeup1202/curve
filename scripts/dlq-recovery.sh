#!/bin/bash
#
# DLQ 복구 스크립트 - 로컬 백업 파일을 Kafka로 재전송
#
# 사용법:
#   ./dlq-recovery.sh [옵션]
#
# 옵션:
#   -d, --dir <경로>         백업 파일 디렉토리 (기본: ./dlq-backup)
#   -t, --topic <토픽명>     재전송할 Kafka 토픽 (기본: event.audit.v1)
#   -b, --broker <주소>      Kafka 브로커 주소 (기본: localhost:9094)
#   -f, --file <파일명>      단일 파일만 재전송
#   -l, --list               백업 파일 목록만 출력
#   -h, --help               도움말 출력
#
# 예시:
#   # 모든 백업 파일 목록 확인
#   ./dlq-recovery.sh --list
#
#   # 모든 백업 파일을 event.audit.v1 토픽으로 재전송
#   ./dlq-recovery.sh --topic event.audit.v1 --broker localhost:9094
#
#   # 특정 파일만 재전송
#   ./dlq-recovery.sh --file 1234567890.json --topic event.audit.v1
#

set -euo pipefail

# 기본 설정
BACKUP_DIR="./dlq-backup"
TOPIC="event.audit.v1"
BROKER="localhost:9094"
SPECIFIC_FILE=""
LIST_ONLY=false

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 도움말 출력
print_help() {
    sed -n '2,/^$/p' "$0" | grep '^#' | sed 's/^# \?//'
}

# 옵션 파싱
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--dir)
            BACKUP_DIR="$2"
            shift 2
            ;;
        -t|--topic)
            TOPIC="$2"
            shift 2
            ;;
        -b|--broker)
            BROKER="$2"
            shift 2
            ;;
        -f|--file)
            SPECIFIC_FILE="$2"
            shift 2
            ;;
        -l|--list)
            LIST_ONLY=true
            shift
            ;;
        -h|--help)
            print_help
            exit 0
            ;;
        *)
            log_error "알 수 없는 옵션: $1"
            print_help
            exit 1
            ;;
    esac
done

# kafka-console-producer 존재 확인
if ! command -v kafka-console-producer.sh &> /dev/null; then
    log_error "kafka-console-producer.sh를 찾을 수 없습니다."
    log_error "Kafka 설치 경로를 PATH에 추가하거나, Kafka bin 디렉토리에서 실행하세요."
    exit 1
fi

# 백업 디렉토리 존재 확인
if [ ! -d "$BACKUP_DIR" ]; then
    log_error "백업 디렉토리를 찾을 수 없습니다: $BACKUP_DIR"
    exit 1
fi

# 백업 파일 목록 가져오기
if [ -n "$SPECIFIC_FILE" ]; then
    if [ ! -f "$BACKUP_DIR/$SPECIFIC_FILE" ]; then
        log_error "파일을 찾을 수 없습니다: $BACKUP_DIR/$SPECIFIC_FILE"
        exit 1
    fi
    FILES=("$BACKUP_DIR/$SPECIFIC_FILE")
else
    mapfile -t FILES < <(find "$BACKUP_DIR" -type f -name "*.json" | sort)
fi

# 파일 개수 확인
FILE_COUNT=${#FILES[@]}

if [ "$FILE_COUNT" -eq 0 ]; then
    log_warn "복구할 백업 파일이 없습니다."
    exit 0
fi

log_info "발견된 백업 파일: $FILE_COUNT개"
echo

# 목록만 출력
if [ "$LIST_ONLY" = true ]; then
    log_info "백업 파일 목록:"
    printf "%-5s %-30s %-20s %s\n" "No" "파일명" "크기" "수정 시간"
    printf "%-5s %-30s %-20s %s\n" "---" "---" "---" "---"

    i=1
    for file in "${FILES[@]}"; do
        filename=$(basename "$file")
        size=$(du -h "$file" | cut -f1)
        mtime=$(stat -c '%y' "$file" 2>/dev/null || stat -f '%Sm' "$file")
        printf "%-5s %-30s %-20s %s\n" "$i" "$filename" "$size" "${mtime:0:19}"
        ((i++))
    done
    exit 0
fi

# 복구 시작
log_info "설정:"
log_info "  백업 디렉토리: $BACKUP_DIR"
log_info "  Kafka 브로커: $BROKER"
log_info "  대상 토픽: $TOPIC"
log_info "  복구할 파일 수: $FILE_COUNT"
echo

read -p "계속 진행하시겠습니까? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    log_warn "복구 취소됨"
    exit 0
fi

# 복구 통계
SUCCESS_COUNT=0
FAIL_COUNT=0
FAILED_FILES=()

# 각 파일 복구
for file in "${FILES[@]}"; do
    filename=$(basename "$file")
    log_info "복구 중: $filename"

    # JSON 유효성 검사
    if ! jq empty "$file" 2>/dev/null; then
        log_error "  ├─ JSON 형식 오류: $filename"
        ((FAIL_COUNT++))
        FAILED_FILES+=("$filename (JSON 형식 오류)")
        continue
    fi

    # eventId 추출 (파일명에서 .json 제거)
    EVENT_ID="${filename%.json}"

    # Kafka로 재전송
    if cat "$file" | kafka-console-producer.sh \
        --broker-list "$BROKER" \
        --topic "$TOPIC" \
        --property "key.separator=:" \
        --property "parse.key=true" \
        <<< "$EVENT_ID:$(cat "$file")" 2>/dev/null; then

        log_info "  ├─ ✓ 재전송 성공: $EVENT_ID"
        ((SUCCESS_COUNT++))

        # 성공한 파일 백업 디렉토리 이동 (선택사항)
        # mv "$file" "${file}.recovered"
    else
        log_error "  ├─ ✗ 재전송 실패: $EVENT_ID"
        ((FAIL_COUNT++))
        FAILED_FILES+=("$filename")
    fi
done

# 결과 출력
echo
log_info "========== 복구 완료 =========="
log_info "총 파일 수: $FILE_COUNT"
log_info "성공: ${GREEN}$SUCCESS_COUNT${NC}"
log_info "실패: ${RED}$FAIL_COUNT${NC}"

if [ "$FAIL_COUNT" -gt 0 ]; then
    echo
    log_warn "실패한 파일 목록:"
    for failed_file in "${FAILED_FILES[@]}"; do
        echo "  - $failed_file"
    done
    exit 1
fi

log_info "모든 파일이 성공적으로 복구되었습니다."
exit 0
