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
import org.springframework.retry.support.RetryTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Kafka 기반 이벤트 발행자
 * <p>
 * - 이벤트를 JSON으로 직렬화하여 Kafka 토픽에 발행
 * - RetryTemplate을 통한 재시도 지원
 * - 전송 실패 시 DLQ(Dead Letter Queue)로 동기 전송하여 이벤트 손실 방지
 */
@Slf4j
public class KafkaEventProducer extends AbstractEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 30;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String dlqTopic;
    private final boolean dlqEnabled;
    private final RetryTemplate retryTemplate;

    public KafkaEventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String topic
    ) {
        this(envelopeFactory, eventContextProvider, kafkaTemplate, objectMapper, topic, null, null);
    }

    public KafkaEventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String topic,
            String dlqTopic
    ) {
        this(envelopeFactory, eventContextProvider, kafkaTemplate, objectMapper, topic, dlqTopic, null);
    }

    public KafkaEventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String topic,
            String dlqTopic,
            RetryTemplate retryTemplate
    ) {
        super(envelopeFactory, eventContextProvider);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.dlqTopic = dlqTopic;
        this.dlqEnabled = dlqTopic != null && !dlqTopic.isBlank();
        this.retryTemplate = retryTemplate;

        if (dlqEnabled) {
            log.info("DLQ enabled for topic: {} -> DLQ: {}", topic, dlqTopic);
        }
        if (retryTemplate != null) {
            log.info("Retry enabled for KafkaEventProducer");
        }
    }

    @Override
    protected <T extends DomainEventPayload> void send(EventEnvelope<T> envelope) {
        String eventId = envelope.eventId().value();
        String value;

        try {
            value = serializeToJson(envelope);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize EventEnvelope: eventId={}", eventId, e);
            throw new EventSerializationException("Failed to serialize EventEnvelope. eventId=" + eventId, e);
        }

        log.debug("Sending event to Kafka: eventId={}, topic={}", eventId, topic);

        if (retryTemplate != null) {
            sendWithRetry(eventId, value);
        } else {
            sendWithoutRetry(eventId, value);
        }
    }

    private void sendWithRetry(String eventId, String value) {
        try {
            retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.warn("Retrying event send: eventId={}, attempt={}", eventId, context.getRetryCount() + 1);
                }
                return doSendSync(eventId, value);
            });
        } catch (Exception e) {
            log.error("All retry attempts exhausted for event: eventId={}", eventId, e);
            handleSendFailure(eventId, value, e);
        }
    }

    private void sendWithoutRetry(String eventId, String value) {
        try {
            doSendSync(eventId, value);
        } catch (Exception e) {
            log.error("Failed to send event to Kafka: eventId={}, topic={}", eventId, topic, e);
            handleSendFailure(eventId, value, e);
        }
    }

    private SendResult<String, String> doSendSync(String eventId, String value) throws Exception {
        SendResult<String, String> result = kafkaTemplate
                .send(topic, eventId, value)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        handleSendSuccess(eventId, result);
        return result;
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

    private void handleSendFailure(String eventId, String originalValue, Throwable ex) {
        if (dlqEnabled) {
            sendToDlqSync(eventId, originalValue, ex);
        } else {
            log.warn("DLQ not configured. Event may be lost: eventId={}", eventId);
        }
    }

    /**
     * DLQ로 동기 전송 - 이벤트 손실 방지를 위해 동기 방식으로 전송
     */
    private void sendToDlqSync(String eventId, String originalValue, Throwable originalException) {
        try {
            log.warn("Sending failed event to DLQ: eventId={}, dlqTopic={}", eventId, dlqTopic);

            FailedEventRecord failedRecord = new FailedEventRecord(
                    eventId,
                    topic,
                    originalValue,
                    originalException.getClass().getName(),
                    originalException.getMessage(),
                    System.currentTimeMillis()
            );

            String dlqValue = objectMapper.writeValueAsString(failedRecord);

            // 동기 전송 - DLQ는 반드시 성공해야 함
            SendResult<String, String> result = kafkaTemplate
                    .send(dlqTopic, eventId, dlqValue)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Event sent to DLQ successfully: eventId={}, dlqTopic={}, partition={}, offset={}",
                    eventId, dlqTopic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send event to DLQ. Event is lost: eventId={}, dlqTopic={}",
                    eventId, dlqTopic, e);
            // DLQ 전송 실패 시 로컬 백업
            backupToLocalFile(eventId, originalValue, e);
        }
    }

    /**
     * DLQ 전송도 실패한 경우 로컬 파일에 백업
     */
    private void backupToLocalFile(String eventId, String originalValue, Exception exception) {
        // 로그에 전체 이벤트 데이터 기록 (운영 환경에서는 파일 또는 DB 백업 권장)
        log.error("BACKUP: eventId={}, payload={}, exception={}",
                eventId, originalValue, exception.getMessage());
    }
}
