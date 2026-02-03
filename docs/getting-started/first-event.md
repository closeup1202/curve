---
title: Your First Event - Curve Tutorial
description: Step-by-step tutorial for publishing your first event with Curve. Learn event payloads, metadata, and testing.
keywords: curve tutorial, first event, kafka event tutorial, spring boot event publishing
---

# Your First Event

This tutorial walks you through creating a complete event publishing setup with Curve.

## Scenario

We'll build a user registration system that publishes a `USER_REGISTERED` event to Kafka.

## Step 1: Define Your Domain Model

```java title="User.java"
public class User {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Instant createdAt;

    // getters, setters, constructors
}
```

```java title="UserRequest.java"
public record UserRequest(
    String username,
    String email,
    String firstName,
    String lastName
) {}
```

## Step 2: Create Event Payload

Create a payload class that implements `DomainEventPayload`:

```java title="UserRegisteredPayload.java"
import io.github.closeup1202.curve.core.envelope.DomainEventPayload;
import io.github.closeup1202.curve.spring.pii.annotation.PiiField;
import io.github.closeup1202.curve.spring.pii.type.PiiType;
import io.github.closeup1202.curve.spring.pii.strategy.PiiStrategy;

public class UserRegisteredPayload implements DomainEventPayload {

    private Long userId;
    private String username;

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;

    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.HASH)
    private String firstName;

    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.HASH)
    private String lastName;

    private Instant registeredAt;

    // Constructor
    public UserRegisteredPayload(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.registeredAt = user.getCreatedAt();
    }

    // Getters
    // ...
}
```

!!! info "PII Protection"
    The `@PiiField` annotation automatically masks/hashes sensitive data before publishing.

## Step 3: Implement Service with @PublishEvent

```java title="UserService.java"
import io.github.closeup1202.curve.spring.audit.annotation.PublishEvent;
import io.github.closeup1202.curve.core.type.EventSeverity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    @PublishEvent(
        eventType = "USER_REGISTERED",
        severity = EventSeverity.INFO,
        payload = "new io.example.UserRegisteredPayload(#result)"
    )
    public User registerUser(UserRequest request) {
        // Business logic
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setCreatedAt(Instant.now());

        return userRepository.save(user);
    }
}
```

### Annotation Breakdown

| Parameter | Value | Description |
|-----------|-------|-------------|
| `eventType` | `"USER_REGISTERED"` | Unique identifier for this event type |
| `severity` | `EventSeverity.INFO` | Event severity level |
| `payload` | SpEL expression | Extracts data from method result |

!!! tip "SpEL Expressions"
    - `#result` - Method return value
    - `#args[0]` - First method argument
    - `#args[0].toEventDto()` - Custom transformation

## Step 4: Configure Application

```yaml title="application.yml"
spring:
  application:
    name: user-service
    version: 1.0.0

  kafka:
    bootstrap-servers: localhost:9094

  jpa:
    hibernate:
      ddl-auto: update

curve:
  enabled: true

  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
    async-mode: false  # Synchronous for reliability

  pii:
    enabled: true
    crypto:
      default-key: ${PII_ENCRYPTION_KEY:your-32-char-secret-key-here}
```

## Step 5: Test Your Event

### Unit Test with MockEventProducer

```java title="UserServiceTest.java"
import io.github.closeup1202.curve.spring.test.MockEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private MockEventProducer mockEventProducer;

    @Test
    void shouldPublishEventWhenUserRegisters() {
        // Given
        UserRequest request = new UserRequest(
            "john_doe",
            "john@example.com",
            "John",
            "Doe"
        );

        // When
        User user = userService.registerUser(request);

        // Then
        assertThat(user.getId()).isNotNull();

        // Verify event was published
        var events = mockEventProducer.getPublishedEvents();
        assertThat(events).hasSize(1);

        var event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("USER_REGISTERED");
        assertThat(event.getSeverity()).isEqualTo(EventSeverity.INFO);
    }
}
```

### Integration Test with Kafka

```java title="UserServiceIntegrationTest.java"
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"event.audit.v1"})
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "curve.enabled=true"
})
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldPublishToKafkaWhenUserRegisters() throws Exception {
        // Setup consumer
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedEvent = new AtomicReference<>();

        // Consume from Kafka
        // ... (consumer setup)

        // When
        userService.registerUser(new UserRequest(
            "test_user", "test@example.com", "Test", "User"
        ));

        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedEvent.get()).contains("USER_REGISTERED");
    }
}
```

## Step 6: Verify in Kafka

Start your application and register a user:

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

Check the Kafka topic:

```bash
kafka-console-consumer --bootstrap-server localhost:9094 \
    --topic event.audit.v1 --from-beginning
```

**Published event:**

```json
{
  "eventId": "7355889748156289024",
  "eventType": "USER_REGISTERED",
  "occurredAt": "2025-02-03T10:30:00Z",
  "publishedAt": "2025-02-03T10:30:00.123Z",
  "severity": "INFO",
  "metadata": {
    "source": {
      "serviceName": "user-service",
      "serviceVersion": "1.0.0"
    },
    "trace": {
      "traceId": "abc123"
    }
  },
  "payload": {
    "userId": 1,
    "username": "john_doe",
    "email": "j***@ex***.com",  // ← Masked!
    "firstName": "5a4b3c...",    // ← Hashed!
    "lastName": "7f8e9d...",     // ← Hashed!
    "registeredAt": "2025-02-03T10:30:00Z"
  }
}
```

!!! success "Notice"
    PII fields are automatically protected based on `@PiiField` annotations!

## Next Steps

<div class="grid cards" markdown>

-   :material-shield-lock:{ .lg .middle } **PII Protection**

    ---

    Learn more about data masking strategies

    [:octicons-arrow-right-24: PII Guide](../features/pii-protection.md)

-   :material-database:{ .lg .middle } **Transactional Outbox**

    ---

    Guarantee atomicity between DB and events

    [:octicons-arrow-right-24: Outbox Pattern](../features/transactional-outbox.md)

-   :material-alert-circle:{ .lg .middle } **Failure Recovery**

    ---

    Handle failures with DLQ and backups

    [:octicons-arrow-right-24: Failure Recovery](../features/failure-recovery.md)

-   :material-chart-line:{ .lg .middle } **Monitoring**

    ---

    Set up metrics and health checks

    [:octicons-arrow-right-24: Observability](../features/observability.md)

</div>
