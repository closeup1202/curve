---
title: Configuration Properties - Curve Reference
description: Complete reference for all Curve configuration properties in application.yml
keywords: curve configuration, spring boot properties, yaml configuration
---

# Configuration Properties

Complete reference for all Curve configuration properties.

## Core Configuration

### curve.enabled

Enable or disable Curve.

- **Type**: `boolean`
- **Default**: `true`

```yaml
curve:
  enabled: true
```

---

## Kafka Configuration

### curve.kafka.topic

Main Kafka topic for event publishing.

- **Type**: `string`
- **Required**: Yes

```yaml
curve:
  kafka:
    topic: event.audit.v1
```

### curve.kafka.dlq-topic

Dead Letter Queue topic for failed events.

- **Type**: `string`
- **Required**: No

```yaml
curve:
  kafka:
    dlq-topic: event.audit.dlq.v1
```

### curve.kafka.async-mode

Enable asynchronous publishing for high throughput.

- **Type**: `boolean`
- **Default**: `false`

```yaml
curve:
  kafka:
    async-mode: true
```

### curve.kafka.async-timeout-ms

Timeout for async publishing (milliseconds).

- **Type**: `integer`
- **Default**: `5000`

```yaml
curve:
  kafka:
    async-timeout-ms: 5000
```

### curve.kafka.retries

Number of Kafka send retries.

- **Type**: `integer`
- **Default**: `3`

```yaml
curve:
  kafka:
    retries: 3
```

### curve.kafka.retry-backoff-ms

Backoff time between retries (milliseconds).

- **Type**: `integer`
- **Default**: `1000`

```yaml
curve:
  kafka:
    retry-backoff-ms: 1000
```

### curve.kafka.request-timeout-ms

Kafka request timeout (milliseconds).

- **Type**: `integer`
- **Default**: `30000`

```yaml
curve:
  kafka:
    request-timeout-ms: 30000
```

---

## Retry Configuration

### curve.retry.enabled

Enable retry mechanism for failed publishes.

- **Type**: `boolean`
- **Default**: `true`

```yaml
curve:
  retry:
    enabled: true
```

### curve.retry.max-attempts

Maximum retry attempts.

- **Type**: `integer`
- **Default**: `3`

```yaml
curve:
  retry:
    max-attempts: 3
```

### curve.retry.initial-interval

Initial retry interval (milliseconds).

- **Type**: `integer`
- **Default**: `1000`

```yaml
curve:
  retry:
    initial-interval: 1000
```

### curve.retry.multiplier

Retry backoff multiplier.

- **Type**: `double`
- **Default**: `2.0`

```yaml
curve:
  retry:
    multiplier: 2.0
```

### curve.retry.max-interval

Maximum retry interval (milliseconds).

- **Type**: `integer`
- **Default**: `10000`

```yaml
curve:
  retry:
    max-interval: 10000
```

---

## PII Configuration

### curve.pii.enabled

Enable PII protection.

- **Type**: `boolean`
- **Default**: `true`

```yaml
curve:
  pii:
    enabled: true
```

### curve.pii.crypto.default-key

Encryption key for PII (Base64-encoded 32-byte key).

- **Type**: `string`
- **Required**: For ENCRYPT strategy

```yaml
curve:
  pii:
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}
```

### curve.pii.crypto.salt

Salt for hashing PII.

- **Type**: `string`
- **Required**: For HASH strategy

```yaml
curve:
  pii:
    crypto:
      salt: ${PII_HASH_SALT}
```

---

## Outbox Configuration

### curve.outbox.enabled

Enable transactional outbox pattern.

- **Type**: `boolean`
- **Default**: `false`

```yaml
curve:
  outbox:
    enabled: true
```

### curve.outbox.poll-interval-ms

Outbox poller interval (milliseconds).

- **Type**: `integer`
- **Default**: `1000`

```yaml
curve:
  outbox:
    poll-interval-ms: 1000
```

### curve.outbox.batch-size

Number of events processed per batch.

- **Type**: `integer`
- **Default**: `100`

```yaml
curve:
  outbox:
    batch-size: 100
```

### curve.outbox.max-retries

Max retries for failed outbox events.

- **Type**: `integer`
- **Default**: `3`

```yaml
curve:
  outbox:
    max-retries: 3
```

### curve.outbox.cleanup-enabled

Enable automatic cleanup of old events.

- **Type**: `boolean`
- **Default**: `true`

```yaml
curve:
  outbox:
    cleanup-enabled: true
```

### curve.outbox.retention-days

Days to retain completed events.

- **Type**: `integer`
- **Default**: `7`

```yaml
curve:
  outbox:
    retention-days: 7
```

### curve.outbox.cleanup-cron

Cron expression for cleanup job.

- **Type**: `string`
- **Default**: `"0 0 2 * * *"` (2 AM daily)

```yaml
curve:
  outbox:
    cleanup-cron: "0 0 2 * * *"
```

---

## Serialization Configuration

### curve.serde.type

Serialization format.

- **Type**: `enum`
- **Values**: `JSON`, `AVRO`, `PROTOBUF`
- **Default**: `JSON`

