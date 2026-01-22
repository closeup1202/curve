# Curve Sample Application - Order Service

Curve 이벤트 발행 라이브러리를 실제로 활용하는 주문 서비스 예제입니다.

## 주요 기능

### 1. **자동 이벤트 발행**
- `@PublishEvent` 어노테이션만 추가하면 메서드 실행 시 자동으로 Kafka에 이벤트 발행
- 별도의 이벤트 발행 코드 작성 불필요

### 2. **PII 데이터 자동 보호**
- 고객 정보(이름, 이메일, 전화번호, 주소)가 자동으로 마스킹/암호화
- Kafka에 전송되는 이벤트에는 보호된 데이터만 포함

### 3. **이벤트 타입**
- **ORDER_CREATED**: 주문 생성 이벤트
- **ORDER_CANCELLED**: 주문 취소 이벤트
- **ORDER_STATUS_CHANGED**: 주문 상태 변경 이벤트

## 프로젝트 구조

```
sample/
├── domain/
│   ├── Order.java              # 주문 도메인 모델
│   ├── Customer.java           # 고객 정보 (PII 포함)
│   └── OrderStatus.java        # 주문 상태 Enum
├── event/
│   ├── OrderCreatedPayload.java    # 주문 생성 이벤트
│   └── OrderCancelledPayload.java  # 주문 취소 이벤트
├── service/
│   └── OrderService.java       # 비즈니스 로직 (@PublishEvent 적용)
├── controller/
│   └── OrderController.java    # REST API
└── dto/
    ├── CreateOrderRequest.java
    ├── CancelOrderRequest.java
    └── OrderResponse.java
```

## 실행 방법

### 1. Kafka 시작

루트 디렉토리에서:
```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
cd sample
../gradlew bootRun
```

또는

```bash
./gradlew :sample:bootRun
```

### 3. Kafka UI 접속

http://localhost:8080 접속하여 이벤트 확인

## API 사용 예제

### 1. 주문 생성

**요청**:
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-001",
    "customerName": "홍길동",
    "email": "hong@example.com",
    "phone": "010-1234-5678",
    "address": "서울시 강남구 테헤란로 123",
    "productName": "MacBook Pro 16",
    "quantity": 1,
    "totalAmount": 3500000
  }'
```

**응답**:
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "customer-001",
  "customerName": "홍길동",
  "productName": "MacBook Pro 16",
  "quantity": 1,
  "totalAmount": 3500000,
  "status": "PENDING",
  "createdAt": "2024-01-17T10:30:00Z",
  "updatedAt": "2024-01-17T10:30:00Z"
}
```

**Kafka에 발행되는 이벤트**:
```json
{
  "eventId": {
    "value": "123456789012345678"
  },
  "eventType": {
    "value": "ORDER_CREATED"
  },
  "severity": "INFO",
  "metadata": {
    "source": {
      "service": "sample-order-service",
      "environment": "local",
      "instanceId": "localhost",
      "host": "192.168.1.100",
      "version": "0.0.1-SNAPSHOT"
    },
    "actor": {
      "userId": "anonymous",
      "role": null,
      "ip": "127.0.0.1"
    },
    "trace": {
      "traceId": null,
      "spanId": null
    },
    "schema": {
      "name": "OrderCreated",
      "version": 1
    },
    "tags": {}
  },
  "payload": {
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "customer": {
      "customerId": "customer-001",
      "name": "홍**",
      "email": "hong@***.com",
      "phone": "010****5678",
      "address": "서울시 강남구 테***"
    },
    "productName": "MacBook Pro 16",
    "quantity": 1,
    "totalAmount": 3500000,
    "status": "PENDING"
  },
  "occurredAt": "2024-01-17T10:30:00Z",
  "publishedAt": "2024-01-17T10:30:00.123Z"
}
```

### 2. 주문 조회

```bash
curl http://localhost:8081/api/orders/{orderId}
```

### 3. 주문 취소

```bash
curl -X POST http://localhost:8081/api/orders/{orderId}/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "고객 변심"
  }'
```

### 4. 주문 상태 업데이트

```bash
curl -X PATCH "http://localhost:8081/api/orders/{orderId}/status?newStatus=SHIPPED"
```

## PII 데이터 보호 확인

### Customer 객체의 PII 필드

```java
@PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
private String name;              // "홍길동" → "홍**"

@PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
private String email;             // "hong@example.com" → "hong@***.com"

@PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
private String phone;             // "010-1234-5678" → "encrypted_value"

@PiiField(strategy = PiiStrategy.MASK)
private String address;           // "서울시 강남구 테헤란로 123" → "서울시 강남구 테***"
```

### Kafka 이벤트에서 확인

