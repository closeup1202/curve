# Curve 운영 가이드

이 문서는 Curve 이벤트 발행 시스템의 모니터링, 트러블슈팅, 복구 절차를 설명합니다.

## 목차

- [DLQ 모니터링](#dlq-모니터링)
- [메트릭 해석](#메트릭-해석)
- [트러블슈팅 매트릭스](#트러블슈팅-매트릭스)
- [복구 절차](#복구-절차)
- [알림 설정](#알림-설정)
- [Runbook 체크리스트](#runbook-체크리스트)

---

## DLQ 모니터링

### 3단계 장애 복구 이해

Curve는 이벤트 유실을 방지하기 위해 3단계 장애 복구 시스템을 구현합니다:

```
이벤트 전송 시도
        │
        ▼
┌─────────────────┐
│  1단계: 메인    │──── 성공 ───▶ 이벤트 발행됨
│     토픽        │
└────────┬────────┘
         │ 실패
         ▼
┌─────────────────┐
│  2단계: DLQ     │──── 성공 ───▶ DLQ 토픽에 저장
│     토픽        │
└────────┬────────┘
         │ 실패
         ▼
┌─────────────────┐
│ 3단계: 로컬     │──── 성공 ───▶ JSON 파일 백업
│     백업        │
└────────┬────────┘
         │ 실패
         ▼
    이벤트 유실 + 알림
```

| 단계 | 구성 요소 | 트리거 | 설명 |
|------|-----------|---------|-------------|
| 1 | 메인 토픽 | 정상 동작 | 설정된 Kafka 토픽으로 이벤트 발행 |
| 2 | DLQ 토픽 | 메인 토픽 실패 | 실패한 이벤트를 Dead Letter Queue로 전송 |
| 3 | 로컬 파일 | DLQ 실패 | 이벤트를 `./dlq-backup/` 디렉토리에 백업 |

### DLQ 이벤트 모니터링

#### Kafka UI 사용

1. Kafka UI 접속 (기본: http://localhost:8080)
2. 메뉴에서 Topics 선택
3. `event.audit.dlq.v1` (또는 설정된 DLQ 토픽) 찾기
4. Messages 탭에서 실패한 이벤트 확인

#### Actuator 엔드포인트 사용

```bash
# DLQ 메트릭 조회
curl http://localhost:8081/actuator/curve-metrics | jq '.summary'
```

**응답:**
```json
{
  "totalEventsPublished": 1523,
  "successfulEvents": 1520,
  "failedEvents": 3,
  "successRate": "99.80%",
  "totalDlqEvents": 3,
  "totalKafkaErrors": 0
}
```

#### Kafka CLI 사용

```bash
# DLQ 토픽 메시지 수 확인
kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic event.audit.dlq.v1

# DLQ 메시지 소비
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic event.audit.dlq.v1 \
  --from-beginning
```

### DLQ 메시지 구조

```json
{
  "eventId": "123456789012345678",
  "originalTopic": "event.audit.v1",
  "originalPayload": "{\"eventType\":\"ORDER_CREATED\",...}",
  "exceptionType": "org.apache.kafka.common.errors.TimeoutException",
  "exceptionMessage": "Failed to send message after 3 retries",
  "failedAt": 1704067200000
}
```

| 필드 | 설명 |
|-------|-------------|
| `eventId` | 고유 이벤트 식별자 (Snowflake ID) |
| `originalTopic` | 이벤트가 전송되려던 토픽 |
| `originalPayload` | 전체 이벤트 페이로드 (JSON 문자열) |
| `exceptionType` | 실패를 유발한 Java 예외 클래스 |
| `exceptionMessage` | 사람이 읽을 수 있는 에러 메시지 |
| `failedAt` | 실패 발생 시각 (epoch milliseconds) |

### 로컬 백업 파일

**위치:** `./dlq-backup/` (`curve.kafka.dlq-backup-path`로 설정 가능)

```bash
# 백업 파일 목록
ls -la ./dlq-backup/

# 예시 출력:
# -rw------- 1 user user 2048 Jan 20 10:30 123456789012345678.json
# -rw------- 1 user user 1856 Jan 20 10:31 123456789012345679.json
```

**파일명:** `{eventId}.json`

**파일 권한:**
- POSIX 시스템: `600` (rw-------)
- Windows: 소유자만 접근 가능한 ACL

---

## 메트릭 해석

### 메트릭 접근

```bash
# 전체 메트릭 리포트
curl http://localhost:8081/actuator/curve-metrics

# 요약 정보만
curl http://localhost:8081/actuator/curve-metrics | jq '.summary'

# 특정 메트릭
curl http://localhost:8081/actuator/curve-metrics | jq '.events.published'
```

### 주요 메트릭 참조

| 메트릭 | 설명 | 경고 임계값 | 위험 임계값 |
|--------|-------------|-------------------|-------------------|
| `successRate` | 이벤트 발행 성공률 | < 99% | < 95% |
| `totalDlqEvents` | DLQ로 전송된 이벤트 수 | > 0 | > 10 (증가 추세) |
| `totalKafkaErrors` | Kafka 프로듀서 에러 수 | > 0 | > 5 |
| `curve.events.retry.count` | 재시도 횟수 | 증가함 | 급격히 증가함 |
| `curve.events.publish.duration` | 발행 지연 시간 | > 100ms 평균 | > 500ms 평균 |

### 상태 해석

| 상태 | 지표 | 의미 | 조치 |
|--------|------------|---------|--------|
| **정상** | successRate >= 99.5%, totalDlqEvents = 0 | 정상 동작 | 모니터링 |
| **경고** | successRate 95-99.5%, totalDlqEvents > 0 안정적 | 간헐적 문제 | 조사 필요 |
| **위험** | successRate < 95%, totalDlqEvents 증가 중 | 시스템 장애 | 즉시 조치 |

### Outbox Publisher 메트릭

Transactional Outbox 패턴 사용자용:

| 메트릭 | 설명 | 비정상 시 조치 |
|--------|-------------|-------------------|
| `circuitBreakerState` | CLOSED/OPEN/HALF-OPEN | OPEN = Kafka 연결 문제 |
| `consecutiveFailures` | 연속 실패 횟수 | > 3 = 서킷 브레이커 열릴 가능성 |
| `timeSinceLastSuccessMs` | 마지막 성공 이후 시간 | > 60000 = Kafka 확인 |
| `totalPending` | 대기 중인 outbox 이벤트 | 0으로 수렴해야 함 |
| `totalFailed` | 영구적으로 실패한 이벤트 | 수동 개입 필요 |

### 서킷 브레이커 상태

| 상태 | 동작 | 지속 시간 | 전이 |
|-------|----------|----------|------------|
| **CLOSED** | 정상 동작 | - | 5회 연속 실패 시 열림 |
| **OPEN** | 모든 요청 차단 | 60초 | HALF-OPEN으로 전이 |
| **HALF-OPEN** | 테스트 요청 허용 | 성공/실패 시까지 | 성공→CLOSED, 실패→OPEN |

---

## 트러블슈팅 매트릭스

### 증상 및 해결책

| 증상 | 가능한 원인 | 확인 방법 | 해결책 |
|---------|---------------|--------------|----------|
| 이벤트 발행 안 됨 | AOP 비활성화 | 설정 `curve.aop.enabled` 확인 | `true`로 설정 |
| 이벤트 발행 안 됨 | 메서드가 public 아님 | 메서드 시그니처 확인 | 메서드를 `public`으로 변경 |
| `TimeoutException` | Kafka 응답 없음 | `docker-compose ps kafka` | Kafka 재시작 |
| `TimeoutException` | 네트워크 지연 | 브로커 핑 테스트 | `request-timeout-ms` 증가 |
| 높은 DLQ 카운트 | Kafka 브로커 다운 | 브로커 로그 확인 | Kafka 복구, DLQ 복구 |
| 서킷 브레이커 OPEN | 5회 이상 연속 실패 | Kafka 상태 확인 | 60초 대기 또는 Kafka 수정 |
| 로컬 백업 파일 존재 | 메인 및 DLQ 모두 실패 | 모든 Kafka 연결 확인 | 수동 복구 필요 |
| PII 암호화 에러 | 암호화 키 누락 | `PII_ENCRYPTION_KEY` 환경변수 확인 | 환경 변수 설정 |
| Worker ID 충돌 | 중복된 Worker ID | 인스턴스 설정 확인 | 고유 ID 할당 |
| Outbox 이벤트 PENDING 고착 | Kafka 도달 불가 | 서킷 브레이커 상태 확인 | Kafka 연결 수정 |
| 느린 이벤트 발행 | 높은 부하에서 동기 모드 | `async-mode` 확인 | 비동기 모드 활성화 |
| `ClockMovedBackwardsException` | 시스템 시간 변경 | NTP 동기화 확인 | 애플리케이션 재시작 |

### 일반적인 에러 메시지

| 에러 메시지 | 원인 | 해결책 |
|--------------|-------|----------|
| `Kafka topic is required` | 토픽 설정 누락 | `curve.kafka.topic` 설정 |
| `workerId must be between 0 and 1023` | 잘못된 Worker ID | 유효한 범위 사용 |
| `PII encryption key is not configured` | 암호화 키 누락 | `PII_ENCRYPTION_KEY` 환경변수 설정 |
| `Failed to send message after N retries` | Kafka 연결 문제 | 브로커 상태 확인 |
| `Circuit breaker is OPEN` | 너무 많은 연속 실패 | Half-open 대기 또는 Kafka 수정 |

### 헬스 체크 응답

```bash
curl http://localhost:8081/actuator/health/curve
```

| 상태 | 상세 | 의미 | 조치 |
|--------|---------|---------|--------|
| UP | `clusterId`, `nodeCount` 포함 | 정상, 브로커 연결됨 | 없음 |
| DOWN | 에러 메시지 | 브로커 도달 불가 또는 연결 문제 | Kafka 설정 및 네트워크 확인 |

---

## 복구 절차

### 절차 1: DLQ 이벤트 복구

**사용 시기:** 일시적인 Kafka 문제가 해결된 후 DLQ 토픽에 이벤트가 쌓여있을 때.

**전제 조건:**
- Kafka가 정상 상태임
- `kafka-console-producer.sh`가 PATH에 있음
- DLQ 토픽 접근 가능

**단계:**

1. **Kafka 상태 확인:**
```bash
# Kafka 컨테이너 확인
docker-compose ps kafka

# Curve 헬스 체크
curl http://localhost:8081/actuator/health/curve
```

2. **복구할 DLQ 이벤트 목록 확인:**
```bash
./scripts/dlq-recovery.sh --list
```

3. **복구 실행:**
```bash
./scripts/dlq-recovery.sh \
  --topic event.audit.v1 \
  --broker localhost:9092 \
  --dir ./dlq-backup
```

4. **특정 파일 복구:**
```bash
./scripts/dlq-recovery.sh \
  --file 123456789012345678.json \
  --topic event.audit.v1 \
  --broker localhost:9092
```

5. **복구 확인:**
- Kafka UI에서 복구된 이벤트 확인
- 백업 파일이 처리되었는지 확인 (`recovered/` 하위 디렉토리로 이동됨)

---

### 절차 2: 로컬 백업 파일 복구

**사용 시기:** 메인 토픽과 DLQ 모두 실패하여 로컬 파일에 백업되었을 때.

**단계:**

1. **백업 파일 목록 확인:**
```bash
ls -la ./dlq-backup/*.json
```

2. **JSON 형식 검증:**
```bash
# 모든 파일 확인
for f in ./dlq-backup/*.json; do
  jq empty "$f" 2>/dev/null || echo "Invalid: $f"
done
```

3. **복구 스크립트 사용:**
```bash
./scripts/dlq-recovery.sh \
  --dir ./dlq-backup \
  --topic event.audit.v1 \
  --broker localhost:9092
```

4. **수동 복구 (스크립트 실패 시):**
```bash
# 각 백업 파일에 대해
EVENT_ID="123456789012345678"

cat ./dlq-backup/${EVENT_ID}.json | \
  kafka-console-producer.sh \
  --broker-list localhost:9092 \
  --topic event.audit.v1
```

5. **복구된 파일 아카이빙:**
```bash
mkdir -p ./dlq-backup/recovered
mv ./dlq-backup/*.json ./dlq-backup/recovered/
```

---

### 절차 3: Outbox 이벤트 복구

**사용 시기:** 서킷 브레이커 문제 후 Outbox 이벤트가 FAILED 상태로 고착되었을 때.

**단계:**

1. **Outbox 통계 확인:**
```bash
curl http://localhost:8081/actuator/curve-metrics | jq '.summary'
```

2. **실패한 이벤트 조회 (DB 접근 필요):**
```sql
-- 실패한 이벤트 목록
SELECT id, event_id, aggregate_type, aggregate_id, status, retry_count, last_error
FROM curve_outbox_event
WHERE status = 'FAILED'
ORDER BY occurred_at DESC
LIMIT 100;

-- 상태별 카운트
SELECT status, COUNT(*) as count
FROM curve_outbox_event
GROUP BY status;
```

3. **재시도를 위해 실패한 이벤트 리셋:**
```sql
-- 특정 이벤트 리셋
UPDATE curve_outbox_event
SET status = 'PENDING', retry_count = 0, last_error = NULL, next_retry_at = NOW()
WHERE id = 'specific-event-id';

-- 모든 실패 이벤트 리셋 (주의해서 사용)
UPDATE curve_outbox_event
SET status = 'PENDING', retry_count = 0, last_error = NULL, next_retry_at = NOW()
WHERE status = 'FAILED';
```

4. **복구 모니터링:**
```bash
watch -n 5 'curl -s http://localhost:8081/actuator/curve-metrics | jq ".summary"'
```

---

### 절차 4: 서킷 브레이커 리셋

**사용 시기:** Kafka 복구 후에도 서킷 브레이커가 OPEN 상태로 유지될 때.

**단계:**

1. **Kafka 정상 확인:**
```bash
curl http://localhost:8081/actuator/health/curve
```

2. **서킷 브레이커 상태 확인:**
```bash
curl http://localhost:8081/actuator/curve-metrics | jq '.summary.circuitBreakerState'
```

3. **자동 Half-Open 대기 (60초)**

   서킷 브레이커는 60초 후 자동으로 HALF-OPEN 상태로 전환되어 테스트 요청을 허용합니다.

4. **대안: 애플리케이션 재시작:**
```bash
# 우아한 종료
kill -TERM $(pgrep -f 'your-application')

# 또는 actuator 사용 (활성화된 경우)
curl -X POST http://localhost:8081/actuator/shutdown
```

5. **상태 전이 모니터링:**
```bash
watch -n 10 'curl -s http://localhost:8081/actuator/curve-metrics | jq ".summary.circuitBreakerState"'
```

---

## 알림 설정

### Prometheus 알림 규칙

```yaml
groups:
  - name: curve-alerts
    rules:
      # DLQ 이벤트 알림
      - alert: CurveDlqEventsHigh
        expr: curve_events_dlq_count_total > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "높은 DLQ 이벤트 수"
          description: "{{ $value }}개의 이벤트가 DLQ에 쌓였습니다."

      # 성공률 알림
      - alert: CurveSuccessRateLow
        expr: (curve_events_published_success_total / curve_events_published_total) < 0.95
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "낮은 이벤트 발행 성공률"
          description: "성공률이 {{ $value | humanizePercentage }} 입니다."

      # 서킷 브레이커 알림
      - alert: CurveCircuitBreakerOpen
        expr: curve_circuit_breaker_state == 1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "서킷 브레이커 OPEN"
          description: "Outbox 퍼블리셔 서킷 브레이커가 열려 이벤트가 발행되지 않고 있습니다."

      # Kafka 프로듀서 다운
      - alert: CurveKafkaProducerDown
        expr: curve_health_status == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Curve Kafka 프로듀서 다운"
          description: "Kafka 프로듀서 초기화 실패 또는 비정상 상태입니다."

      # 높은 지연 시간 알림
      - alert: CurvePublishLatencyHigh
        expr: histogram_quantile(0.95, curve_events_publish_duration_seconds_bucket) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "높은 이벤트 발행 지연 시간"
          description: "95분위 지연 시간이 {{ $value }}초 입니다."

      # Outbox 백로그 알림
      - alert: CurveOutboxBacklogHigh
        expr: curve_outbox_pending_total > 1000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "높은 Outbox 백로그"
          description: "{{ $value }}개의 이벤트가 Outbox에 대기 중입니다."
```

### Grafana 대시보드 패널

Curve 모니터링 대시보드 추천 패널:

1. **이벤트 발행률** - `rate(curve_events_published_total[5m])`
2. **성공률 게이지** - 현재 성공 백분율
3. **DLQ 이벤트 수** - `curve_events_dlq_count_total` 시간별 추이
4. **발행 지연 시간** - `histogram_quantile(0.95, curve_events_publish_duration_seconds_bucket)`
5. **서킷 브레이커 상태** - 현재 상태 표시기 (CLOSED/OPEN/HALF-OPEN)
6. **Outbox 큐 깊이** - `curve_outbox_pending_total` 시간별 추이
7. **재시도 횟수** - `rate(curve_events_retry_count_total[5m])`
8. **Kafka 에러** - `curve_kafka_producer_errors_total` 시간별 추이

---

## Runbook 체크리스트

### 일일 운영

- [ ] `/actuator/health/curve` 상태 확인
- [ ] `/actuator/curve-metrics` 요약 검토
- [ ] DLQ 토픽이 비어있거나 안정적인지 확인
- [ ] `./dlq-backup/`에 로컬 백업 파일이 있는지 확인
- [ ] 애플리케이션 로그에서 WARN/ERROR 항목 검토

### 주간 운영

- [ ] DLQ 이벤트 패턴 및 근본 원인 분석
- [ ] 발행 지연 시간 추세 분석
- [ ] Outbox 정리 작업(cleanup job) 성공 여부 확인
- [ ] 오래된 백업 파일 아카이빙 (있는 경우)
- [ ] 로그 검토 및 로테이션 확인

### 사고 대응

- [ ] 영향 받은 시간 범위 식별
- [ ] 서킷 브레이커 상태 이력 확인
- [ ] DLQ 및 로컬 백업의 이벤트 수 확인
- [ ] 근본 원인 파악 (Kafka, 네트워크, 설정)
- [ ] 적절한 복구 절차 실행
- [ ] 소비자에게 이벤트 전달 확인
- [ ] 사후 분석(Post-mortem) 문서화

### 월간 운영

- [ ] 알림 임계값 검토 및 조정
- [ ] 성공률 추세 분석
- [ ] 이벤트 볼륨 기반 용량 계획
- [ ] 필요 시 이 Runbook 업데이트

---

## 추가 리소스

- [설정 가이드](CONFIGURATION.ko.md) - 상세 설정 옵션
- [DLQ 복구 스크립트](../scripts/dlq-recovery.sh) - 자동화된 복구 도구
- [샘플 애플리케이션](../sample/) - 작동 예시
- [README](../README.ko.md) - 프로젝트 개요 및 빠른 시작
