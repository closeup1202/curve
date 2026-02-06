# Curve 설정 가이드

이 문서는 Curve 이벤트 발행 라이브러리의 상세한 설정 방법을 설명합니다.

## 목차

- [기본 설정](#기본-설정)
- [설정 유효성 검사](#설정-유효성-검사)
- [Worker ID 설정](#worker-id-설정)
- [Kafka 전송 모드 설정](#kafka-전송-모드-설정)
- [DLQ 설정](#dlq-설정)
- [백업 전략 설정](#백업-전략-설정)
- [재시도 설정](#재시도-설정)
- [AOP 설정](#aop-설정)
- [PII 보호 설정](#pii-보호-설정)
- [Outbox 설정](#outbox-설정)
- [직렬화 설정](#직렬화-설정)
- [Avro 직렬화 설정](#avro-직렬화-설정)
- [로깅 설정](#로깅-설정)

---

## 기본 설정

### application.yml

```yaml
curve:
  enabled: true  # Curve 활성화 (기본값: true)

  kafka:
    topic: event.audit.v1  # 메인 토픽 이름
    dlq-topic: event.audit.dlq.v1  # DLQ 토픽 (선택)

  id-generator:
    worker-id: 1  # Snowflake Worker ID (0~1023)
    auto-generate: false  # MAC 주소 기반 자동 생성
```

---

## 설정 유효성 검사

Curve는 애플리케이션 시작 시 `@Validated`를 통해 설정값을 자동으로 검증합니다.
잘못된 설정값이 입력되면 명확한 에러 메시지와 함께 애플리케이션 시작이 실패합니다.

### 검증 규칙

| 설정 항목 | 검증 규칙 | 에러 메시지 |
|----------|----------|------------|
| `curve.kafka.topic` | 필수 (빈 문자열 불가) | "Kafka topic is required" |
| `curve.kafka.retries` | 0 이상 | "retries must be 0 or greater" |
| `curve.kafka.retry-backoff-ms` | 양수 | "retryBackoffMs must be positive" |
| `curve.kafka.request-timeout-ms` | 양수 | "requestTimeoutMs must be positive" |
| `curve.kafka.async-timeout-ms` | 양수 | "asyncTimeoutMs must be positive" |
| `curve.kafka.sync-timeout-seconds` | 양수 | "syncTimeoutSeconds must be positive" |
| `curve.kafka.dlq-executor-threads` | 1 이상 | "dlqExecutorThreads must be 1 or greater" |
| `curve.id-generator.worker-id` | 0 ~ 1023 | "workerId must be between 0 and 1023" |
| `curve.retry.max-attempts` | 1 이상 | "maxAttempts must be 1 or greater" |
| `curve.retry.initial-interval` | 양수 | "initialInterval must be positive" |
| `curve.retry.multiplier` | 1 이상 | "multiplier must be 1 or greater" |
| `curve.retry.max-interval` | 양수 | "maxInterval must be positive" |
| `curve.outbox.poll-interval-ms` | 양수 | "pollIntervalMs must be positive" |
| `curve.outbox.batch-size` | 1 ~ 1000 | "batchSize must be between 1 and 1000" |
| `curve.outbox.max-retries` | 1 이상 | "maxRetries must be 1 or greater" |
| `curve.outbox.send-timeout-seconds` | 양수 | "sendTimeoutSeconds must be positive" |
| `curve.outbox.retention-days` | 1 이상 | "retentionDays must be 1 or greater" |
| `curve.async.core-pool-size` | 1 이상 | "corePoolSize must be at least 1" |
| `curve.async.max-pool-size` | 1 이상 | "maxPoolSize must be at least 1" |
| `curve.async.queue-capacity` | 0 이상 | "queueCapacity must be at least 0" |
| `curve.kafka.backup.s3-bucket` | s3Enabled=true일 때 필수 | "s3Bucket is required when s3Enabled=true" |
| `curve.serde.schema-registry-url` | type=AVRO일 때 필수 | "schemaRegistryUrl is required when serde type is AVRO" |

### 검증 실패 예시

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Binding to target org.springframework.boot.context.properties.bind.BindException:
Failed to bind properties under 'curve' to com.project.curve.autoconfigure.CurveProperties failed:

    Property: curve.id-generator.worker-id
    Value: "2000"
    Reason: workerId must be 1023 or less
```

---

## Worker ID 설정

Snowflake ID Generator는 분산 환경에서 고유한 ID를 생성하기 위해 Worker ID를 사용합니다.

### 방법 1: 명시적 Worker ID 설정 (권장)

각 인스턴스에 고유한 Worker ID를 부여합니다.

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
        fieldPath: metadata.uid  # Pod UID 해시값 사용
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

MAC 주소를 기반으로 Worker ID를 자동 생성합니다.

```yaml
curve:
  id-generator:
    auto-generate: true
```

**⚠️ 주의:**
- 가상 환경에서는 MAC 주소가 동일할 수 있어 충돌 가능성이 있습니다.
- 컨테이너 재시작 시 MAC 주소가 변경될 수 있습니다.
- 프로덕션 환경에서는 명시적 설정을 권장합니다.

### Worker ID 범위

- **최소값:** 0
- **최대값:** 1023
- **권장:** 환경 변수 또는 설정 관리 시스템(Consul, etcd)을 통해 관리

---

## Kafka 전송 모드 설정

Curve는 동기(Synchronous) 및 비동기(Asynchronous) 전송 모드를 모두 지원합니다.

### 동기 전송 (기본값)

```yaml
curve:
  kafka:
    async-mode: false  # 동기 전송
    request-timeout-ms: 30000  # 30초
```

**특징:**
- ✅ 확실한 전송 보장 (성공/실패 여부 명확)
- ✅ 에러 핸들링 용이
- ❌ 성능 저하 (Blocking)
- ❌ 처리량 제한적

**적합한 경우:**
- 금융 거래, 결제 등 정확성이 중요한 경우
- 이벤트 유실이 절대 허용되지 않는 경우
- 낮은 처리량 (수십 ~ 수백 TPS)

### 비동기 전송

```yaml
curve:
  kafka:
    async-mode: true  # 비동기 전송
    async-timeout-ms: 5000  # 5초 타임아웃
```

**특징:**
- ✅ 높은 성능 (Non-blocking)
- ✅ 대량 처리 가능
- ⚠️ 콜백 기반 에러 핸들링
- ⚠️ 전송 실패 시 DLQ 의존

**적합한 경우:**
- 로그, 분석 이벤트 등 일부 유실이 허용되는 경우
- 높은 처리량이 필요한 경우 (수천 ~ 수만 TPS)
- Latency가 중요한 경우

### 성능 비교

| 항목 | 동기 전송 | 비동기 전송 |
|------|-----------|-------------|
| 처리량 (TPS) | ~500 | ~10,000+ |
| Latency | 높음 (10-50ms) | 낮음 (1-5ms) |
| 전송 보장 | 강력함 | 보통 (DLQ 의존) |
| 리소스 사용 | 높음 | 낮음 |

---

## DLQ 설정

Dead Letter Queue는 전송에 실패한 이벤트를 저장합니다.

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
    dlq-topic:  # 빈 값 또는 설정 안 함
```

⚠️ **주의:** DLQ를 비활성화하면 전송 실패 시 이벤트가 유실될 수 있습니다.

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

## 백업 전략 설정

DLQ 전송마저 실패할 경우를 대비한 백업 전략을 설정합니다.

### S3 백업 (클라우드 환경 권장)

```yaml
curve:
  kafka:
    backup:
      s3-enabled: true
      s3-bucket: "my-event-backup-bucket"
      s3-prefix: "dlq-backup"
```

**요구사항:**
- `software.amazon.awssdk:s3` 의존성 추가
- Spring Context에 `S3Client` 빈 등록

### 로컬 파일 백업

```yaml
curve:
  kafka:
    backup:
      local-enabled: true
    dlq-backup-path: "./dlq-backup"
```

---

## 재시도 설정

전송 실패 시 자동 재시도 설정입니다.

### 기본 설정

```yaml
curve:
  retry:
    enabled: true  # 재시도 활성화
    max-attempts: 3  # 최대 3회 시도
    initial-interval: 1000  # 초기 1초 대기
    multiplier: 2.0  # 2배씩 증가 (1초 -> 2초 -> 4초)
    max-interval: 10000  # 최대 10초
```

### Exponential Backoff 예시

| 시도 횟수 | 대기 시간 |
|------|-----------|
| 1회차 | 0ms (즉시) |
| 2회차 | 1,000ms (1초) |
| 3회차 | 2,000ms (2초) |
| 4회차 | 4,000ms (4초) |

### 재시도 비활성화

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

## 비동기 실행기 설정

Curve는 비동기 이벤트 처리를 위한 전용 `curveAsyncExecutor` 빈을 등록할 수 있습니다.

> **참고:** 이 설정은 애플리케이션에 `@EnableAsync`를 강제 적용하지 않습니다. `@EnableAsync`가 필요하면 별도의 설정에서 활성화하세요.

### 비동기 실행기 활성화

```yaml
curve:
  async:
    enabled: true  # curveAsyncExecutor 빈 등록
    core-pool-size: 2  # 코어 스레드 풀 크기 (기본값: 2)
    max-pool-size: 10  # 최대 스레드 풀 크기 (기본값: 10)
    queue-capacity: 500  # 작업 큐 용량 (기본값: 500)
```

### 비동기 실행기 비활성화 (기본값)

```yaml
curve:
  async:
    enabled: false
```

---

## PII 보호 설정

PII(개인정보) 보호 기능을 통해 민감한 데이터를 자동으로 마스킹, 암호화, 해싱할 수 있습니다.

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
# 출력 예시: K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

**2. 환경 변수 설정 (권장)**
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

**⚠️ 주의:**
- 암호화 키를 application.yml에 직접 하드코딩하지 마세요.
- 프로덕션 환경에서는 환경 변수 또는 외부 시크릿 관리 시스템(Vault, AWS Secrets Manager)을 사용하세요.
- 키가 설정되지 않으면 ENCRYPT 전략 사용 시 예외가 발생합니다.

### PII 전략 종류

| 전략 | 설명 | 복호화 가능 여부 | 예시 |
|------|------|----------|------|
| `MASK` | 패턴 기반 마스킹 | 불가능 | `홍길동` → `홍**` |
| `ENCRYPT` | AES-256-GCM 암호화 | 가능 (키 필요) | 암호화된 Base64 문자열 |
| `HASH` | HMAC-SHA256 해싱 | 불가능 | 해싱된 Base64 문자열 |

### PII 타입별 마스킹 패턴

| 타입 | 마스킹 패턴 | 예시 |
|------|------------|------|
| `NAME` | 첫 글자 유지, 나머지 마스킹 | `홍길동` → `홍**` |
| `EMAIL` | 로컬 파트 유지, 도메인 마스킹 | `user@example.com` → `user@***.com` |
| `PHONE` | 앞 3자리, 뒤 4자리만 유지 | `010-1234-5678` → `010****5678` |
| `DEFAULT` | 앞 30% 유지, 나머지 마스킹 | `서울시 강남구` → `서울시***` |

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

## Outbox 설정

Transactional Outbox 패턴을 사용하여 DB 트랜잭션과 이벤트 발행 간의 원자성을 보장합니다.

### 기본 설정

```yaml
curve:
  outbox:
    enabled: true  # Outbox 활성화
    poll-interval-ms: 1000  # 폴링 간격 (1초)
    batch-size: 100  # 배치 크기
    max-retries: 3  # 최대 재시도 횟수
    send-timeout-seconds: 10  # 전송 타임아웃
    cleanup-enabled: true  # 오래된 이벤트 정리 활성화
    retention-days: 7  # 보관 기간 (7일)
    cleanup-cron: "0 0 2 * * *"  # 정리 작업 실행 시간 (매일 새벽 2시)
    initialize-schema: embedded  # 스키마 초기화 모드 (embedded, always, never)
```

### 스키마 초기화 모드

- `embedded`: H2, HSQLDB 등 임베디드 DB일 때만 테이블 자동 생성 (기본값)
- `always`: 항상 테이블 생성 시도 (없을 경우)
- `never`: 자동 생성 안 함 (Flyway/Liquibase 사용 시 권장)

---

## 직렬화 설정

이벤트 페이로드 직렬화 방식을 설정합니다.

```yaml
curve:
  serde:
    type: JSON  # JSON (기본값), AVRO, PROTOBUF
```

---

## Avro 직렬화 설정

Avro를 사용하여 이벤트를 직렬화하려면 추가 설정이 필요합니다.

### 1. Curve 설정

```yaml
curve:
  serde:
    type: AVRO
    schema-registry-url: http://localhost:8081  # Schema Registry 주소
```

### 2. Spring Kafka 설정 (필수)

Spring Kafka의 Producer 설정에서 `value-serializer`를 명시적으로 지정해야 합니다.

```yaml
spring:
  kafka:
    producer:
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    properties:
      schema.registry.url: http://localhost:8081
```

**⚠️ 주의:**
- `curve.serde.type=AVRO` 설정 시, Curve는 내부적으로 `GenericRecord` 객체를 생성하여 KafkaTemplate에 전달합니다.
- 따라서 KafkaTemplate이 `GenericRecord`를 직렬화할 수 있도록 반드시 `KafkaAvroSerializer`를 사용해야 합니다.
- `schema.registry.url`은 `curve.serde`와 `spring.kafka.properties` 양쪽에 설정이 필요할 수 있습니다 (Curve 내부 로직용 및 Kafka Serializer용).

### Avro 스키마 구조

Curve는 내부적으로 다음과 같은 고정된 Avro 스키마를 사용합니다. `payload`와 `metadata`의 일부 필드는 유연성을 위해 JSON 문자열로 저장됩니다.

```json
{
  "type": "record",
  "name": "EventEnvelope",
  "namespace": "com.project.curve.core.envelope",
  "fields": [
    {"name": "eventId", "type": "string"},
    {"name": "eventType", "type": "string"},
    {"name": "severity", "type": "string"},
    {"name": "metadata", "type": { ... }},
    {"name": "payload", "type": "string"}, // JSON String
    {"name": "occurredAt", "type": "long", "logicalType": "timestamp-millis"},
    {"name": "publishedAt", "type": "long", "logicalType": "timestamp-millis"}
  ]
}
```

---

## 전체 설정 예시

### 프로덕션 환경 (안정성 중시)

```yaml
curve:
  enabled: true

  id-generator:
    worker-id: ${INSTANCE_ID}  # 환경 변수에서 주입
    auto-generate: false

  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
    async-mode: false  # 동기 전송
    retries: 5
    retry-backoff-ms: 1000
    request-timeout-ms: 30000
    
    # 백업 전략
    backup:
      s3-enabled: true
      s3-bucket: "prod-event-backups"
      local-enabled: false

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
      default-key: ${PII_ENCRYPTION_KEY}  # 환경 변수 필수
      salt: ${PII_HASH_SALT}

  async:
    enabled: true
    core-pool-size: 4
    max-pool-size: 20
    queue-capacity: 1000

  outbox:
    enabled: true
    initialize-schema: never  # Flyway 사용
    cleanup-enabled: true
    retention-days: 14
```

### 개발/테스트 환경 (성능 중시)

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
    
    backup:
      local-enabled: true

  retry:
    enabled: true
    max-attempts: 3
    initial-interval: 500
    multiplier: 1.5

  aop:
    enabled: true

  outbox:
    enabled: true
    initialize-schema: always

  async:
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

  async:
    enabled: true
    core-pool-size: 8
    max-pool-size: 32
    queue-capacity: 2000
```

---

## 환경별 권장 설정

### 로컬 개발

- Worker ID: 1 (고정)
- 전송 모드: 동기 (디버깅 편의성)
- DLQ: 활성화
- 재시도: 최소화 (빠른 실패)
- Outbox: 활성화 (스키마 자동 생성)
- 백업: 로컬 파일

### 스테이징

- Worker ID: 환경 변수
- 전송 모드: 비동기
- DLQ: 활성화
- 재시도: 중간 수준
- Outbox: 활성화
- 백업: S3 (가능한 경우) 또는 로컬

### 프로덕션

- Worker ID: 중앙 관리 (Consul/etcd)
- 전송 모드: 비즈니스 요건에 따라 결정
- DLQ: 필수 활성화
- 재시도: 높은 수준
- Outbox: 필수 활성화 (데이터 정합성)
- 백업: S3 (K8s 환경 필수)

---

## 트러블슈팅

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

### 높은 Latency

**증상:** 이벤트 발행 속도가 느림

**해결:**
```yaml
curve:
  kafka:
    async-mode: true  # 비동기 모드로 전환
```

### PII 암호화 키 미설정

**증상:**
```
ERROR: PII encryption key is not configured!
ERROR: An exception will occur when using @PiiField(strategy = PiiStrategy.ENCRYPT).
```

**해결:**
```bash
# 1. 키 생성
openssl rand -base64 32

# 2. 환경 변수 설정
export PII_ENCRYPTION_KEY=generated_key_value

# 3. application.yml 설정
curve:
  pii:
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}
```

### 설정 유효성 검사 실패

**증상:**
```
APPLICATION FAILED TO START
Reason: workerId must be 1023 or less
```

**해결:**
- 설정값이 검증 규칙에 맞는지 확인
- [설정 유효성 검사](#설정-유효성-검사) 섹션의 규칙 참조

---

## 로깅 설정

Curve는 기본적으로 최소한의 로그만 출력합니다. 상세한 설정 정보나 내부 동작을 확인하려면 DEBUG 레벨을 활성화하세요.

### 기본 로깅 (INFO)

기본 설정에서는 다음과 같은 로그만 출력됩니다:

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
| Kafka Producer 설정 | retries, timeout, async-mode 등 상세 설정값 |
| RetryTemplate 설정 | max-attempts, backoff 정책 상세 |
| SnowflakeIdGenerator | Worker ID 및 초기화 정보 |
| DLQ ExecutorService | 스레드 풀 크기, 종료 타임아웃 |
| PII 모듈 | 암호화/솔트 설정 상태, 모듈 등록 여부 |
| 이벤트 전송 | 이벤트별 전송 내역 (eventId, topic, partition, offset) |
| Outbox Publisher | 폴링, 발행, 정리 작업 로그 |

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

    # Outbox 관련만 DEBUG
    com.project.curve.spring.outbox: DEBUG
```

---

## 추가 정보

- [Snowflake ID 알고리즘](https://en.wikipedia.org/wiki/Snowflake_ID)
- [Kafka Producer 설정](https://kafka.apache.org/documentation/#producerconfigs)
- [Spring Retry](https://docs.spring.io/spring-retry/docs/current/reference/html/)
- [Transactional Outbox 패턴](https://microservices.io/patterns/data/transactional-outbox.html)
