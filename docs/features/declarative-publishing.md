---
title: Declarative Event Publishing with @PublishEvent
description: Learn how to use Curve's @PublishEvent annotation for declarative event publishing in Spring Boot with Kafka.
keywords: declarative event publishing, publishevent annotation, spring boot events, kafka annotations
---

# Declarative Event Publishing

The `@PublishEvent` annotation is the core feature of Curve, enabling declarative event publishing with minimal code.

## Basic Usage

```java
import io.github.closeup1202.curve.spring.audit.annotation.PublishEvent;

@Service
public class OrderService {

    @PublishEvent(eventType = "ORDER_CREATED")
    public Order createOrder(OrderRequest request) {
        return orderRepository.save(new Order(request));
    }
}
```

When `createOrder()` is called, Curve automatically:

1. Captures the method return value (`Order`)
2. Extracts metadata (trace ID, user, etc.)
3. Wraps it in `EventEnvelope`
4. Publishes to Kafka

---

## Annotation Parameters

### Required Parameters

#### `eventType` (String)

Unique identifier for this event type.

```java
@PublishEvent(eventType = "USER_REGISTERED")
```

**Naming conventions:**

- Use SCREAMING_SNAKE_CASE
- Be specific: `ORDER_CREATED` not just `CREATED`
- Include entity name: `USER_DELETED`, `PAYMENT_COMPLETED`

---

### Optional Parameters

#### `severity` (EventSeverity)

Event severity level for filtering and alerting.

```java
@PublishEvent(
    eventType = "PAYMENT_FAILED",
    severity = EventSeverity.ERROR
)
```

**Available values:**

- `DEBUG` - Development/debugging
- `INFO` - Normal operations (default)
- `WARN` - Warnings
- `ERROR` - Errors requiring attention
- `FATAL` - Critical failures

---

#### `payload` (SpEL Expression)

Extract specific data for the event payload using Spring Expression Language.

```java
@PublishEvent(
    eventType = "USER_UPDATED",
    payload = "#args[0].toEventDto()"  // Transform request
)
public User updateUser(UserUpdateRequest request) {
    return userRepository.save(request.toEntity());
}
```

**SpEL Variables:**

| Variable | Description | Example |
|----------|-------------|---------|
| `#result` | Method return value | `#result` |
| `#args[n]` | Method arguments | `#args[0]`, `#args[1]` |
| `#root` | Root evaluation context | `#root.methodName` |

**Examples:**

```java
// Use entire return value (default)
@PublishEvent(eventType = "ORDER_CREATED")
public Order createOrder(OrderRequest req) { ... }

// Use specific field
@PublishEvent(
    eventType = "ORDER_CREATED",
    payload = "#result.id"
)
public Order createOrder(OrderRequest req) { ... }

// Transform with custom method
@PublishEvent(
    eventType = "USER_CREATED",
    payload = "#result.toPublicDto()"
)
public User createUser(UserRequest req) { ... }

// Use method argument
@PublishEvent(
    eventType = "ORDER_SUBMITTED",
    payload = "#args[0]"
)
public Order submitOrder(OrderSubmission submission) { ... }
```

---

#### `tags` (Map<String, String>)

Add custom metadata tags to events.

```java
@PublishEvent(
    eventType = "ORDER_CREATED",
    tags = {
        @Tag(key = "region", value = "US-WEST"),
        @Tag(key = "channel", value = "mobile")
    }
)
```

Tags are useful for:

- Filtering events downstream
- Regional routing
- A/B testing markers
- Feature flags

---

#### Transactional Outbox Parameters

For guaranteed delivery with transactional outbox pattern:

```java
@Transactional
@PublishEvent(
    eventType = "ORDER_CREATED",
    outbox = true,                      // Enable outbox
    aggregateType = "Order",            // Entity type
    aggregateId = "#result.id"          // Entity ID
)
public Order createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}
```

**Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `outbox` | boolean | Enable transactional outbox |
| `aggregateType` | String | Entity type name |
| `aggregateId` | SpEL | Entity unique identifier |

[:octicons-arrow-right-24: Transactional Outbox Guide](transactional-outbox.md)

---

## Advanced Examples

### 1. Multi-Parameter Method

```java
@PublishEvent(
    eventType = "ORDER_SHIPPED",
    payload = "new ShipmentPayload(#args[0], #args[1], #result)"
)
public Shipment shipOrder(Long orderId, Address address) {
    // ...
    return shipment;
}
```

### 2. Conditional Publishing

Use Spring's conditional annotations:

```java
@ConditionalOnProperty(name = "features.audit", havingValue = "true")
@PublishEvent(eventType = "ADMIN_ACTION")
public void performAdminAction(AdminRequest request) {
    // ...
}
```

### 3. Method-Level Configuration

Override global settings per method:

```java
@Service
public class CriticalService {

    // High-priority event with custom severity
    @PublishEvent(
        eventType = "FRAUD_DETECTED",
        severity = EventSeverity.FATAL,
        tags = {@Tag(key = "priority", value = "critical")}
    )
    public FraudAlert detectFraud(Transaction tx) {
        // ...
    }
}
```

### 4. Async Method Publishing

Works with `@Async` methods:

```java
@Async
@PublishEvent(eventType = "REPORT_GENERATED")
public CompletableFuture<Report> generateReport(ReportRequest req) {
    Report report = reportGenerator.generate(req);
    return CompletableFuture.completedFuture(report);
}
```

!!! note "MDC Context Propagation"
    Curve automatically propagates MDC context (trace ID, etc.) to async threads.

---

## Best Practices

### :white_check_mark: DO

- Use descriptive event types: `USER_REGISTERED`, `ORDER_COMPLETED`
- Apply on service layer methods (not controllers or repositories)
- Keep payload minimal - only essential data
- Use `@PiiField` for sensitive data
- Set appropriate severity levels

### :x: DON'T

- Publish high-volume events in sync mode (use async)
- Include entire entities as payload (extract DTOs)
- Publish from controllers (breaks separation of concerns)
- Use generic event types like `CREATED` or `UPDATED`

---

## Troubleshooting

### Events Not Publishing

!!! failure "Events not appearing in Kafka"

    **Check:**

    1. `curve.enabled=true` in application.yml
    2. Method is called through Spring proxy (not `this.method()`)
    3. No exceptions thrown before method completes
    4. Kafka connection is healthy

    **Debug:**

    ```yaml
    logging:
      level:
        io.github.closeup1202.curve: DEBUG
    ```

### Payload Extraction Fails

!!! failure "SpEL evaluation error"

    **Common issues:**

    - Typo in SpEL expression
    - Accessing null fields
    - Wrong argument index

    **Solution:**

    ```java
    // Add null check
    @PublishEvent(
        eventType = "USER_UPDATED",
        payload = "#result != null ? #result.toDto() : null"
    )
    ```

---

## What's Next?

<div class="grid cards" markdown>

-   :material-shield-lock:{ .lg .middle } **PII Protection**

    ---

    Automatically protect sensitive data

    [:octicons-arrow-right-24: PII Guide](pii-protection.md)

-   :material-database:{ .lg .middle } **Transactional Outbox**

    ---

    Guarantee exactly-once delivery

    [:octicons-arrow-right-24: Outbox Pattern](transactional-outbox.md)

-   :material-code-braces:{ .lg .middle } **API Reference**

    ---

    Complete annotation reference

    [:octicons-arrow-right-24: API Docs](../api/annotations.md)

</div>
