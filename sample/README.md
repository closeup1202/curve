# Curve Sample Application - Order Service

A sample order service demonstrating the Curve event publishing library in action.

## Key Features

### 1. **Automatic Event Publishing**
- Just add `@PublishEvent` annotation to automatically publish events to Kafka on method execution
- No need to write separate event publishing code

### 2. **Automatic PII Data Protection**
- Customer information (name, email, phone, address) is automatically masked/encrypted
- Only protected data is included in events sent to Kafka

### 3. **Event Types**
- **ORDER_CREATED**: Order creation event
- **ORDER_CANCELLED**: Order cancellation event
- **ORDER_STATUS_CHANGED**: Order status change event

## Project Structure

```
sample/
├── domain/
│   ├── Order.java              # Order domain model
│   ├── Customer.java           # Customer information (includes PII)
│   └── OrderStatus.java        # Order status Enum
├── event/
│   ├── OrderCreatedPayload.java    # Order created event
│   └── OrderCancelledPayload.java  # Order cancelled event
├── service/
│   └── OrderService.java       # Business logic (@PublishEvent applied)
├── controller/
│   └── OrderController.java    # REST API
└── dto/
    ├── CreateOrderRequest.java
    ├── CancelOrderRequest.java
    └── OrderResponse.java
```

## How to Run

### 1. Start Kafka

From the root directory:
```bash
docker-compose up -d
```

### 2. Run the Application

```bash
cd sample
../gradlew bootRun
```

Or:

```bash
./gradlew :sample:bootRun
```

### 3. Access Kafka UI

Go to http://localhost:8080 to view events

## API Usage Examples

### 1. Create Order

**Request**:
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-001",
    "customerName": "John Doe",
    "email": "john@example.com",
    "phone": "010-1234-5678",
    "address": "123 Main St, Seoul",
    "productName": "MacBook Pro 16",
    "quantity": 1,
    "totalAmount": 3500000
  }'
```

**Response**:
```json
{
  "orderId": "550e8400-e29b-41d4-a716-446655440000",
  "customerId": "customer-001",
  "customerName": "John Doe",
  "productName": "MacBook Pro 16",
  "quantity": 1,
  "totalAmount": 3500000,
  "status": "PENDING",
  "createdAt": "2024-01-17T10:30:00Z",
  "updatedAt": "2024-01-17T10:30:00Z"
}
```

**Event Published to Kafka**:
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
      "name": "Joh**",
      "email": "john@***.com",
      "phone": "010****5678",
      "address": "123 Main St, S***"
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

### 2. Get Order

```bash
curl http://localhost:8081/api/orders/{orderId}
```

### 3. Cancel Order

```bash
curl -X POST http://localhost:8081/api/orders/{orderId}/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Customer request"
  }'
```

### 4. Update Order Status

```bash
curl -X PATCH "http://localhost:8081/api/orders/{orderId}/status?newStatus=SHIPPED"
```

## PII Data Protection Verification

### PII Fields in Customer Object

```java
@PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
private String name;              // "John Doe" → "Joh**"

@PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
private String email;             // "john@example.com" → "john@***.com"

@PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
private String phone;             // "010-1234-5678" → "encrypted_value"

