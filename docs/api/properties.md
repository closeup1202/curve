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

Encryption key for PII (32 characters).

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
      default-key: ${PII_ENCRYPTION_KEY}
      salt: ${PII_HASH_SALT}

  outbox:
    enabled: true
    poll-interval-ms: 1000
    batch-size: 100
    max-retries: 3
    cleanup-enabled: true
    retention-days: 7
    cleanup-cron: "0 0 2 * * *"

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

See [Configuration Guide](../CONFIGURATION.en.md) for environment-specific examples.
