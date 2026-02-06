# Curve Sample - Quick Start Guide

## 1. Start Kafka

From the root directory:

```bash
docker-compose up -d
```

**Verify**:

- Kafka UI: http://localhost:8080
- Kafka broker: localhost:9092

## 2. Set Environment Variables (for PII Encryption)

To use PII encryption features, you need to set the encryption key.

```bash
# Generate key
openssl rand -base64 32

# Set environment variables (Linux/macOS)
export PII_ENCRYPTION_KEY=generated_key_value
export PII_HASH_SALT=your-random-salt

# Windows PowerShell
$env:PII_ENCRYPTION_KEY="generated_key_value"
$env:PII_HASH_SALT="your-random-salt"
```

## 3. Run the Application

```bash
cd sample
../gradlew bootRun
```

Or from root:

```bash
./gradlew :sample:bootRun
```

**Verify startup**:

```
2024-01-17 10:30:00 - Curve has been automatically activated!
2024-01-17 10:30:00 - DLQ ExecutorService created with 2 threads
2024-01-17 10:30:00 - KafkaEventProducer initialized: topic=event.audit.v1, asyncMode=true, ...
2024-01-17 10:30:00 - Tomcat started on port 8081
```

## 4. Test APIs

### Create Order

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "cust-001",
    "customerName": "John Doe",
    "email": "john@example.com",
    "phone": "010-1234-5678",
    "address": "123 Main St, Seoul",
    "productName": "MacBook Pro",
    "quantity": 1,
    "totalAmount": 3500000
  }'
```

**Response example**:

```json
{
  "orderId": "a1b2c3d4-...",
  "customerId": "cust-001",
  "customerName": "John Doe",
  "productName": "MacBook Pro",
  "quantity": 1,
  "totalAmount": 3500000,
  "status": "PENDING",
  "createdAt": "2024-01-17T10:35:00Z",
  "updatedAt": "2024-01-17T10:35:00Z"
}
```

### Get Order

```bash
# Use the orderId from the response above
curl http://localhost:8081/api/orders/a1b2c3d4-...
```

### Cancel Order

```bash
curl -X POST http://localhost:8081/api/orders/a1b2c3d4-.../cancel \
  -H "Content-Type: application/json" \
  -d '{"reason": "Customer request"}'
```

## 5. Verify Kafka Events

### Check in Kafka UI

1. Go to http://localhost:8080
2. Select `event.audit.v1` topic
3. Click **Messages** tab
4. View the latest messages

### Event Structure

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
      "name": "Joh**",                    ← Masked
      "email": "john@***.com",            ← Masked
      "phone": "010****5678",             ← Encrypted
      "address": "123 Main St, S***"      ← Masked
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

## 6. Verify PII Protection

### Original Data

```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "010-1234-5678",
  "address": "123 Main St, Seoul"
}
```

### Data Stored in Kafka

```json
{
  "name": "Joh**",                    ← PiiType.NAME, PiiStrategy.MASK
  "email": "john@***.com",            ← PiiType.EMAIL, PiiStrategy.MASK
  "phone": "010****5678",             ← PiiType.PHONE, PiiStrategy.ENCRYPT
  "address": "123 Main St, S***"      ← PiiStrategy.MASK
}
```

## 7. Check Logs

### Event Publishing Success

```
INFO  : Creating order: customer=cust-001, product=MacBook Pro, quantity=1, amount=3500000
DEBUG : Event published: eventType=ORDER_CREATED, severity=INFO
INFO  : Order created successfully: orderId=a1b2c3d4-...
DEBUG : Sending event to Kafka: eventId=123456789012345678, topic=event.audit.v1, mode=async
DEBUG : Event sent successfully: eventId=123456789012345678, topic=event.audit.v1, partition=0, offset=123
```

### Event Publishing Failure (when Kafka is down)

```
ERROR : All retry attempts exhausted for event: eventId=123456789012345678
WARN  : Sending failed event to DLQ (async): eventId=123456789012345678, dlqTopic=event.audit.dlq.v1
INFO  : Event sent to DLQ successfully (async): eventId=123456789012345678, dlqTopic=event.audit.dlq.v1, partition=0, offset=5
```

## 8. Code Explanation

### @PublishEvent Annotation

```java

@PublishEvent(
        eventType = "ORDER_CREATED",           // Kafka event type
        severity = EventSeverity.INFO,         // Event severity
        phase = PublishEvent.Phase.AFTER_RETURNING,  // Method execution timing
        payloadIndex = -1,                     // -1: use return value
        failOnError = false,                   // Continue business logic even if event publish fails
        outbox = true,                         // Use Transactional Outbox
        aggregateType = "Order",               // Aggregate type
        aggregateId = "#result.orderId"        // Aggregate ID (SpEL)
)
public OrderCreatedPayload createOrder(...) {
    // Just write business logic
    // Events are automatically published to Kafka
}
```

### PII Field Protection

```java
public class Customer {
    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
    private String name;  // "John Doe" → "Joh**"

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;  // "john@example.com" → "john@***.com"

    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;  // "010-1234-5678" → "encrypted_value"

    @PiiField(strategy = PiiStrategy.MASK)
    private String address;  // "123 Main St, Seoul" → "123 Main St, S***"
}
```

## 9. Next Steps

### Customization

- **Change async/sync mode**: Set `curve.kafka.async-mode` in `application.yml`
- **Adjust retry count**: Set `curve.retry.max-attempts`
- **Adjust DLQ thread count**: Set `curve.kafka.dlq-executor-threads`
- **Outbox settings**: Set `curve.outbox.enabled=true`

### Spring Security Integration

```yaml
spring:
  security:
    user:
      name: admin
      password: admin
```

After this, `userId` and `role` information will be automatically included in EventActor.

### Distributed Tracing (Sleuth)

```yaml
spring:
  sleuth:
    enabled: true
```

After this, `traceId` and `spanId` information will be automatically included in EventTrace.

## 10. Troubleshooting

### Kafka Connection Failure

```
ERROR: Failed to send event to Kafka
```

**Solution**: Verify Kafka is running with `docker-compose ps`

### Port Conflict

```
ERROR: Port 8081 is already in use
```

**Solution**: Change `server.port` in `application.yml`

### Events Not Being Published

**Checklist**:

- Verify `curve.aop.enabled=true`
- Verify method is `public`
- Verify `@PublishEvent` annotation is correctly applied

### PII Encryption Key Not Set

```
ERROR: PII encryption key is not configured!
```

**Solution**: See [2. Set Environment Variables](#2-set-environment-variables-for-pii-encryption)

### Configuration Validation Failure

```
APPLICATION FAILED TO START
Reason: workerId must be 1023 or less
```

**Solution**: Verify configuration values meet validation rules

- `curve.id-generator.worker-id`: 0 ~ 1023
- `curve.kafka.topic`: Cannot be empty
- Details: [CONFIGURATION.md](../docs/CONFIGURATION.md#configuration-validation)

## 11. Learn More

- [Full README](README.md)
- [Curve Main Documentation](../README.md)
- [Curve Configuration Guide](../docs/CONFIGURATION.md)
- [Kafka UI](http://localhost:8080)
