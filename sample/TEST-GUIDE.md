# Curve Sample 애플리케이션 테스트 가이드

## 사전 준비

### 1. Kafka 실행
Curve는 Kafka 기반 이벤트 발행 라이브러리이므로, **Kafka가 반드시 실행 중**이어야 합니다.

```bash
# Docker Compose로 Kafka 실행 (가장 간단)
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_CFG_NODE_ID=1 \
  -e KAFKA_CFG_PROCESS_ROLES=controller,broker \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  bitnami/kafka:latest
```

### 2. Sample 애플리케이션 실행
```bash
# 프로젝트 루트에서
./gradlew :sample:bootRun
```
- 서버 포트: **8081**
- H2 콘솔: http://localhost:8081/h2-console (JDBC URL: `jdbc:h2:mem:curvedb`)

---

## 테스트 방법

`sample-request.html` 파일을 브라우저에서 열면 모든 API를 버튼 클릭으로 테스트할 수 있습니다.

---

## API 목록 및 테스트 순서

### 추천 테스트 순서

> **회원가입 → 로그인 → 주문 생성 → 주문 조회 → 상태 변경 → 주문 취소 → 결제 → 결제(재시도)**

이 순서대로 테스트하면 실제 서비스 흐름과 동일하게 확인할 수 있습니다.

---

### Step 1. 회원 서비스

| API | Method | URL | 설명 |
|-----|--------|-----|------|
| 회원가입 | POST | `/api/users/register` | 새 사용자 등록 |
| 로그인 | POST | `/api/users/login` | 등록한 이메일/비밀번호로 로그인 |

**확인 포인트:**
- 회원가입 후 `success: true` 응답
- 같은 이메일로 재가입 시 `400` 에러 (중복 방지)
- 로그인 성공/실패 응답 확인
- 이벤트: `USER_REGISTERED`, `USER_LOGIN` 발행 여부 (Kafka 로그 또는 앱 로그에서 확인)

---

### Step 2. 주문 서비스

| API | Method | URL | 설명 |
|-----|--------|-----|------|
| 주문 생성 | POST | `/api/orders` | 새 주문 생성 |
| 주문 조회 | GET | `/api/orders/{orderId}` | 생성된 주문 조회 |
| 상태 변경 | PATCH | `/api/orders/{orderId}/status?newStatus=PAID` | 주문 상태 변경 |
| 주문 취소 | POST | `/api/orders/{orderId}/cancel` | 주문 취소 |

**확인 포인트:**
- 주문 생성 후 응답의 `orderId` 복사 → 이후 API에서 사용
- 상태 변경: `PENDING → PAID → SHIPPED → DELIVERED` 순서
- 취소 시 `status: CANCELLED` 확인
- 이벤트: `ORDER_CREATED`, `ORDER_STATUS_CHANGED`, `ORDER_CANCELLED`
- PII 보호: 로그에서 고객 이름/이메일/전화번호가 마스킹/암호화 되는지 확인

---

### Step 3. 결제 서비스

| API | Method | URL | 설명 |
|-----|--------|-----|------|
| 결제 처리 | POST | `/api/payments` | 결제 요청 (일부 확률로 실패) |
| 결제 (재시도) | POST | `/api/payments/with-retry?maxRetries=3` | 실패 시 자동 재시도 |

**확인 포인트:**
- 결제 성공 시 `transactionId` 반환
- 결제 실패 시 `502` 에러 (게이트웨이 타임아웃 시뮬레이션)
- 재시도 API는 최대 N회 재시도 후 결과 반환
- 이벤트: `PAYMENT_SUCCESS` (outbox 패턴 사용), `PAYMENT_FAILED`

---

## 핵심 확인 항목

### 1. 이벤트 발행 확인
애플리케이션 로그에서 다음을 확인:
```
# 이벤트 발행 성공 로그 예시
Published event: ORDER_CREATED ...
Published event: USER_REGISTERED ...
```

### 2. PII 보호 확인
Kafka로 전송된 이벤트 메시지에서 개인정보가 처리되는지 확인:
- **MASK**: `John Doe` → `Joh**`
- **ENCRYPT**: `010-1234-5678` → 암호화된 값
- **HASH**: 비밀번호 → 해시값

### 3. Health Check
```
GET http://localhost:8081/actuator/health
```
Kafka 연결 상태, 클러스터 정보 등을 확인할 수 있습니다.

### 4. Curve Metrics
```
GET http://localhost:8081/actuator/curve-metrics
```
이벤트 발행 통계를 확인할 수 있습니다.

---

## 에러 상황 테스트

| 테스트 | 방법 | 기대 결과 |
|--------|------|-----------|
| 중복 회원가입 | 같은 이메일로 2번 가입 | 400 에러 |
| 없는 유저 로그인 | 가입 안 한 이메일로 로그인 | 401 에러 |
| 없는 주문 조회 | 임의의 UUID로 조회 | 404 에러 |
| 결제 실패 | 결제 API 여러 번 호출 (확률적 실패) | 502 에러 |
| Kafka 다운 시 | Kafka 중지 후 API 호출 | DLQ 또는 에러 로그 확인 |
