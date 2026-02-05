---
title: Annotation Reference - Curve API
description: Complete reference for Curve annotations including @PublishEvent, @PiiField, and more.
keywords: curve annotations, publishevent, piifield, api reference
---

# Annotation Reference

Complete reference for all Curve annotations.

## @PublishEvent

Marks a method to automatically publish events after execution.

### Package

```java
io.github.closeup1202.curve.spring.audit.annotation.PublishEvent
```

### Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `eventType` | String | No | "" | Unique event type identifier (auto-generated from method name if empty) |
| `severity` | EventSeverity | No | INFO | Event severity level |
| `payloadIndex` | int | No | -1 | Parameter index for payload (-1: use return value, 0+: use parameter) |
| `payload` | String (SpEL) | No | "" | Payload extraction SpEL expression (overrides payloadIndex) |
| `phase` | Phase | No | AFTER_RETURNING | When to publish (BEFORE, AFTER_RETURNING, AFTER) |
| `failOnError` | boolean | No | false | Throw exception if event publishing fails |
| `outbox` | boolean | No | false | Enable transactional outbox pattern |
| `aggregateType` | String | No | "" | Entity type for outbox (required if outbox=true) |
| `aggregateId` | String (SpEL) | No | "" | Entity ID SpEL expression for outbox (required if outbox=true) |

### Example

```java
@PublishEvent(
    eventType = "ORDER_CREATED",
    severity = EventSeverity.INFO,
    payload = "#result.toDto()",
    phase = PublishEvent.Phase.AFTER_RETURNING,
    failOnError = false,
    outbox = true,
    aggregateType = "Order",
    aggregateId = "#result.id"
)
public Order createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}
```

### Phase Options

```java
public enum Phase {
    BEFORE,          // Publish before method execution
    AFTER_RETURNING, // Publish after successful execution (default)
    AFTER            // Publish after execution (even if method throws exception)
}
```

### Payload Extraction Examples

```java
// Use return value (default when payloadIndex=-1 and payload="")
@PublishEvent(eventType = "ORDER_CREATED")
public Order createOrder(OrderRequest req) { ... }

// Use specific parameter by index
@PublishEvent(
    eventType = "ORDER_SUBMITTED",
    payloadIndex = 0  // Use first parameter
)
public Order submitOrder(OrderSubmission submission) { ... }

// Use SpEL expression (overrides payloadIndex)
@PublishEvent(
    eventType = "USER_UPDATED",
    payload = "#args[0].toEventDto()"
)
public User updateUser(UserUpdateRequest request) { ... }

// SpEL with return value method call
@PublishEvent(
    eventType = "ORDER_COMPLETED",
    payload = "#result.toCompletedDto()"
)
public Order completeOrder(String reason) { ... }
```

---

## @PiiField

Marks a field for automatic PII protection.

### Package

```java
io.github.closeup1202.curve.spring.pii.annotation.PiiField
```

### Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `type` | PiiType | Yes | - | Type of PII data |
| `strategy` | PiiStrategy | Yes | - | Protection strategy |
| `condition` | String (SpEL) | No | "" | Conditional protection |

### PiiType Values

- `EMAIL` - Email addresses
- `PHONE` - Phone numbers
- `SSN` - Social Security Numbers
- `NAME` - Person names
- `ADDRESS` - Physical addresses
- `CREDIT_CARD` - Credit card numbers
- `IP_ADDRESS` - IP addresses
- `GENERIC` - Custom sensitive data

### PiiStrategy Values

- `MASK` - Pattern-based masking
- `ENCRYPT` - AES-256-GCM encryption
- `HASH` - SHA-256 hashing

### Example

```java
public class UserPayload implements DomainEventPayload {

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;

    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;

    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.HASH)
    private String name;
}
```

---

## Enums

### EventSeverity

```java
public enum EventSeverity {
    INFO,     // Normal operations (default)
    WARN,     // Warnings
    ERROR,    // Errors requiring attention
    CRITICAL  // Critical failures requiring immediate action
}
```

---

## SpEL Context Variables

Available in SpEL expressions:

| Variable | Description | Example |
|----------|-------------|---------|
| `#result` | Method return value | `#result` |
| `#args[n]` | Method arguments (0-indexed) | `#args[0]` |
| `#root` | Root evaluation context | `#root.methodName` |
| `#this` | Current object | `#this.getId()` |

### Example

```java
// Use return value
@PublishEvent(payload = "#result")

// Use method argument
@PublishEvent(payload = "#args[0].toDto()")

// Call method on return value
@PublishEvent(payload = "#result.getId()")

// Call method on parameter
@PublishEvent(
    payload = "#args[0].toPayloadDto()"
)
```

---

## Best Practices

1. **Event Type Naming**: Use SCREAMING_SNAKE_CASE (e.g., `ORDER_CREATED`)
2. **Severity Levels**: Choose appropriate severity for filtering (INFO, WARN, ERROR, CRITICAL)
3. **SpEL Expressions**: Keep simple for maintainability
4. **PII Protection**: Apply to all sensitive fields
5. **Phase Selection**: Use AFTER_RETURNING for normal cases, BEFORE for pre-validation events
6. **Error Handling**: Set `failOnError=false` (default) to prevent business logic failure from event publishing issues
7. **Transactional Outbox**: Enable for critical events requiring guaranteed delivery

---

## See Also

- [Declarative Publishing Guide](../features/declarative-publishing.md)
- [PII Protection Guide](../features/pii-protection.md)
- [Configuration Properties](properties.md)
