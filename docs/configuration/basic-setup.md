# Basic Setup Guide

This guide covers the fundamental configuration options for Curve.

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

## Configuration Validation

Curve automatically validates configuration values at application startup using `@Validated`.

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

### Method 2: Auto-Generation (Caution)

Auto-generate Worker ID based on MAC address.

```yaml
curve:
  id-generator:
    auto-generate: true
```

## Kafka Transmission Mode Configuration

Curve supports both synchronous and asynchronous transmission modes.

### Synchronous Transmission (Default)

```yaml
curve:
  kafka:
    async-mode: false  # Synchronous transmission
    request-timeout-ms: 30000  # 30 seconds
```

### Asynchronous Transmission

```yaml
curve:
  kafka:
    async-mode: true  # Asynchronous transmission
    async-timeout-ms: 5000  # 5 seconds timeout
```

## DLQ Configuration

The Dead Letter Queue stores events that fail to be transmitted.

```yaml
curve:
  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1  # Enable DLQ
```

## Retry Configuration

Automatic retry configuration in case of transmission failure.

```yaml
curve:
  retry:
    enabled: true  # Enable retry
    max-attempts: 3  # Maximum 3 attempts
    initial-interval: 1000  # Initial 1 second wait
    multiplier: 2.0  # Increase by 2x (1s -> 2s -> 4s)
    max-interval: 10000  # Maximum 10 seconds
```

## PII Protection Configuration

Through PII (Personally Identifiable Information) protection features, sensitive data can be automatically masked, encrypted, or hashed.

```yaml
curve:
  pii:
    enabled: true  # Enable PII protection (default: true)
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}  # Encryption key (environment variable required)
      salt: ${PII_HASH_SALT}              # Hashing salt (environment variable recommended)
```

## Outbox Configuration

Use the Transactional Outbox Pattern to ensure atomicity between DB transactions and event publishing.

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

## Serialization Configuration

Configure the event payload serialization method.

```yaml
curve:
  serde:
    type: JSON  # JSON (default), AVRO, PROTOBUF
```

## Logging Configuration

By default, Curve outputs minimal logs. To see detailed configuration information or internal operations, enable the DEBUG level.

```yaml
logging:
  level:
    com.project.curve: DEBUG
```