@PiiField(strategy = PiiStrategy.MASK)
private String address;           // "123 Main St, Seoul" → "123 Main St, S***"
```

### Verify in Kafka

1. Go to Kafka UI (http://localhost:8080)
2. Select Topics → `event.audit.v1`
3. Click Messages tab to view events
4. Verify PII data is masked/encrypted in `payload.customer` field

## Verify Event Publishing in Logs

Application logs:
```
INFO : Creating order: customer=customer-001, product=MacBook Pro 16, quantity=1, amount=3500000
DEBUG: Event published: eventType=ORDER_CREATED, severity=INFO
INFO : Order created successfully: orderId=550e8400-e29b-41d4-a716-446655440000
DEBUG: Sending event to Kafka: eventId=123456789012345678, topic=event.audit.v1, mode=async
DEBUG: Event sent successfully: eventId=123456789012345678, topic=event.audit.v1, partition=0, offset=123
```

## @PublishEvent Annotation Options

### OrderService.java Example

```java
@PublishEvent(
    eventType = "ORDER_CREATED",           // Event type (required)
    severity = EventSeverity.INFO,         // Event severity (INFO, WARNING, ERROR, CRITICAL)
    phase = PublishEvent.Phase.AFTER_RETURNING,  // Execution timing
    payloadIndex = -1,                     // -1: return value, 0~N: parameter index
    failOnError = false                    // Whether to throw exception on publish failure
)
public OrderCreatedPayload createOrder(...) {
    // Business logic
}
```

### Phase Options

- **BEFORE**: Publish event before method execution
- **AFTER_RETURNING**: Publish event after successful method completion
- **AFTER**: Always publish event after method completion (even on exception)

## DLQ (Dead Letter Queue) Testing

### Kafka Down Scenario

1. Stop Kafka:
```bash
docker-compose stop kafka
```

2. Call API:
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{...}'
```

3. Check logs:
```
ERROR: All retry attempts exhausted for event: eventId=123456789012345678
WARN : Sending failed event to DLQ (async): eventId=123456789012345678, dlqTopic=event.audit.dlq.v1
ERROR: Failed to send event to DLQ (async): eventId=123456789012345678, dlqTopic=event.audit.dlq.v1
ERROR: Event backed up to file with restricted permissions: eventId=123456789012345678, file=./dlq-backup/123456789012345678.json
```

4. Check backup files:
```bash
ls -la dlq-backup/
# -rw------- 1 user group 2048 Jan 17 10:30 123456789012345678.json
```

## Performance Monitoring

### Check Curve Metrics

```bash
# Event publish success/failure counts
# DLQ send counts
# Average processing time
```

## Troubleshooting

### 1. Kafka Connection Failure

```
ERROR: Failed to send event to Kafka
```

**Solution**:
- Verify Kafka is running: `docker-compose ps`
- Check bootstrap-servers setting: `localhost:9092`

### 2. PII Encryption Failure

**Symptoms:**
```
ERROR: PII encryption key is not configured!
ERROR: Exception occurs when using @PiiField(strategy = PiiStrategy.ENCRYPT)
```

**Solution**:

1. **Generate encryption key**:
```bash
openssl rand -base64 32
# Output example: K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

2. **Set environment variables**:
```bash
# Linux/macOS
export PII_ENCRYPTION_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
export PII_HASH_SALT=your-random-salt-value

# Windows PowerShell
$env:PII_ENCRYPTION_KEY="K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols="
$env:PII_HASH_SALT="your-random-salt-value"
```

3. **application.yml configuration**:
```yaml
curve:
  pii:
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}
      salt: ${PII_HASH_SALT}
```

**Warning**: Do not hardcode encryption keys directly in application.yml!

### 3. Events Not Being Published

**Checklist**:
- Verify `@PublishEvent` annotation is correctly applied
- Verify AOP is enabled: `curve.aop.enabled=true`
- Verify method is public (AOP only supports public methods)

### 4. Configuration Validation Failure

**Symptoms:**
```
APPLICATION FAILED TO START
Reason: workerId must be 1023 or less
```

**Solution**:
- Verify configuration values meet validation rules
- `curve.id-generator.worker-id`: Range 0 ~ 1023
- `curve.kafka.topic`: Cannot be empty string
- `curve.retry.max-attempts`: Must be 1 or greater
- See [CONFIGURATION.md](../docs/CONFIGURATION.en.md#configuration-validation) for detailed validation rules

## Next Steps

1. **Spring Security Integration**: Include actual user authentication info in EventActor
2. **Distributed Tracing**: Integrate Sleuth/Zipkin for automatic traceId extraction
3. **Custom Event Types**: Extend domain-specific event payloads
4. **Event Consumer**: Create Kafka Consumer to process events

## References

- [Curve Main README](../README.md)
- [Curve Configuration Guide](../docs/CONFIGURATION.en.md)
- [Kafka UI](http://localhost:8080)
