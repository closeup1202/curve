package com.project.curve.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.spring.context.EventContextProvider;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.spring.publisher.AbstractEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;

public class KafkaEventProducer extends AbstractEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaEventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String topic
    ) {
        super(envelopeFactory, eventContextProvider);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    protected <T extends DomainEventPayload> void send(EventEnvelope<T> envelope) {
        try {
            String value = objectMapper.writeValueAsString(envelope);
            String key = envelope.eventId().value();
            kafkaTemplate.send(topic, key, value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize EventEnvelope. eventId=" + envelope.eventId(), e);
        }
    }
}
