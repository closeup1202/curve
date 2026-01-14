# Curve 설정 가이드

이 문서는 Curve 이벤트 발행 라이브러리의 상세 설정 방법을 설명합니다.

## 목차

- [기본 설정](#기본-설정)
- [Worker ID 설정](#worker-id-설정)
- [Kafka 전송 모드 설정](#kafka-전송-모드-설정)
- [DLQ 설정](#dlq-설정)
- [Retry 설정](#retry-설정)
- [AOP 설정](#aop-설정)

---

## 기본 설정

### application.yml

```yaml
curve:
  enabled: true  # Curve 활성화 (기본값: true)

  kafka:
    topic: event.audit.v1  # 메인 토픽 이름
    dlq-topic: event.audit.dlq.v1  # DLQ 토픽 (선택사항)

  id-generator:
    worker-id: 1  # Snowflake Worker ID (0~1023)
    auto-generate: false  # MAC 주소 기반 자동 생성
```

---

## Worker ID 설정

Snowflake ID Generator는 분산 환경에서 고유한 ID를 생성하기 위해 Worker ID를 사용합니다.

### 방법 1: 명시적 Worker ID 설정 (권장)

각 인스턴스마다 고유한 Worker ID를 할당합니다.

```yaml
curve:
  id-generator:
    worker-id: 1  # 인스턴스 1
    auto-generate: false
```

**Kubernetes 환경 예시:**

```yaml
# deployment.yaml
env:
  - name: CURVE_ID_GENERATOR_WORKER_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.uid  # Pod UID를 해싱하여 사용
```

**Docker Compose 예시:**

```yaml
# docker-compose.yml
services:
  app-1:
    environment:
      - CURVE_ID_GENERATOR_WORKER_ID=1
  app-2:
    environment:
      - CURVE_ID_GENERATOR_WORKER_ID=2
```

### 방법 2: 자동 생성 (주의)

MAC 주소 기반으로 Worker ID를 자동 생성합니다.

```yaml
curve:
  id-generator:
    auto-generate: true
```

**⚠️ 주의사항:**
- 가상 환경에서는 MAC 주소가 동일할 수 있어 충돌 가능
- 컨테이너 재시작 시 MAC 주소 변경 가능
- 프로덕션 환경에서는 명시적 설정 권장

### Worker ID 범위

- **최소값:** 0
- **최대값:** 1023
- **권장:** 환경변수나 설정 관리 시스템(Consul, etcd)에서 관리

---

## Kafka 전송 모드 설정

Curve는 동기/비동기 두 가지 전송 모드를 지원합니다.

### 동기 전송 (기본값)

```yaml
curve:
  kafka:
    async-mode: false  # 동기 전송
    request-timeout-ms: 30000  # 30초
```

**특징:**
- ✅ 전송 보장 (확실한 성공/실패 확인)
- ✅ 에러 처리 용이
- ❌ 성능 저하 (블로킹)
- ❌ 처리량 제한

**적합한 경우:**
- 금융 거래, 결제 등 정확성이 중요한 경우
- 이벤트 손실이 허용되지 않는 경우
- 낮은 처리량 (수십~수백 TPS)

### 비동기 전송

```yaml
curve:
  kafka:
    async-mode: true  # 비동기 전송
    async-timeout-ms: 5000  # 5초 타임아웃
```

**특징:**
- ✅ 높은 성능 (non-blocking)
- ✅ 높은 처리량 가능
- ⚠️ 콜백 기반 에러 처리
- ⚠️ 전송 실패 시 DLQ 의존

**적합한 경우:**
- 로그, 분석 이벤트 등 일부 손실 허용
- 높은 처리량 필요 (수천~수만 TPS)
- 레이턴시가 중요한 경우

### 성능 비교

| 항목 | 동기 전송 | 비동기 전송 |
|------|-----------|-------------|
| 처리량 (TPS) | ~500 | ~10,000+ |
| 레이턴시 | 높음 (10-50ms) | 낮음 (1-5ms) |
| 전송 보장 | 강함 | 보통 (DLQ 의존) |
| 리소스 사용 | 높음 | 낮음 |

---

## DLQ 설정

Dead Letter Queue는 전송 실패한 이벤트를 저장합니다.

### DLQ 활성화

```yaml
curve:
  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1  # DLQ 활성화
```

### DLQ 비활성화

```yaml
curve:
  kafka:
    topic: event.audit.v1
    dlq-topic:  # 빈 값 또는 미설정
```

⚠️ **주의:** DLQ를 비활성화하면 전송 실패 시 이벤트가 손실될 수 있습니다.

### DLQ 메시지 구조

```json
{
  "eventId": "123456789",
  "originalTopic": "event.audit.v1",
  "originalPayload": "{...}",
  "exceptionType": "org.apache.kafka.common.errors.TimeoutException",
  "exceptionMessage": "Failed to send message after 3 retries",
  "failedAt": 1704067200000
}
```

---

## Retry 설정

전송 실패 시 자동 재시도 설정입니다.

### 기본 설정

```yaml
curve:
  retry:
    enabled: true  # 재시도 활성화
    max-attempts: 3  # 최대 3번 시도
    initial-interval: 1000  # 초기 1초 대기
    multiplier: 2.0  # 2배씩 증가 (1초 -> 2초 -> 4초)
    max-interval: 10000  # 최대 10초
```

### Exponential Backoff 예시

| 시도 | 대기 시간 |
|------|-----------|
| 1차 | 0ms (즉시) |
| 2차 | 1,000ms (1초) |
| 3차 | 2,000ms (2초) |
| 4차 | 4,000ms (4초) |

### Retry 비활성화

```yaml
curve:
  retry:
    enabled: false
```

---

## AOP 설정

`@Auditable` 어노테이션 기반 AOP 설정입니다.

### AOP 활성화 (기본값)

```yaml
curve:
  aop:
    enabled: true
```

### AOP 비활성화

```yaml
curve:
  aop:
    enabled: false
```

---

## 전체 설정 예시

### 프로덕션 환경 (안정성 중심)

```yaml
curve:
  enabled: true

  id-generator:
    worker-id: ${INSTANCE_ID}  # 환경변수에서 주입
    auto-generate: false

  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
    async-mode: false  # 동기 전송
    retries: 5
    retry-backoff-ms: 1000
    request-timeout-ms: 30000

  retry:
    enabled: true
    max-attempts: 5
    initial-interval: 1000
    multiplier: 2.0
    max-interval: 10000

  aop:
    enabled: true
```

### 개발/테스트 환경 (성능 중심)

```yaml
curve:
  enabled: true

  id-generator:
    worker-id: 1
    auto-generate: false

  kafka:
    topic: event.audit.dev.v1
    dlq-topic: event.audit.dlq.dev.v1
    async-mode: true  # 비동기 전송
    async-timeout-ms: 3000
    retries: 3

  retry:
    enabled: true
    max-attempts: 3
    initial-interval: 500
    multiplier: 1.5

  aop:
    enabled: true
```

### 고성능 환경

```yaml
curve:
  enabled: true

  id-generator:
    worker-id: ${WORKER_ID}
    auto-generate: false

  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
    async-mode: true  # 비동기 전송
    async-timeout-ms: 5000
    retries: 1  # 최소 재시도

  retry:
    enabled: false  # 재시도 비활성화 (성능 우선)

  aop:
    enabled: true
```

---

## 환경별 설정 권장사항

### 로컬 개발

- Worker ID: 1 (고정)
- 전송 모드: 동기 (디버깅 편의)
- DLQ: 활성화
- Retry: 최소 (빠른 실패)

### 스테이징

- Worker ID: 환경변수
- 전송 모드: 비동기
- DLQ: 활성화
- Retry: 중간 수준

### 프로덕션

- Worker ID: 중앙 관리 (Consul/etcd)
- 전송 모드: 비즈니스 요구사항에 따라
- DLQ: 필수 활성화
- Retry: 높은 수준

---

## 문제 해결

### Worker ID 충돌

**증상:** 동일한 ID가 생성됨

**해결:**
```yaml
curve:
  id-generator:
    worker-id: ${UNIQUE_INSTANCE_ID}
```

### 전송 타임아웃

**증상:** `TimeoutException` 발생

**해결:**
```yaml
curve:
  kafka:
    request-timeout-ms: 60000  # 타임아웃 증가
```

### 높은 레이턴시

**증상:** 이벤트 발행이 느림

**해결:**
```yaml
curve:
  kafka:
    async-mode: true  # 비동기 모드로 전환
```

---

## 추가 정보

- [Snowflake ID Algorithm](https://en.wikipedia.org/wiki/Snowflake_ID)
- [Kafka Producer Configuration](https://kafka.apache.org/documentation/#producerconfigs)
- [Spring Retry](https://docs.spring.io/spring-retry/docs/current/reference/html/)
