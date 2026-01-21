# Curve Sample - Quick Start Guide

## 1. Kafka 시작

루트 디렉토리에서:
```bash
docker-compose up -d
```

**확인**:
- Kafka UI: http://localhost:8080
- Kafka broker: localhost:9094

## 2. 애플리케이션 실행

```bash
cd sample
../gradlew bootRun
```

또는 루트에서:
```bash
./gradlew :sample:bootRun
```

**실행 확인**:
```
2024-01-17 10:30:00 - Curve가 자동으로 활성화되었습니다!
2024-01-17 10:30:00 - DLQ ExecutorService created with 2 threads
2024-01-17 10:30:00 - KafkaEventProducer initialized: topic=event.audit.v1, asyncMode=true, ...
2024-01-17 10:30:00 - Tomcat started on port 8081
```

## 3. API 테스트

### 주문 생성
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-001",
    "customerName": "홍길동",
    "email": "hong@example.com",
    "phone": "010-1234-5678",
    "address": "서울시 강남구 테헤란로 123",
    "productName": "MacBook Pro",
    "quantity": 1,
    "totalAmount": 3500000
  }'
```

**응답 예시**:
```json
{
  "orderId": "a1b2c3d4-...",
  "customerId": "cust-001",
  "customerName": "홍길동",
  "productName": "MacBook Pro",
  "quantity": 1,
  "totalAmount": 3500000,
  "status": "PENDING",
  "createdAt": "2024-01-17T10:35:00Z",
  "updatedAt": "2024-01-17T10:35:00Z"
}
```

### 주문 조회
```bash
# 위 응답에서 받은 orderId 사용
curl http://localhost:8081/api/orders/a1b2c3d4-...
```

### 주문 취소
```bash
curl -X POST http://localhost:8081/api/orders/a1b2c3d4-.../cancel \
  -H "Content-Type: application/json" \
  -d '{"reason": "고객 변심"}'
```

## 4. Kafka 이벤트 확인

### Kafka UI에서 확인
1. http://localhost:8080 접속
2. `event.audit.v1` 토픽 선택
3. **Messages** 탭 클릭
4. 최신 메시지 확인

### 이벤트 구조
```json
{
  "eventId": {"value": "123456789012345678"},
  "eventType": {"value": "ORDER_CREATED"},
  "severity": "INFO",
  "metadata": {
    "source": {
      "service": "sample-order-service",
      "environment": "local"
    },
    "actor": {
      "userId": "anonymous",
      "ip": "127.0.0.1"
    },
    "schema": {
      "name": "OrderCreated",
      "version": 1
    }
  },
  "payload": {
    "orderId": "a1b2c3d4-...",
    "customer": {
      "customerId": "cust-001",
      "name": "홍**",                    ← 마스킹됨
      "email": "hong@***.com",           ← 마스킹됨
      "phone": "010****5678",            ← 암호화됨
      "address": "서울시 강남구 테***"    ← 마스킹됨
    },
    "productName": "MacBook Pro",
    "quantity": 1,
    "totalAmount": 3500000,
    "status": "PENDING"
  },
  "occurredAt": "2024-01-17T10:35:00Z",
  "publishedAt": "2024-01-17T10:35:00.123Z"
}
```

## 5. PII 보호 확인

### 원본 데이터
```json
{
  "name": "홍길동",
  "email": "hong@example.com",
  "phone": "010-1234-5678",
  "address": "서울시 강남구 테헤란로 123"
}
```

### Kafka에 저장된 데이터
```json
{
  "name": "홍**",                    ← PiiType.NAME, PiiStrategy.MASK
  "email": "hong@***.com",           ← PiiType.EMAIL, PiiStrategy.MASK
  "phone": "010****5678",            ← PiiType.PHONE, PiiStrategy.ENCRYPT
  "address": "서울시 강남구 테***"    ← PiiStrategy.MASK
}
```

## 6. 로그 확인

### 이벤트 발행 성공
```
INFO  : Creating order: customer=cust-001, product=MacBook Pro, quantity=1, amount=3500000
DEBUG : Event published: eventType=ORDER_CREATED, severity=INFO
INFO  : Order created successfully: orderId=a1b2c3d4-...
DEBUG : Sending event to Kafka: eventId=123456789012345678, topic=event.audit.v1, mode=async
DEBUG : Event sent successfully: eventId=123456789012345678, topic=event.audit.v1, partition=0, offset=123
```

### 이벤트 발행 실패 (Kafka 중단 시)
```
ERROR : All retry attempts exhausted for event: eventId=123456789012345678
WARN  : Sending failed event to DLQ (async): eventId=123456789012345678, dlqTopic=event.audit.dlq.v1
INFO  : Event sent to DLQ successfully (async): eventId=123456789012345678, dlqTopic=event.audit.dlq.v1, partition=0, offset=5
```

## 7. 코드 설명

### @PublishEvent 어노테이션
```java
@PublishEvent(
    eventType = "ORDER_CREATED",           // Kafka 이벤트 타입
    severity = EventSeverity.INFO,         // 이벤트 심각도
    phase = PublishEvent.Phase.AFTER_RETURNING,  // 메서드 실행 시점
    payloadIndex = -1,                     // -1: 반환값 사용
    failOnError = false                    // 이벤트 발행 실패해도 비즈니스 로직 계속
)
public OrderCreatedPayload createOrder(...) {
    // 비즈니스 로직만 작성
    // 이벤트는 자동으로 Kafka에 발행됨
}
```

### PII 필드 보호
```java
public class Customer {
    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
    private String name;  // "홍길동" → "홍**"

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;  // "hong@example.com" → "hong@***.com"

    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;  // "010-1234-5678" → "encrypted_value"

    @PiiField(strategy = PiiStrategy.MASK)
    private String address;  // "서울시 강남구 테헤란로 123" → "서울시 강남구 테***"
}
```

## 8. 다음 단계

### 커스터마이징
- **비동기/동기 모드 변경**: `application.yml`에서 `curve.kafka.async-mode` 설정
- **재시도 횟수 조정**: `curve.retry.max-attempts` 설정
- **DLQ 스레드 수 조정**: `curve.kafka.dlq-executor-threads` 설정

### Spring Security 통합
```yaml
spring:
  security:
    user:
      name: admin
      password: admin
```

이후 EventActor에 자동으로 `userId`, `role` 정보가 포함됩니다.

### 분산 추적 (Sleuth)
```yaml
spring:
  sleuth:
    enabled: true
```

이후 EventTrace에 자동으로 `traceId`, `spanId` 정보가 포함됩니다.

## 9. 문제 해결

### Kafka 연결 실패
```
ERROR: Failed to send event to Kafka
```
**해결**: `docker-compose ps`로 Kafka 실행 확인

### 포트 충돌
```
ERROR: Port 8081 is already in use
```
**해결**: `application.yml`에서 `server.port` 변경

### 이벤트가 발행되지 않음
**확인 사항**:
- `curve.aop.enabled=true`인지 확인
- 메서드가 `public`인지 확인
- `@PublishEvent` 어노테이션이 올바르게 적용되었는지 확인

## 10. 더 알아보기

- [전체 README](README.md)
- [Curve 메인 문서](../README.md)
- [Curve 설정 가이드](../CONFIGURATION.md)
- [Kafka UI](http://localhost:8080)
