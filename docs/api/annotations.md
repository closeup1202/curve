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
| `eventType` | String | Yes | - | Unique event type identifier |
| `severity` | EventSeverity | No | INFO | Event severity level |
| `payload` | String (SpEL) | No | "#result" | Payload extraction expression |
| `tags` | Tag[] | No | {} | Custom metadata tags |
| `outbox` | boolean | No | false | Enable transactional outbox |
| `aggregateType` | String | No | "" | Entity type for outbox |
| `aggregateId` | String (SpEL) | No | "" | Entity ID for outbox |

### Example

```java
@PublishEvent(
    eventType = "ORDER_CREATED",
    severity = EventSeverity.INFO,
    payload = "#result.toDto()",
    tags = {
        @Tag(key = "region", value = "US"),
        @Tag(key = "channel", value = "web")
    },
    outbox = true,
    aggregateType = "Order",
    aggregateId = "#result.id"
)
public Order createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}
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

## @Tag

Adds custom metadata tag to events (used within @PublishEvent).

### Package

```java
io.github.closeup1202.curve.spring.audit.annotation.Tag
```

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `key` | String | Yes | Tag key |
| `value` | String | Yes | Tag value |

### Example

```java
@PublishEvent(
    eventType = "ORDER_CREATED",
    tags = {
        @Tag(key = "region", value = "US-WEST"),
        @Tag(key = "channel", value = "mobile"),
        @Tag(key = "version", value = "v2")
    }
)
```

---

## Enums

### EventSeverity

```java
public enum EventSeverity {
    DEBUG,   // Development/debugging
    INFO,    // Normal operations
    WARN,    // Warnings
    ERROR,   // Errors
    FATAL    // Critical failures
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

// Complex expression
@PublishEvent(
    payload = "new PayloadDto(#args[0], #result)"
)
```

---

## Best Practices

1. **Event Type Naming**: Use SCREAMING_SNAKE_CASE (e.g., `ORDER_CREATED`)
2. **Severity Levels**: Choose appropriate severity for filtering
3. **SpEL Expressions**: Keep simple for maintainability
4. **PII Protection**: Apply to all sensitive fields
5. **Tags**: Use for filtering and routing downstream

---

## See Also

- [Declarative Publishing Guide](../features/declarative-publishing.md)
- [PII Protection Guide](../features/pii-protection.md)
- [Configuration Properties](properties.md)
