---
title: Custom Implementation Guide
description: Learn how to extend Curve with custom event producers, context providers, and serializers.
keywords: curve extensibility, custom implementation, event producer, context provider
---

# Custom Implementation

Curve's hexagonal architecture makes it easy to extend and customize.

!!! warning "Breaking Change in v0.2.0"
    The `EventProducer` interface added two new methods for multi-topic support:
    - `publish(T payload, String topic)`
    - `publish(T payload, EventSeverity severity, String topic)`

    If you have a custom `EventProducer` implementation, you must add implementations for these methods. When `topic` is empty or null, use the default topic from configuration.

## Custom Event Producer

Implement the `EventProducer` interface to support non-Kafka brokers.

### EventProducer Interface

The `EventProducer` interface defines the contract for publishing domain events:

```java
public interface EventProducer {
    // Existing methods (v0.1.x and earlier)
    <T extends DomainEventPayload> void publish(T payload);
    <T extends DomainEventPayload> void publish(T payload, EventSeverity severity);

    // New methods (v0.2.0+) for multi-topic publishing
    <T extends DomainEventPayload> void publish(T payload, String topic);
    <T extends DomainEventPayload> void publish(T payload, EventSeverity severity, String topic);
}
```

**Topic Resolution Logic:**
- If `topic` parameter is provided and non-empty → publish to specified topic
- If `topic` is empty or null → use default topic from `curve.kafka.topic` configuration

### Example: RabbitMQ Producer

```java
import io.github.closeup1202.curve.core.port.EventProducer;
import io.github.closeup1202.curve.core.envelope.EventEnvelope;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqEventProducer implements EventProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String defaultTopic;  // Injected from curve.kafka.topic

    public RabbitMqEventProducer(
        RabbitTemplate rabbitTemplate,
        ObjectMapper objectMapper,
        @Value("${curve.kafka.topic}") String defaultTopic
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.defaultTopic = defaultTopic;
    }

    @Override
    public <T extends DomainEventPayload> void publish(T payload) {
        publish(payload, EventSeverity.INFO);
    }

    @Override
    public <T extends DomainEventPayload> void publish(T payload, EventSeverity severity) {
        publish(payload, severity, null);  // Use default topic
    }

    @Override
    public <T extends DomainEventPayload> void publish(T payload, String topic) {
        publish(payload, EventSeverity.INFO, topic);
    }

    @Override
    public <T extends DomainEventPayload> void publish(T payload, EventSeverity severity, String topic) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String resolvedTopic = (topic != null && !topic.isEmpty()) ? topic : defaultTopic;

            // Send to RabbitMQ exchange with routing key = topic name
            rabbitTemplate.convertAndSend(
                "events.exchange",
                resolvedTopic,
                json
            );
        } catch (Exception e) {
            throw new EventPublishException("Failed to publish to RabbitMQ", e);
        }
    }
}
```

---

## Custom Context Provider

Add custom metadata to events.

### Example: Custom Tag Provider

```java
import io.github.closeup1202.curve.core.context.ContextProvider;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagContextProvider implements ContextProvider {

    private final FeatureFlagService featureFlagService;

    @Override
    public Map<String, String> provide() {
        return Map.of(
            "experiment_id", featureFlagService.getCurrentExperiment(),
            "feature_flags", featureFlagService.getActiveFlags()
        );
    }
}
```

Context providers are automatically discovered and added to event metadata.

---

## Custom Serializer

Implement custom serialization logic.

### Example: Protobuf Serializer

```java
import io.github.closeup1202.curve.core.serde.EventSerializer;
import com.google.protobuf.Message;

@Component
public class ProtobufEventSerializer implements EventSerializer {

    @Override
    public byte[] serialize(EventEnvelope<?> envelope) {
        EventProto.Event proto = EventProto.Event.newBuilder()
            .setEventId(envelope.getEventId())
            .setEventType(envelope.getEventType())
            .setPayload(serializePayload(envelope.getPayload()))
            .build();

        return proto.toByteArray();
    }

    private ByteString serializePayload(DomainEventPayload payload) {
        // Custom protobuf serialization
        return ByteString.copyFrom(/* ... */);
    }
}
```

---

## Custom PII Strategy

Implement custom PII protection logic.

### Example: Tokenization Strategy

```java
import io.github.closeup1202.curve.spring.pii.PiiProcessor;

@Component
public class TokenizationPiiProcessor implements PiiProcessor {

    private final TokenVault tokenVault;

    @Override
    public String process(String value, PiiType type, PiiStrategy strategy) {
        if (strategy == PiiStrategy.TOKENIZE) {
            return tokenVault.tokenize(value);
        }
        // Delegate to default processor
        return defaultProcessor.process(value, type, strategy);
    }
}
```

---

## Complete Example: AWS SNS Producer

```java
@Component
@ConditionalOnProperty(name = "curve.producer.type", havingValue = "sns")
public class SnsEventProducer extends AbstractEventPublisher {

    private final AmazonSNS snsClient;
    private final ObjectMapper objectMapper;

    @Value("${curve.sns.topic-arn}")
    private String topicArn;

    public SnsEventProducer(AmazonSNS snsClient, ObjectMapper objectMapper) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected <T extends DomainEventPayload> void send(EventEnvelope<T> envelope) {
        try {
            String message = objectMapper.writeValueAsString(envelope);

            PublishRequest request = new PublishRequest()
                .withTopicArn(topicArn)
                .withMessage(message)
                .withMessageAttributes(buildAttributes(envelope));

            snsClient.publish(request);

        } catch (Exception e) {
            throw new EventPublishException("Failed to publish to SNS", e);
        }
    }

    private Map<String, MessageAttributeValue> buildAttributes(EventEnvelope<?> envelope) {
        return Map.of(
            "eventType", new MessageAttributeValue()
                .withDataType("String")
                .withStringValue(envelope.getEventType()),
            "severity", new MessageAttributeValue()
                .withDataType("String")
                .withStringValue(envelope.getSeverity().name())
        );
    }
}
```

---

## See Also

- [Architecture Overview](../features/overview.md#architecture)
- [API Reference](annotations.md)
