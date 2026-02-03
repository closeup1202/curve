# Environment Profiles

This guide provides configuration examples and recommendations for different environments.

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

## Environment-specific Configuration Recommendations

### Local Development

- **Worker ID**: 1 (fixed)
- **Transmission Mode**: Synchronous (debugging convenience)
- **DLQ**: Enabled
- **Retry**: Minimum (fast failure)
- **Outbox**: Enabled (auto schema generation)

### Staging

- **Worker ID**: Environment variable
- **Transmission Mode**: Asynchronous
- **DLQ**: Enabled
- **Retry**: Medium level
- **Outbox**: Enabled

### Production

- **Worker ID**: Centrally managed (Consul/etcd)
- **Transmission Mode**: Based on business requirements
- **DLQ**: Mandatory enabled
- **Retry**: High level
- **Outbox**: Mandatory enabled (data consistency)
