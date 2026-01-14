package com.project.curve.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.kafka.dlq.FailedEventRecord;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.spring.publisher.AbstractEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka 기반 이벤트 발행자
 * <p>
 * - 이벤트를 JSON으로 직렬화하여 Kafka 토픽에 발행
 * - 비동기 전송 결과 처리 (성공/실패 로깅)
 * - 전송 실패 시 DLQ(Dead Letter Queue)로 전송하여 이벤트 손실 방지
 */
@Slf4j
public class KafkaEventProducer extends AbstractEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String dlqTopic;
    private final boolean dlqEnabled;

    public KafkaEventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String topic
    ) {
        this(envelopeFactory, eventContextProvider, kafkaTemplate, objectMapper, topic, null);
    }

    public KafkaEventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String topic,
            String dlqTopic
    ) {
        super(envelopeFactory, eventContextProvider);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.dlqTopic = dlqTopic;
        this.dlqEnabled = dlqTopic != null && !dlqTopic.isBlank();

        if (dlqEnabled) {
            log.info("DLQ enabled for topic: {} -> DLQ: {}", topic, dlqTopic);
        }
    }

    @Override
    protected <T extends DomainEventPayload> void send(EventEnvelope<T> envelope) {
        String eventId = envelope.eventId().value();

        try {
            String value = serializeToJson(envelope);

            log.debug("Sending event to Kafka: eventId={}, topic={}", eventId, topic);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, eventId, value);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    handleSendFailure(eventId, ex);
                } else {
                    handleSendSuccess(eventId, result);
                }
            });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize EventEnvelope: eventId={}", eventId, e);
            throw new EventSerializationException("Failed to serialize EventEnvelope. eventId=" + eventId, e);
        }
    }

    private <T extends DomainEventPayload> String serializeToJson(EventEnvelope<T> envelope)
            throws JsonProcessingException {
        return objectMapper.writeValueAsString(envelope);
    }

    private void handleSendSuccess(String eventId, SendResult<String, String> result) {
        var metadata = result.getRecordMetadata();
        log.info("Event sent successfully: eventId={}, topic={}, partition={}, offset={}",
                eventId, metadata.topic(), metadata.partition(), metadata.offset());
    }

    private void handleSendFailure(String eventId, Throwable ex) {
        log.error("Failed to send event to Kafka: eventId={}, topic={}", eventId, topic, ex);

        if (dlqEnabled) {
            sendToDlq(eventId, ex);
        } else {
            log.warn("DLQ not configured. Event may be lost: eventId={}", eventId);
        }
    }

    private void sendToDlq(String eventId, Throwable originalException) {
        try {
            log.warn("Sending failed event to DLQ: eventId={}, dlqTopic={}", eventId, dlqTopic);

            // DLQ 메시지에 원본 이벤트 정보와 실패 원인을 포함
            FailedEventRecord failedRecord = new FailedEventRecord(
                    eventId,
                    topic,
                    originalException.getClass().getName(),
                    originalException.getMessage(),
                    System.currentTimeMillis()
            );

            String dlqValue = objectMapper.writeValueAsString(failedRecord);

            CompletableFuture<SendResult<String, String>> dlqFuture = kafkaTemplate.send(dlqTopic, eventId, dlqValue);

            dlqFuture.whenComplete((result, dlqEx) -> {
                if (dlqEx != null) {
                    log.error("CRITICAL: Failed to send event to DLQ. Event is lost: eventId={}, dlqTopic={}",
                            eventId, dlqTopic, dlqEx);
                } else {
                    log.info("Event sent to DLQ successfully: eventId={}, dlqTopic={}", eventId, dlqTopic);
                }
            });

        } catch (Exception e) {
            log.error("CRITICAL: Failed to serialize DLQ message. Event is lost: eventId={}", eventId, e);
        }
    }
}
