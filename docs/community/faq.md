---
title: Frequently Asked Questions (FAQ)
description: Common questions and answers about Curve event publishing library
keywords: curve faq, troubleshooting, common questions
---

# Frequently Asked Questions

## General Questions

### What is Curve?

Curve is a declarative event publishing library for Spring Boot applications. It simplifies event-driven architecture by providing automatic Kafka publishing, PII protection, DLQ handling, and observability with minimal code.

### Why use Curve instead of Spring Kafka directly?

Curve reduces boilerplate by 90% while providing:

- Declarative annotations (`@PublishEvent`)
- Automatic PII protection
- Built-in DLQ and backup
- Standardized event structure
- Production-ready observability

### Is Curve production-ready?

Yes! Curve is designed for production use with:

- Comprehensive testing (>80% coverage)
- Battle-tested patterns (outbox, DLQ, retry)
- Observability and monitoring
- Active maintenance

---

## Compatibility

### What versions of Spring Boot are supported?

Curve supports Spring Boot 3.0+. For specific version compatibility, see the [Installation Guide](../getting-started/installation.md#version-compatibility).

### What Kafka versions are supported?

Kafka 2.8+ is supported. Kafka 3.0+ is recommended for best performance.

### Can I use Curve with Spring Boot 2.x?

Not currently. Curve requires Spring Boot 3.0+ due to Jakarta EE dependencies.

---

## Configuration

### How do I enable async publishing?

```yaml
curve:
  kafka:
    async-mode: true
    async-timeout-ms: 5000
```

See [Configuration Guide](../CONFIGURATION.md) for details.

### How do I configure multiple Kafka topics?

Currently, Curve uses a single main topic. For multiple topics, you can:

1. Use different event types and route downstream
2. Implement custom `EventProducer` with routing logic

### Can I disable Curve conditionally?

Yes, use Spring profiles:

```yaml
spring:
  profiles: prod

curve:
  enabled: true
```

---

## Features

### Does Curve support transactional publishing?

Yes! Use the [Transactional Outbox Pattern](../features/transactional-outbox.md):

```java
@Transactional
@PublishEvent(
    eventType = "ORDER_CREATED",
    outbox = true
)
```

### Can I publish events without Kafka?

Yes, Curve's hexagonal architecture allows custom implementations. See [Custom Implementation Guide](../api/custom-implementation.md).

### Does Curve support event replay?

Not built-in, but you can:

1. Republish from DLQ topic
2. Republish from outbox table
3. Use Kafka's consumer group reset

---

## Performance

### What's the throughput?

- **Sync mode**: ~500 TPS
- **Async mode**: ~10,000+ TPS
- **Transactional outbox**: ~1,000 TPS

### How can I improve performance?

1. Enable async mode
2. Increase Kafka batch size
3. Use connection pooling
4. Scale Kafka brokers

### Does Curve add latency?

Minimal overhead (~5-10ms) for:

- Annotation processing
- Metadata extraction
- PII protection

---

## Troubleshooting

### Events not publishing?

Check:

1. `curve.enabled=true`
2. Kafka connection is healthy
3. Method is called through Spring proxy (not `this.method()`)
4. No exceptions before method completes

See [Troubleshooting Guide](../TROUBLESHOOTING.md).

### PII not being masked?

Verify:

1. `curve.pii.enabled=true`
2. `@PiiField` annotation is present
3. Payload class implements `DomainEventPayload`

### Outbox events stuck in PENDING?

Check:

1. Outbox poller is running (enable DEBUG logging)
2. Kafka is accessible
3. No database connection issues

---

## Best Practices

### Should I use outbox for all events?

No. Use outbox for:

- Critical events (payments, orders)
- Events requiring atomicity

Use async for:

- High-volume events
- Non-critical events

### What should I include in event payload?

**Include:**

- Essential data for consumers
- Identifiers (IDs)
- Timestamps

**Exclude:**

- Large objects (>1MB)
- Binary data
- Entire entity graphs

### How should I name event types?

Use SCREAMING_SNAKE_CASE with entity and action:

- ✅ `ORDER_CREATED`, `PAYMENT_COMPLETED`
- ❌ `created`, `update`, `event`

---

## Advanced

### Can I customize event metadata?

Yes, implement `ContextProvider`:

```java
@Component
public class CustomContextProvider implements ContextProvider {
    @Override
    public Map<String, String> provide() {
        return Map.of("custom_key", "custom_value");
    }
}
```

### Can I use Curve with Kotlin?

Yes! Curve works with Kotlin:

```kotlin
@PublishEvent(eventType = "ORDER_CREATED")
fun createOrder(request: OrderRequest): Order {
    return orderRepository.save(Order(request))
}
```

### Can I publish events manually?

Yes, inject `EventProducer`:

```java
@Autowired
private EventProducer eventProducer;

public void manualPublish() {
    EventEnvelope<MyPayload> envelope = EventEnvelope.builder()
        .eventType("MANUAL_EVENT")
        .payload(new MyPayload())
        .build();

    eventProducer.publish(envelope);
}
```

---

## Contributing

### How can I contribute?

See our [Contributing Guide](../../CONTRIBUTING.md) for:

- Code contributions
- Bug reports
- Feature requests
- Documentation improvements

### Where can I ask questions?

- [GitHub Issues](https://github.com/closeup1202/curve/issues)
- [GitHub Discussions](https://github.com/closeup1202/curve/discussions)
- Email: closeup1202@gmail.com

---

## Still have questions?

Check our [Documentation](../index.md) or [open an issue](https://github.com/closeup1202/curve/issues).
