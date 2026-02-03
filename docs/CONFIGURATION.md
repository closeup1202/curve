# Curve Configuration Guide

This document describes the detailed configuration methods for the Curve event publishing library.

## Table of Contents

- [Basic Configuration](#basic-configuration)
- [Configuration Validation](#configuration-validation)
- [Worker ID Configuration](#worker-id-configuration)
- [Kafka Transmission Mode Configuration](#kafka-transmission-mode-configuration)
- [DLQ Configuration](#dlq-configuration)
- [Retry Configuration](#retry-configuration)
- [AOP Configuration](#aop-configuration)
- [PII Protection Configuration](#pii-protection-configuration)
- [Outbox Configuration](#outbox-configuration)
- [Serialization Configuration](#serialization-configuration)
- [Avro Serialization Configuration](#avro-serialization-configuration)
- [Logging Configuration](#logging-configuration)

---

## Basic Configuration

### application.yml

```yaml
curve:
  enabled: true  # Enable Curve (default: true)

  kafka:
    topic: event.audit.v1  # Main topic name
    dlq-topic: event.audit.dlq.v1  # DLQ topic (optional)

  id-generator:
    worker-id: 1  # Snowflake Worker ID (0~1023)
    auto-generate: false  # Auto-generate based on MAC address
```

---

## Configuration Validation

Curve automatically validates configuration values at application startup using `@Validated`.
If invalid configuration values are entered, the application will fail to start with a clear error message.

### Validation Rules

| Configuration Item | Validation Rule | Error Message |
|----------|----------|------------|
| `curve.kafka.topic` | Required (non-empty string) | "Kafka topic is required" |
| `curve.kafka.retries` | 0 or greater | "retries must be 0 or greater" |
| `curve.kafka.retry-backoff-ms` | Positive number | "retryBackoffMs must be positive" |
| `curve.kafka.request-timeout-ms` | Positive number | "requestTimeoutMs must be positive" |
| `curve.kafka.async-timeout-ms` | Positive number | "asyncTimeoutMs must be positive" |
| `curve.kafka.sync-timeout-seconds` | Positive number | "syncTimeoutSeconds must be positive" |
| `curve.kafka.dlq-executor-threads` | 1 or greater | "dlqExecutorThreads must be 1 or greater" |
| `curve.id-generator.worker-id` | 0 ~ 1023 | "workerId must be between 0 and 1023" |
| `curve.retry.max-attempts` | 1 or greater | "maxAttempts must be 1 or greater" |
| `curve.retry.initial-interval` | Positive number | "initialInterval must be positive" |
| `curve.retry.multiplier` | 1 or greater | "multiplier must be 1 or greater" |
| `curve.retry.max-interval` | Positive number | "maxInterval must be positive" |
| `curve.outbox.poll-interval-ms` | Positive number | "pollIntervalMs must be positive" |
| `curve.outbox.batch-size` | 1 ~ 1000 | "batchSize must be between 1 and 1000" |
| `curve.outbox.max-retries` | 1 or greater | "maxRetries must be 1 or greater" |
| `curve.outbox.send-timeout-seconds` | Positive number | "sendTimeoutSeconds must be positive" |
| `curve.outbox.retention-days` | 1 or greater | "retentionDays must be 1 or greater" |

### Validation Error Example

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

## Worker ID Configuration

The Snowflake ID Generator uses a Worker ID to generate unique IDs in a distributed environment.

### Method 1: Explicit Worker ID Configuration (Recommended)

Assign a unique Worker ID to each instance.

```yaml
curve:
  id-generator:
    worker-id: 1  # Instance 1
    auto-generate: false
```

**Kubernetes Environment Example:**

```yaml
# deployment.yaml
env:
  - name: CURVE_ID_GENERATOR_WORKER_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.uid  # Use hashed Pod UID
```

**Docker Compose Example:**

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

### Method 2: Auto-Generation (Caution)

Auto-generate Worker ID based on MAC address.

```yaml
curve:
  id-generator:
    auto-generate: true
```

**⚠️ Caution:**
- In virtual environments, MAC addresses may be identical, leading to conflicts
- MAC addresses may change when containers restart
- Explicit configuration is recommended for production environments

### Worker ID Range

- **Minimum value:** 0
- **Maximum value:** 1023
- **Recommended:** Manage using environment variables or configuration management systems (Consul, etcd)

---

## Kafka Transmission Mode Configuration

Curve supports both synchronous and asynchronous transmission modes.

### Synchronous Transmission (Default)

```yaml
curve:
  kafka:
    async-mode: false  # Synchronous transmission
    request-timeout-ms: 30000  # 30 seconds
```

**Characteristics:**
- ✅ Guaranteed transmission (clear success/failure confirmation)
- ✅ Easy error handling
- ❌ Performance degradation (blocking)
- ❌ Limited throughput

**Suitable for:**
- Financial transactions, payments, etc. where accuracy is critical
- Cases where event loss is not acceptable
- Low throughput (tens to hundreds of TPS)

### Asynchronous Transmission

```yaml
curve:
  kafka:
    async-mode: true  # Asynchronous transmission
    async-timeout-ms: 5000  # 5 seconds timeout
```

**Characteristics:**
- ✅ High performance (non-blocking)
- ✅ High throughput capability
- ⚠️ Callback-based error handling
- ⚠️ Relies on DLQ in case of transmission failure

**Suitable for:**
- Logs, analytics events, etc. where some loss is acceptable
- High throughput required (thousands to tens of thousands of TPS)
- Cases where latency is critical

### Performance Comparison

| Item | Synchronous Transmission | Asynchronous Transmission |
|------|-----------|-------------|
| Throughput (TPS) | ~500 | ~10,000+ |
| Latency | High (10-50ms) | Low (1-5ms) |
| Transmission Guarantee | Strong | Moderate (DLQ dependent) |
| Resource Usage | High | Low |

---

## DLQ Configuration

The Dead Letter Queue stores events that fail to be transmitted.

### Enable DLQ

```yaml
curve:
  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1  # Enable DLQ
```

### Disable DLQ

```yaml
curve:
  kafka:
    topic: event.audit.v1
    dlq-topic:  # Empty value or not configured
```

⚠️ **Caution:** Disabling DLQ may result in event loss in case of transmission failure.

### DLQ Message Structure

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

## Retry Configuration

Automatic retry configuration in case of transmission failure.

### Basic Configuration

```yaml
curve:
  retry:
    enabled: true  # Enable retry
    max-attempts: 3  # Maximum 3 attempts
    initial-interval: 1000  # Initial 1 second wait
    multiplier: 2.0  # Increase by 2x (1s -> 2s -> 4s)
    max-interval: 10000  # Maximum 10 seconds
```

### Exponential Backoff Example

| Attempt | Wait Time |
|------|-----------|
| 1st | 0ms (immediate) |
| 2nd | 1,000ms (1 second) |
| 3rd | 2,000ms (2 seconds) |
| 4th | 4,000ms (4 seconds) |

### Disable Retry

```yaml
curve:
  retry:
    enabled: false
```

---

## AOP Configuration

AOP configuration based on `@PublishEvent` annotation.

### Enable AOP (Default)

```yaml
curve:
  aop:
    enabled: true
```

### Disable AOP

```yaml
curve:
  aop:
    enabled: false
```

---

## PII Protection Configuration

Through PII (Personally Identifiable Information) protection features, sensitive data can be automatically masked, encrypted, or hashed.

### Basic Configuration

```yaml
curve:
  pii:
    enabled: true  # Enable PII protection (default: true)
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}  # Encryption key (environment variable required)
      salt: ${PII_HASH_SALT}              # Hashing salt (environment variable recommended)
```

### Encryption Key Configuration (Required)

When using `@PiiField(strategy = PiiStrategy.ENCRYPT)`, an encryption key is mandatory.

**1. Generate Key**
```bash
# Generate 32-byte AES-256 key
openssl rand -base64 32
# Output example: K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
```

**2. Set Environment Variable (Recommended)**
```bash
# Linux/macOS
export PII_ENCRYPTION_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
export PII_HASH_SALT=your-random-salt-value

# Windows PowerShell
$env:PII_ENCRYPTION_KEY="K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols="
$env:PII_HASH_SALT="your-random-salt-value"
```

**3. application.yml Configuration**
```yaml
curve:
  pii:
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}
      salt: ${PII_HASH_SALT}
```

**⚠️ Caution:**
- Do not hardcode the encryption key directly in application.yml
- For production environments, use environment variables or external secret management systems (Vault, AWS Secrets Manager)
- If the key is not configured, an exception will occur when using the ENCRYPT strategy

### PII Strategies

| Strategy | Description | Reversible | Example |
|------|------|----------|------|
| `MASK` | Pattern-based masking | Not possible | `John Doe` → `John **` |
| `ENCRYPT` | AES-256-GCM encryption | Possible (key required) | Encrypted Base64 string |
| `HASH` | SHA-256 hashing | Not possible | Hashed Base64 string |

### Masking Patterns by PII Type

| Type | Masking Pattern | Example |
|------|------------|------|
| `NAME` | Keep first character, mask rest | `John Doe` → `J*** ***` |
| `EMAIL` | Keep local part, mask domain | `user@example.com` → `user@***.com` |
| `PHONE` | Keep first 3 and last 4 digits only | `010-1234-5678` → `010****5678` |
| `DEFAULT` | Keep first 30%, mask rest | `Seoul Gangnam` → `Seou***` |

### Usage Example

```java
public class CustomerInfo {
    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
    private String name;

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;

    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;

    @PiiField(strategy = PiiStrategy.HASH)
    private String ssn;  // Social Security Number
}
```

### Kubernetes Environment Configuration

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
# Create Secret
kubectl create secret generic curve-secrets \
  --from-literal=pii-encryption-key=$(openssl rand -base64 32) \
  --from-literal=pii-hash-salt=$(openssl rand -base64 16)
```

---

## Outbox Configuration

Use the Transactional Outbox Pattern to ensure atomicity between DB transactions and event publishing.

### Basic Configuration

```yaml
curve:
  outbox:
    enabled: true  # Enable Outbox
    poll-interval-ms: 1000  # Polling interval (1 second)
    batch-size: 100  # Batch size
    max-retries: 3  # Maximum retry count
    send-timeout-seconds: 10  # Send timeout
    cleanup-enabled: true  # Enable old event cleanup
    retention-days: 7  # Retention period (7 days)
    cleanup-cron: "0 0 2 * * *"  # Cleanup job execution time (2 AM daily)
    initialize-schema: embedded  # Schema initialization mode (embedded, always, never)
```

### Schema Initialization Modes

- `embedded`: Automatically create tables only for embedded DBs like H2, HSQLDB (default)
- `always`: Always attempt to create tables (if they don't exist)
- `never`: No automatic creation (recommended when using Flyway/Liquibase)

---

## Serialization Configuration

Configure the event payload serialization method.

```yaml
curve:
  serde:
    type: JSON  # JSON (default), AVRO, PROTOBUF
```

---

## Avro Serialization Configuration

Additional configuration is required to serialize events using Avro.

### 1. Curve Configuration

```yaml
curve:
  serde:
    type: AVRO
    schema-registry-url: http://localhost:8081  # Schema Registry address
```

### 2. Spring Kafka Configuration (Required)

You must explicitly specify the `value-serializer` in Spring Kafka's Producer configuration.

```yaml
spring:
  kafka:
    producer:
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    properties:
      schema.registry.url: http://localhost:8081
```

**⚠️ Caution:**
- When `curve.serde.type=AVRO` is configured, Curve internally creates a `GenericRecord` object and passes it to KafkaTemplate.
- Therefore, you must use `KafkaAvroSerializer` so that KafkaTemplate can serialize `GenericRecord`.
- `schema.registry.url` may need to be configured in both `curve.serde` and `spring.kafka.properties` (for Curve internal logic and Kafka Serializer).

### Avro Schema Structure

Curve internally uses the following fixed Avro schema. Some fields in `payload` and `metadata` are stored as JSON strings for flexibility.

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

## Complete Configuration Examples

### Production Environment (Stability-focused)

```yaml
curve:
  enabled: true

  id-generator:
    worker-id: ${INSTANCE_ID}  # Injected from environment variable
    auto-generate: false

  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
    async-mode: false  # Synchronous transmission
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
      default-key: ${PII_ENCRYPTION_KEY}  # Environment variable required
      salt: ${PII_HASH_SALT}

  outbox:
    enabled: true
    initialize-schema: never  # Use Flyway
    cleanup-enabled: true
    retention-days: 14
```

### Development/Test Environment (Performance-focused)

```yaml
curve:
  enabled: true

  id-generator:
    worker-id: 1
    auto-generate: false

  kafka:
    topic: event.audit.dev.v1
    dlq-topic: event.audit.dlq.dev.v1
    async-mode: true  # Asynchronous transmission
    async-timeout-ms: 3000
    retries: 3

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
```

### High-Performance Environment

```yaml
curve:
  enabled: true

  id-generator:
    worker-id: ${WORKER_ID}
    auto-generate: false

  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
    async-mode: true  # Asynchronous transmission
    async-timeout-ms: 5000
    retries: 1  # Minimum retry

  retry:
    enabled: false  # Disable retry (performance priority)

  aop:
    enabled: true
```

---

## Environment-specific Configuration Recommendations

### Local Development

- Worker ID: 1 (fixed)
- Transmission Mode: Synchronous (debugging convenience)
- DLQ: Enabled
- Retry: Minimum (fast failure)
- Outbox: Enabled (auto schema generation)

### Staging

- Worker ID: Environment variable
- Transmission Mode: Asynchronous
- DLQ: Enabled
- Retry: Medium level
- Outbox: Enabled

### Production

- Worker ID: Centrally managed (Consul/etcd)
- Transmission Mode: Based on business requirements
- DLQ: Mandatory enabled
- Retry: High level
- Outbox: Mandatory enabled (data consistency)

---

## Troubleshooting

### Worker ID Conflict

**Symptom:** Identical IDs are being generated

**Solution:**
```yaml
curve:
  id-generator:
    worker-id: ${UNIQUE_INSTANCE_ID}
```

### Transmission Timeout

**Symptom:** `TimeoutException` occurs

**Solution:**
```yaml
curve:
  kafka:
    request-timeout-ms: 60000  # Increase timeout
```

### High Latency

**Symptom:** Event publishing is slow

**Solution:**
```yaml
curve:
  kafka:
    async-mode: true  # Switch to asynchronous mode
```

### PII Encryption Key Not Configured

**Symptom:**
```
ERROR: PII encryption key is not configured!
ERROR: An exception will occur when using @PiiField(strategy = PiiStrategy.ENCRYPT).
```

**Solution:**
```bash
# 1. Generate key
openssl rand -base64 32

# 2. Set environment variable
export PII_ENCRYPTION_KEY=generated_key_value

# 3. Configure application.yml
curve:
  pii:
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}
```

### Configuration Validation Failure

**Symptom:**
```
APPLICATION FAILED TO START
Reason: workerId must be 1023 or less
```

**Solution:**
- Check if configuration values meet validation rules
- Refer to validation rules in the [Configuration Validation](#configuration-validation) section

---

## Logging Configuration

By default, Curve outputs minimal logs. To see detailed configuration information or internal operations, enable the DEBUG level.

### Basic Logging (INFO)

In the default configuration, only the following log is output:

```
INFO  c.p.c.a.CurveAutoConfiguration : Curve auto-configuration enabled (disable with curve.enabled=false)
```

### Enable DEBUG Logging

```yaml
logging:
  level:
    com.project.curve: DEBUG
```

### Information Available at DEBUG Level

| Item | Description |
|------|------|
| Kafka Producer Configuration | Detailed configuration such as retries, timeout, async-mode |
| RetryTemplate Configuration | max-attempts, detailed backoff policy |
| SnowflakeIdGenerator | Worker ID and initialization information |
| DLQ ExecutorService | Thread pool size, shutdown timeout |
| PII Module | Encryption/salt configuration status, module registration |
| Event Transmission | Transmission details per event (eventId, topic, partition, offset) |
| Outbox Publisher | Polling, publishing, cleanup job logs |

### Enable DEBUG for Specific Modules Only

```yaml
logging:
  level:
    # DEBUG for Kafka transmission only
    com.project.curve.kafka: DEBUG

    # DEBUG for Auto-Configuration only
    com.project.curve.autoconfigure: DEBUG

    # DEBUG for PII processing only
    com.project.curve.spring.pii: DEBUG

    # DEBUG for Outbox only
    com.project.curve.spring.outbox: DEBUG
```

---

## Additional Information

- [Snowflake ID Algorithm](https://en.wikipedia.org/wiki/Snowflake_ID)
- [Kafka Producer Configuration](https://kafka.apache.org/documentation/#producerconfigs)
- [Spring Retry](https://docs.spring.io/spring-retry/docs/current/reference/html/)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
