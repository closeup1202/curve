---
title: Custom Implementation Guide
description: Learn how to extend Curve with custom event producers, context providers, and serializers.
keywords: curve extensibility, custom implementation, event producer, context provider
---

# Custom Implementation

Curve's hexagonal architecture makes it easy to extend and customize.

## Custom Event Producer

Implement the `EventProducer` interface to support non-Kafka brokers.

### Example: RabbitMQ Producer

```java
import io.github.closeup1202.curve.core.port.EventProducer;
import io.github.closeup1202.curve.core.envelope.EventEnvelope;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqEventProducer extends AbstractEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public RabbitMqEventProducer(
        RabbitTemplate rabbitTemplate,
        ObjectMapper objectMapper
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected <T extends DomainEventPayload> void send(EventEnvelope<T> envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            rabbitTemplate.convertAndSend(
                "events.exchange",
                envelope.getEventType(),
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