1. Kafka UI (http://localhost:8080) 접속
2. Topics → `event.audit.v1` 선택
3. Messages 탭에서 이벤트 확인
4. `payload.customer` 필드에서 PII 데이터가 마스킹/암호화된 것을 확인

## 로그에서 이벤트 발행 확인

애플리케이션 로그:
```
INFO : Creating order: customer=customer-001, product=MacBook Pro 16, quantity=1, amount=3500000
DEBUG: Event published: eventType=ORDER_CREATED, severity=INFO
INFO : Order created successfully: orderId=550e8400-e29b-41d4-a716-446655440000
DEBUG: Sending event to Kafka: eventId=123456789012345678, topic=event.audit.v1, mode=async
DEBUG: Event sent successfully: eventId=123456789012345678, topic=event.audit.v1, partition=0, offset=123
```

## @PublishEvent 어노테이션 옵션

### OrderService.java 예제

```java
@PublishEvent(
    eventType = "ORDER_CREATED",           // 이벤트 타입 (필수)
    severity = EventSeverity.INFO,         // 이벤트 심각도 (INFO, WARNING, ERROR, CRITICAL)
    phase = PublishEvent.Phase.AFTER_RETURNING,  // 실행 시점
    payloadIndex = -1,                     // -1: 반환값, 0~N: 파라미터 인덱스
    failOnError = false                    // 이벤트 발행 실패 시 예외 발생 여부
)
public OrderCreatedPayload createOrder(...) {
    // 비즈니스 로직
}
```

### Phase 옵션

- **BEFORE**: 메서드 실행 전 이벤트 발행
- **AFTER_RETURNING**: 메서드 정상 완료 후 이벤트 발행
- **AFTER**: 메서드 완료 후 항상 이벤트 발행 (예외 발생해도)

## DLQ (Dead Letter Queue) 테스트

### Kafka 중단 시나리오

1. Kafka 중단:
```bash
docker-compose stop kafka
```

2. API 호출:
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{...}'
```

3. 로그 확인:
```
ERROR: All retry attempts exhausted for event: eventId=123456789012345678
WARN : Sending failed event to DLQ (async): eventId=123456789012345678, dlqTopic=event.audit.dlq.v1
ERROR: Failed to send event to DLQ (async): eventId=123456789012345678, dlqTopic=event.audit.dlq.v1
ERROR: Event backed up to file with restricted permissions: eventId=123456789012345678, file=./dlq-backup/123456789012345678.json
```

4. 백업 파일 확인:
```bash
ls -la dlq-backup/
# -rw------- 1 user group 2048 Jan 17 10:30 123456789012345678.json
```

## 성능 모니터링

### Curve 메트릭 확인

```bash
# 이벤트 발행 성공/실패 횟수
# DLQ 전송 횟수
# 평균 처리 시간
```

## 트러블슈팅

### 1. Kafka 연결 실패

```
ERROR: Failed to send event to Kafka
```

**해결**:
- Kafka가 실행 중인지 확인: `docker-compose ps`
- bootstrap-servers 설정 확인: `localhost:9094`

### 2. PII 암호화 실패

**증상:**
```
ERROR: PII 암호화 키가 설정되지 않았습니다!
ERROR: @PiiField(strategy = PiiStrategy.ENCRYPT) 사용 시 예외가 발생합니다.
```

**해결**:

1. **암호화 키 생성**:
```bash
openssl rand -base64 32
# 출력 예: K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

2. **환경변수 설정**:
```bash
# Linux/macOS
export PII_ENCRYPTION_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
export PII_HASH_SALT=your-random-salt-value

# Windows PowerShell
$env:PII_ENCRYPTION_KEY="K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols="
$env:PII_HASH_SALT="your-random-salt-value"
```

3. **application.yml 설정**:
```yaml
curve:
  pii:
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}
      salt: ${PII_HASH_SALT}
```

**⚠️ 주의**: 암호화 키를 application.yml에 직접 하드코딩하지 마세요!

### 3. 이벤트가 발행되지 않음

**확인 사항**:
- `@PublishEvent` 어노테이션이 올바르게 적용되었는지 확인
- AOP가 활성화되어 있는지 확인: `curve.aop.enabled=true`
- 메서드가 public인지 확인 (AOP는 public 메서드만 지원)

### 4. 설정 검증 실패

**증상:**
```
APPLICATION FAILED TO START
Reason: workerId는 1023 이하여야 합니다
```

**해결**:
- 설정값이 검증 규칙에 맞는지 확인
- `curve.id-generator.worker-id`: 0 ~ 1023 범위
- `curve.kafka.topic`: 빈 문자열 불가
- `curve.retry.max-attempts`: 1 이상
- 상세 검증 규칙은 [CONFIGURATION.md](../docs/CONFIGURATION.md#설정-검증) 참고

## 다음 단계

1. **Spring Security 통합**: 실제 사용자 인증 정보를 EventActor에 포함
2. **분산 추적**: Sleuth/Zipkin 통합하여 traceId 자동 추출
3. **커스텀 이벤트 타입**: 도메인별 이벤트 페이로드 확장
4. **이벤트 소비자**: Kafka Consumer를 만들어 이벤트 처리

## 참고

- [Curve 메인 README](../README.md)
- [Curve 설정 가이드](../CONFIGURATION.md)
- [Kafka UI](http://localhost:8080)