```yaml
curve:
  serde:
    type: JSON
```

---

## ID Generator Configuration

### curve.id-generator.worker-id

Snowflake worker ID (0-1023).

- **Type**: `integer`
- **Range**: `0-1023`
- **Default**: Auto-generated

```yaml
curve:
  id-generator:
    worker-id: 1
```

### curve.id-generator.auto-generate

Auto-generate worker ID from hostname/IP.

- **Type**: `boolean`
- **Default**: `true`

```yaml
curve:
  id-generator:
    auto-generate: true
```

---

## Async Executor Configuration

### curve.async.enabled

Enable dedicated async executor bean (`curveAsyncExecutor`).

- **Type**: `boolean`
- **Default**: `false`

```yaml
curve:
  async:
    enabled: true
```

### curve.async.core-pool-size

Core thread pool size for async executor.

- **Type**: `integer`
- **Default**: `2`

```yaml
curve:
  async:
    core-pool-size: 4
```

### curve.async.max-pool-size

Maximum thread pool size for async executor.

- **Type**: `integer`
- **Default**: `10`

```yaml
curve:
  async:
    max-pool-size: 20
```

### curve.async.queue-capacity

Task queue capacity for async executor.

- **Type**: `integer`
- **Default**: `500`

```yaml
curve:
  async:
    queue-capacity: 1000
```

---

## Backup Configuration

### curve.kafka.backup.s3-enabled

Enable S3 backup for failed events.

- **Type**: `boolean`
- **Default**: `false`

```yaml
curve:
  kafka:
    backup:
      s3-enabled: true
```

### curve.kafka.backup.s3-bucket

S3 bucket name for backup.

- **Type**: `string`
- **Required**: When `s3-enabled=true`

```yaml
curve:
  kafka:
    backup:
      s3-bucket: "my-event-backup"
```

### curve.kafka.backup.s3-prefix

S3 key prefix for backup files.

- **Type**: `string`
- **Default**: `"dlq-backup"`

```yaml
curve:
  kafka:
    backup:
      s3-prefix: "dlq-backup"
```

### curve.kafka.backup.local-enabled

Enable local file backup.

- **Type**: `boolean`
- **Default**: `true`

```yaml
curve:
  kafka:
    backup:
      local-enabled: true
```

---

## Kafka Additional Properties

### curve.kafka.sync-timeout-seconds

Timeout for synchronous send operations (seconds).

- **Type**: `integer`
- **Default**: `10`

```yaml
curve:
  kafka:
    sync-timeout-seconds: 10
```

### curve.kafka.dlq-executor-threads

Number of threads for DLQ executor.

- **Type**: `integer`
- **Default**: `1`

```yaml
curve:
  kafka:
    dlq-executor-threads: 2
```

---

## Outbox Additional Properties

### curve.outbox.send-timeout-seconds

Timeout for outbox event send operations (seconds).

- **Type**: `integer`
- **Default**: `10`

```yaml
curve:
  outbox:
    send-timeout-seconds: 10
```

### curve.outbox.publisher-enabled

Enable the outbox publisher (polling and sending events).

- **Type**: `boolean`
- **Default**: `true`

```yaml
curve:
  outbox:
    publisher-enabled: true
```

### curve.outbox.initialize-schema

Database schema initialization mode.

- **Type**: `string`
- **Values**: `embedded`, `always`, `never`
- **Default**: `embedded`

```yaml
curve:
  outbox:
    initialize-schema: always
```

---

## Security Configuration

### curve.security.use-forwarded-headers

Use X-Forwarded-* headers for IP extraction.

- **Type**: `boolean`
- **Default**: `false`

```yaml
curve:
  security:
    use-forwarded-headers: true  # When behind proxy
```

---

## Complete Example

```yaml title="application.yml"
spring:
  application:
    name: my-service
  kafka:
    bootstrap-servers: localhost:9092

curve:
  enabled: true

  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
    async-mode: false
    async-timeout-ms: 5000
    retries: 3
    retry-backoff-ms: 1000
    request-timeout-ms: 30000

  retry:
    enabled: true
    max-attempts: 3
    initial-interval: 1000
    multiplier: 2.0
    max-interval: 10000

  pii:
    enabled: true
    crypto:
      default-key: ${PII_ENCRYPTION_KEY}  # Base64-encoded 32-byte key
      salt: ${PII_HASH_SALT}

  async:
    enabled: true
    core-pool-size: 2
    max-pool-size: 10
    queue-capacity: 500

  outbox:
    enabled: true
    poll-interval-ms: 1000
    batch-size: 100
    max-retries: 3
    send-timeout-seconds: 10
    cleanup-enabled: true
    retention-days: 7
    cleanup-cron: "0 0 2 * * *"
    initialize-schema: embedded

  serde:
    type: JSON

  id-generator:
    worker-id: 1
    auto-generate: false

  security:
    use-forwarded-headers: false
```

---

## Environment-Specific Profiles

See [Configuration Guide](../CONFIGURATION.md) for environment-specific examples.
