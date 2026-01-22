# Curve 설정 가이드

이 문서는 Curve 이벤트 발행 라이브러리의 상세 설정 방법을 설명합니다.

## 목차

- [기본 설정](#기본-설정)
- [설정 검증](#설정-검증)
- [Worker ID 설정](#worker-id-설정)
- [Kafka 전송 모드 설정](#kafka-전송-모드-설정)
- [DLQ 설정](#dlq-설정)
- [Retry 설정](#retry-설정)
- [AOP 설정](#aop-설정)
- [PII 보호 설정](#pii-보호-설정)
- [로깅 설정](#로깅-설정)

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

## 설정 검증

Curve는 `@Validated`를 사용하여 애플리케이션 시작 시 설정값의 유효성을 자동으로 검증합니다.
잘못된 설정값이 입력되면 명확한 오류 메시지와 함께 애플리케이션 시작이 실패합니다.

### 검증 규칙

| 설정 항목 | 검증 규칙 | 오류 메시지 |
|----------|----------|------------|
| `curve.kafka.topic` | 필수 (빈 문자열 불가) | "Kafka topic은 필수입니다" |
| `curve.kafka.retries` | 0 이상 | "retries는 0 이상이어야 합니다" |
| `curve.kafka.retry-backoff-ms` | 양수 | "retryBackoffMs는 양수여야 합니다" |
| `curve.kafka.request-timeout-ms` | 양수 | "requestTimeoutMs는 양수여야 합니다" |
| `curve.kafka.async-timeout-ms` | 양수 | "asyncTimeoutMs는 양수여야 합니다" |
| `curve.kafka.sync-timeout-seconds` | 양수 | "syncTimeoutSeconds는 양수여야 합니다" |
| `curve.kafka.dlq-executor-threads` | 1 이상 | "dlqExecutorThreads는 1 이상이어야 합니다" |
| `curve.id-generator.worker-id` | 0 ~ 1023 | "workerId는 0 이상 1023 이하여야 합니다" |
| `curve.retry.max-attempts` | 1 이상 | "maxAttempts는 1 이상이어야 합니다" |
| `curve.retry.initial-interval` | 양수 | "initialInterval은 양수여야 합니다" |
| `curve.retry.multiplier` | 1 이상 | "multiplier는 1 이상이어야 합니다" |
| `curve.retry.max-interval` | 양수 | "maxInterval은 양수여야 합니다" |

### 검증 오류 예시

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target org.springframework.boot.context.properties.bind.BindException:
Failed to bind properties under 'curve' to com.project.curve.autoconfigure.CurveProperties failed:

    Property: curve.id-generator.worker-id
    Value: "2000"
    Reason: workerId는 1023 이하여야 합니다
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

`@PublishEvent` 어노테이션 기반 AOP 설정입니다.

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

## PII 보호 설정

PII(개인식별정보) 보호 기능을 통해 민감한 데이터를 자동으로 마스킹, 암호화, 해싱할 수 있습니다.

### 기본 설정

```yaml
curve:
  pii:
    enabled: true  # PII 보호 활성화 (기본값: true)
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}  # 암호화 키 (환경변수 필수)
      salt: ${PII_HASH_SALT}              # 해싱 솔트 (환경변수 권장)
```

### 암호화 키 설정 (필수)

`@PiiField(strategy = PiiStrategy.ENCRYPT)` 사용 시 암호화 키가 반드시 필요합니다.

**1. 키 생성**
```bash
# 32바이트 AES-256 키 생성
openssl rand -base64 32
# 출력 예: K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

**2. 환경변수 설정 (권장)**
```bash
# Linux/macOS
export PII_ENCRYPTION_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
export PII_HASH_SALT=your-random-salt-value

# Windows PowerShell
$env:PII_ENCRYPTION_KEY="K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols="
$env:PII_HASH_SALT="your-random-salt-value"
```

**3. application.yml 설정**
```yaml
curve:
  pii:
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}
      salt: ${PII_HASH_SALT}
```

**⚠️ 주의사항:**
- 암호화 키를 application.yml에 직접 하드코딩하지 마세요
- 프로덕션 환경에서는 환경변수 또는 외부 비밀 관리 시스템(Vault, AWS Secrets Manager) 사용 권장
- 키가 설정되지 않으면 ENCRYPT 전략 사용 시 예외 발생

### PII 전략

| 전략 | 설명 | 복원 가능 | 예시 |
|------|------|----------|------|
| `MASK` | 패턴 기반 마스킹 | 불가능 | `홍길동` → `홍**` |
| `ENCRYPT` | AES-256-GCM 암호화 | 가능 (키 필요) | 암호화된 Base64 문자열 |
| `HASH` | SHA-256 해싱 | 불가능 | 해시된 Base64 문자열 |

### PII 타입별 마스킹 패턴

| 타입 | 마스킹 패턴 | 예시 |
|------|------------|------|
| `NAME` | 첫 글자 유지, 나머지 마스킹 | `홍길동` → `홍**` |
| `EMAIL` | 로컬 부분 유지, 도메인 마스킹 | `user@example.com` → `user@***.com` |
| `PHONE` | 앞 3자리 + 뒤 4자리만 유지 | `010-1234-5678` → `010****5678` |
| `DEFAULT` | 앞 30% 유지, 나머지 마스킹 | `서울시 강남구` → `서울***` |

### 사용 예시

```java
public class CustomerInfo {
    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
    private String name;

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;

    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;

    @PiiField(strategy = PiiStrategy.HASH)
    private String ssn;  // 주민등록번호
}
```

### Kubernetes 환경 설정

```yaml
# deployment.yaml
env:
  - name: PII_ENCRYPTION_KEY
    valueFrom:
      secretKeyRef:
        name: curve-secrets
        key: pii-encryption-key
  - name: PII_HASH_SALT
    valueFrom:
      secretKeyRef:
        name: curve-secrets
        key: pii-hash-salt
```

```bash
# Secret 생성
kubectl create secret generic curve-secrets \
  --from-literal=pii-encryption-key=$(openssl rand -base64 32) \
  --from-literal=pii-hash-salt=$(openssl rand -base64 16)
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

  pii:
    enabled: true
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}  # 환경변수 필수
      salt: ${PII_HASH_SALT}
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

### PII 암호화 키 미설정

**증상:**
```
ERROR: PII 암호화 키가 설정되지 않았습니다!
ERROR: @PiiField(strategy = PiiStrategy.ENCRYPT) 사용 시 예외가 발생합니다.
```

**해결:**
```bash
# 1. 키 생성
openssl rand -base64 32

# 2. 환경변수 설정
export PII_ENCRYPTION_KEY=생성된키값

# 3. application.yml 설정
curve:
  pii:
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}
```

### 설정 검증 실패

**증상:**
```
APPLICATION FAILED TO START
Reason: workerId는 1023 이하여야 합니다
```

**해결:**
- 설정값이 검증 규칙에 맞는지 확인
- [설정 검증](#설정-검증) 섹션의 검증 규칙 참고

---

## 로깅 설정

Curve는 기본적으로 최소한의 로그만 출력합니다. 상세한 설정 정보나 내부 동작을 확인하려면 DEBUG 레벨을 활성화하세요.

### 기본 로깅 (INFO)

기본 설정에서는 다음 로그만 출력됩니다:

```
INFO  c.p.c.a.CurveAutoConfiguration : Curve auto-configuration enabled (disable with curve.enabled=false)
```

### DEBUG 로깅 활성화

```yaml
logging:
  level:
    com.project.curve: DEBUG
```

### DEBUG 레벨에서 확인 가능한 정보

| 항목 | 설명 |
|------|------|
| Kafka Producer 설정 | retries, timeout, async-mode 등 상세 설정 |
| RetryTemplate 설정 | max-attempts, backoff 정책 상세 |
| SnowflakeIdGenerator | Worker ID 및 초기화 정보 |
| DLQ ExecutorService | 스레드 풀 크기, shutdown timeout |
| PII 모듈 | 암호화/솔트 설정 상태, 모듈 등록 |
| 이벤트 전송 | 이벤트별 전송 상세 (eventId, topic, partition, offset) |

### 특정 모듈만 DEBUG 활성화

```yaml
logging:
  level:
    # Kafka 전송 관련만 DEBUG
    com.project.curve.kafka: DEBUG

    # Auto-Configuration 관련만 DEBUG
    com.project.curve.autoconfigure: DEBUG

    # PII 처리 관련만 DEBUG
    com.project.curve.spring.pii: DEBUG
```

---

## 추가 정보

- [Snowflake ID Algorithm](https://en.wikipedia.org/wiki/Snowflake_ID)
- [Kafka Producer Configuration](https://kafka.apache.org/documentation/#producerconfigs)
- [Spring Retry](https://docs.spring.io/spring-retry/docs/current/reference/html/)
