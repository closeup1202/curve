package com.project.curve.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.spring.publisher.AbstractEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka 기반 이벤트 발행자
 *
 * - 이벤트를 JSON으로 직렬화하여 Kafka 토픽에 발행
 * - 비동기 전송 결과 처리 (성공/실패 로깅)
 * - 전송 실패 시 예외 발생
 */
@Slf4j
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
        String eventId = envelope.eventId().value();

        try {
            String value = serializeToJson(envelope);
            String key = eventId;

            log.debug("Sending event to Kafka: eventId={}, topic={}", eventId, topic);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, value);

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
        // 비동기 전송이므로 여기서 예외를 던져도 호출자에게 전달되지 않음
        // 추후 DLQ(Dead Letter Queue) 전송, 알림, 재시도 등의 처리 추가 가능
    }

    /**
     * 이벤트 직렬화 실패 예외
     */
    public static class EventSerializationException extends RuntimeException {
        public EventSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
