---
title: Features Overview - Curve Event Publishing Library
description: Comprehensive overview of Curve features including declarative publishing, PII protection, failure recovery, and observability.
keywords: curve features, event-driven features, kafka features, microservices patterns
---

# Features Overview

Curve provides production-ready features for event-driven microservices out of the box.

## Core Features

### :material-gesture-tap-button: Declarative Event Publishing

Publish events with a single annotation - no boilerplate code required.

```java
@PublishEvent(eventType = "ORDER_CREATED")
public Order createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}
```

**Benefits:**

- 90% less code compared to manual Kafka usage
- Type-safe with compile-time validation
- SpEL support for flexible payload extraction

[:octicons-arrow-right-24: Learn more](declarative-publishing.md)

---

### :material-package-variant: Standardized Event Structure

All events follow CloudEvents-inspired schema:

```json
{
  "eventId": "7355889748156289024",
  "eventType": "ORDER_CREATED",
  "occurredAt": "2026-02-03T10:30:00Z",
  "publishedAt": "2026-02-03T10:30:00.123Z",
  "severity": "INFO",
  "metadata": {
    "source": { ... },
    "actor": { ... },
    "trace": { ... },
    "tags": { ... }
  },
  "payload": { ... }
}
```

**Metadata includes:**

- **Source**: Service name, version, hostname
- **Actor**: User ID, session ID, roles
- **Trace**: Distributed tracing (trace ID, span ID)
- **Tags**: Custom key-value pairs

---

### :material-shield-check: 3-Tier Failure Recovery

**Main Topic → DLQ → Local File Backup**

Zero event loss even when Kafka is completely down.

1. **Primary**: Publish to main Kafka topic
2. **DLQ**: Failed events sent to Dead Letter Queue
3. **Backup**: If Kafka unavailable, save to local disk

[:octicons-arrow-right-24: Failure Recovery Guide](failure-recovery.md)

---

### :material-lock: Automatic PII Protection

Annotate sensitive fields and Curve handles the rest:

```java
public class UserPayload implements DomainEventPayload {

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;  // → "j***@ex***.com"

    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;  // → Encrypted with AES-256-GCM

    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.HASH)
    private String name;   // → SHA-256 hashed
}
```

[:octicons-arrow-right-24: PII Protection Guide](pii-protection.md)

---

### :material-lightning-bolt: High Performance

| Mode | Throughput | Use Case |
|------|------------|----------|
| **Sync** | ~500 TPS | Strong consistency |
| **Async** | ~10,000+ TPS | High throughput |
| **Transactional Outbox** | ~1,000 TPS | Atomicity guarantee |

**Async Mode** with MDC context propagation:

```yaml
curve:
  kafka:
    async-mode: true
    async-timeout-ms: 5000
```

---

### :material-database: Transactional Outbox Pattern

Guarantee atomicity between database and event publishing:

```java
@Transactional
@PublishEvent(
    eventType = "ORDER_CREATED",
    outbox = true,
    aggregateType = "Order",
    aggregateId = "#result.id"
)
public Order createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}
```

**How it works:**

1. Event saved to DB in same transaction
2. Background poller publishes to Kafka
3. Exponential backoff for retries
4. `SKIP LOCKED` prevents duplicate processing

[:octicons-arrow-right-24: Outbox Pattern Guide](transactional-outbox.md)

---

### :material-chart-line: Built-in Observability

#### Health Checks

```bash
curl http://localhost:8080/actuator/health/curve
```

```json
{
  "status": "UP",
  "details": {
    "kafkaProducerInitialized": true,
    "producerMetrics": 42,
    "topic": "event.audit.v1"
  }
}
```

#### Custom Metrics

```bash
curl http://localhost:8080/actuator/curve-metrics
```

```json
{
  "summary": {
    "totalEventsPublished": 1523,
    "successfulEvents": 1520,
    "failedEvents": 3,
    "successRate": "99.80%"
  }
}
```

[:octicons-arrow-right-24: Observability Guide](observability.md)

---

## Architecture

### Hexagonal Architecture (Ports & Adapters)

```
┌─────────────────────────────────────┐
│         Domain Layer (Core)         │
│  • EventEnvelope, EventMetadata     │
│  • Framework-independent            │
└───────────────┬─────────────────────┘
                │
        ┌───────┴────────┐
        │                │
        ▼                ▼
┌───────────┐      ┌────────────┐
│  Spring   │      │   Kafka    │
│ (Adapter) │      │ (Adapter)  │
└───────────┘      └────────────┘
```

**Benefits:**

- Framework-independent core
- Easy to test
- Extensible (can swap Kafka for RabbitMQ, etc.)

---

## What's Next?

<div class="grid cards" markdown>

-   :material-rocket-launch:{ .lg .middle } **Quick Start**

    ---

    Get up and running in 5 minutes

    [:octicons-arrow-right-24: Quick Start](../getting-started/quick-start.md)

-   :material-shield-lock:{ .lg .middle } **PII Protection**

    ---

    Protect sensitive data automatically

    [:octicons-arrow-right-24: PII Guide](pii-protection.md)

-   :material-alert-circle:{ .lg .middle } **Failure Recovery**

    ---

    Handle failures gracefully

    [:octicons-arrow-right-24: Failure Recovery](failure-recovery.md)

-   :material-cog:{ .lg .middle } **Configuration**

    ---

    Production-ready settings

    [:octicons-arrow-right-24: Configuration](../CONFIGURATION.md)

</div>
