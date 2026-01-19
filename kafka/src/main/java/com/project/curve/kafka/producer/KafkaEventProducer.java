package com.project.curve.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.kafka.dlq.FailedEventRecord;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import com.project.curve.spring.publisher.AbstractEventPublisher;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 기반 이벤트 발행자
 * - 이벤트를 JSON으로 직렬화하여 Kafka 토픽에 발행
 * - RetryTemplate을 통한 재시도 지원
 * - 전송 실패 시 DLQ(Dead Letter Queue)로 동기 전송하여 이벤트 손실 방지
 * - 동기/비동기 전송 모드 지원
 */
@Slf4j
public class KafkaEventProducer extends AbstractEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String dlqTopic;
    private final boolean dlqEnabled;
    private final RetryTemplate retryTemplate;
    private final boolean asyncMode;
    private final long asyncTimeoutMs;
    private final long syncTimeoutSeconds;
    private final String dlqBackupPath;
    private final ExecutorService dlqExecutor;
    private final CurveMetricsCollector metricsCollector;

    @Builder
    public KafkaEventProducer(
            @NonNull EventEnvelopeFactory envelopeFactory,
            @NonNull EventContextProvider eventContextProvider,
            @NonNull KafkaTemplate<String, String> kafkaTemplate,
            @NonNull ObjectMapper objectMapper,
            @NonNull String topic,
            String dlqTopic,
            RetryTemplate retryTemplate,
            Boolean asyncMode,
            Long asyncTimeoutMs,
            Long syncTimeoutSeconds,
            String dlqBackupPath,
            ExecutorService dlqExecutor,
            CurveMetricsCollector metricsCollector
    ) {
        super(envelopeFactory, eventContextProvider);
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.dlqTopic = dlqTopic;
        this.dlqEnabled = dlqTopic != null && !dlqTopic.isBlank();
        this.retryTemplate = retryTemplate;
        this.asyncMode = asyncMode != null ? asyncMode : false;
        this.asyncTimeoutMs = asyncTimeoutMs != null ? asyncTimeoutMs : 5000L;
        this.syncTimeoutSeconds = syncTimeoutSeconds != null ? syncTimeoutSeconds : 30L;
        this.dlqBackupPath = dlqBackupPath != null ? dlqBackupPath : "./dlq-backup";
        this.dlqExecutor = dlqExecutor;
        this.metricsCollector = metricsCollector;

        log.info("KafkaEventProducer initialized: topic={}, asyncMode={}, syncTimeout={}s, asyncTimeout={}ms, dlq={}, retry={}, backupPath={}, dlqExecutor={}",
                this.topic, this.asyncMode, this.syncTimeoutSeconds, this.asyncTimeoutMs,
                this.dlqEnabled ? this.dlqTopic : "disabled",
                this.retryTemplate != null ? "enabled" : "disabled",
                this.dlqBackupPath,
                this.dlqExecutor != null ? "enabled" : "disabled");
    }

    @Override
    protected <T extends DomainEventPayload> void send(EventEnvelope<T> envelope) {
        String eventId = envelope.eventId().value();
        String eventType = envelope.eventType().fullName();
        long startTime = System.currentTimeMillis();
        String value;

        try {
            value = serializeToJson(envelope);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize EventEnvelope: eventId={}", eventId, e);
            if (metricsCollector != null) {
                metricsCollector.recordEventPublished(eventType, false, System.currentTimeMillis() - startTime);
                metricsCollector.recordKafkaError("SerializationException");
            }
            throw new EventSerializationException("Failed to serialize EventEnvelope. eventId=" + eventId, e);
        }

        log.debug("Sending event to Kafka: eventId={}, topic={}, mode={}", eventId, topic, asyncMode ? "async" : "sync");

        try {
            if (asyncMode) {
                sendAsync(eventId, eventType, value, startTime);
            } else {
                if (retryTemplate != null) {
                    sendWithRetry(eventId, eventType, value, startTime);
                } else {
                    sendWithoutRetry(eventId, eventType, value, startTime);
                }
            }
        } catch (Exception e) {
            if (metricsCollector != null) {
                metricsCollector.recordEventPublished(eventType, false, System.currentTimeMillis() - startTime);
            }
            throw e;
        }
    }

    private <T extends DomainEventPayload> String serializeToJson(EventEnvelope<T> envelope)
            throws JsonProcessingException {
        return objectMapper.writeValueAsString(envelope);
    }

    private void sendWithRetry(String eventId, String eventType, String value, long startTime) {
        try {
            retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.warn("Retrying event send: eventId={}, attempt={}", eventId, context.getRetryCount() + 1);
                    if (metricsCollector != null) {
                        metricsCollector.recordRetry(eventType, context.getRetryCount(), "in_progress");
                    }
                }
                return doSendSync(eventId, eventType, value, startTime);
            });
        } catch (Exception e) {
            log.error("All retry attempts exhausted for event: eventId={}", eventId, e);
            if (metricsCollector != null) {
                metricsCollector.recordRetry(eventType, 3, "failure");
            }
            handleSendFailure(eventId, eventType, value, e);
        }
    }

    private void sendWithoutRetry(String eventId, String eventType, String value, long startTime) {
        try {
            doSendSync(eventId, eventType, value, startTime);
        } catch (Exception e) {
            log.error("Failed to send event to Kafka: eventId={}, topic={}", eventId, topic, e);
            if (metricsCollector != null) {
                metricsCollector.recordKafkaError(e.getClass().getSimpleName());
            }
            handleSendFailure(eventId, eventType, value, e);
        }
    }

    /**
     * 비동기 전송 - CompletableFuture 기반
     * 전송 성공/실패를 콜백으로 처리하며, 메인 스레드를 블로킹하지 않음
     */
    private void sendAsync(String eventId, String eventType, String value, long startTime) {
        kafkaTemplate.send(topic, eventId, value)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Async send failed: eventId={}, topic={}", eventId, topic, ex);
                        if (metricsCollector != null) {
                            metricsCollector.recordEventPublished(eventType, false, System.currentTimeMillis() - startTime);
                            metricsCollector.recordKafkaError(ex.getClass().getSimpleName());
                        }
                        handleSendFailure(eventId, eventType, value, ex);
                    } else {
                        if (metricsCollector != null) {
                            metricsCollector.recordEventPublished(eventType, true, System.currentTimeMillis() - startTime);
                        }
                        handleSendSuccess(eventId, result);
                    }
                })
                .orTimeout(asyncTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.error("Async send timeout: eventId={}, topic={}, timeout={}ms",
                            eventId, topic, asyncTimeoutMs, ex);
                    if (metricsCollector != null) {
                        metricsCollector.recordEventPublished(eventType, false, System.currentTimeMillis() - startTime);
                        metricsCollector.recordKafkaError("TimeoutException");
                    }
                    handleSendFailure(eventId, eventType, value, ex);
                    return null;
                });

        log.debug("Event sent asynchronously (non-blocking): eventId={}, topic={}", eventId, topic);
    }

    private SendResult<String, String> doSendSync(String eventId, String eventType, String value, long startTime) throws Exception {
        SendResult<String, String> result = kafkaTemplate
                .send(topic, eventId, value)
                .get(syncTimeoutSeconds, TimeUnit.SECONDS);

        if (metricsCollector != null) {
            metricsCollector.recordEventPublished(eventType, true, System.currentTimeMillis() - startTime);
        }
        handleSendSuccess(eventId, result);
        return result;
    }

    private void handleSendSuccess(String eventId, SendResult<String, String> result) {
        var metadata = result.getRecordMetadata();
        log.debug("Event sent successfully: eventId={}, topic={}, partition={}, offset={}",
                eventId, metadata.topic(), metadata.partition(), metadata.offset());
    }

    private void handleSendFailure(String eventId, String eventType, String originalValue, Throwable ex) {
        if (dlqEnabled) {
            if (metricsCollector != null) {
                metricsCollector.recordDlqEvent(eventType, ex.getClass().getSimpleName());
            }
            if (dlqExecutor != null) {  // ExecutorService가 있으면 비동기로, 없으면 동기로 DLQ 전송
                sendToDlqAsync(eventId, originalValue, ex);
            } else {
                sendToDlqSync(eventId, originalValue, ex);
            }
        } else {
            log.warn("DLQ not configured. Event may be lost: eventId={}", eventId);
        }
    }

    /**
     * DLQ로 비동기 전송 - 별도 ExecutorService를 사용하여 콜백 스레드 블로킹 방지
     */
    private void sendToDlqAsync(String eventId, String originalValue, Throwable originalException) {
        dlqExecutor.submit(() -> {
            try {
                log.warn("Sending failed event to DLQ (async): eventId={}, dlqTopic={}", eventId, dlqTopic);

                FailedEventRecord failedRecord = new FailedEventRecord(
                        eventId,
                        topic,
                        originalValue,
                        originalException.getClass().getName(),
                        originalException.getMessage(),
                        System.currentTimeMillis()
                );

                String dlqValue = objectMapper.writeValueAsString(failedRecord);

                // 비동기 전송 (ExecutorService 스레드에서 동기 대기)
                SendResult<String, String> result = kafkaTemplate
                        .send(dlqTopic, eventId, dlqValue)
                        .get(syncTimeoutSeconds, TimeUnit.SECONDS);

                log.info("Event sent to DLQ successfully (async): eventId={}, dlqTopic={}, partition={}, offset={}",
                        eventId, dlqTopic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());

            } catch (Exception e) {
                log.error("Failed to send event to DLQ (async): eventId={}, dlqTopic={}", eventId, dlqTopic, e);
                backupToLocalFile(eventId, originalValue);
            }
        });
    }

    /**
     * DLQ로 동기 전송 - 이벤트 손실 방지를 위해 동기 방식으로 전송
     */
    private void sendToDlqSync(String eventId, String originalValue, Throwable originalException) {
        try {
            log.warn("Sending failed event to DLQ (sync): eventId={}, dlqTopic={}", eventId, dlqTopic);

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
                    .get(syncTimeoutSeconds, TimeUnit.SECONDS);

            log.info("Event sent to DLQ successfully (sync): eventId={}, dlqTopic={}, partition={}, offset={}",
                    eventId, dlqTopic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("Failed to send event to DLQ (sync): eventId={}, dlqTopic={}", eventId, dlqTopic, e);
            backupToLocalFile(eventId, originalValue);
        }
    }

    /**
     * DLQ 전송도 실패한 경우 로컬 파일에 백업
     * - POSIX 파일 권한 설정 (rw-------)으로 보안 강화
     * - 민감 데이터 노출 방지를 위해 페이로드 정보는 로깅하지 않음
     */
    private void backupToLocalFile(String eventId, String originalValue) {
        try {
            Path backupDir = Paths.get(dlqBackupPath);
            Files.createDirectories(backupDir);

            Path backupFile = backupDir.resolve(eventId + ".json");

            // POSIX 파일 시스템인 경우 파일 권한 설정 (rw-------)
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.writeString(backupFile, originalValue, StandardOpenOption.CREATE);
                Files.setPosixFilePermissions(backupFile, perms);
                log.error("Event backed up to file with restricted permissions: eventId={}, file={}", eventId, backupFile);
            } catch (UnsupportedOperationException e) {
                // Windows 등 POSIX를 지원하지 않는 시스템
                Files.writeString(backupFile, originalValue, StandardOpenOption.CREATE);
                log.error("Event backed up to file (POSIX not supported): eventId={}, file={}", eventId, backupFile);
                log.warn("File permissions not set - consider manual security configuration on non-POSIX systems");
            }
        } catch (IOException e) {
            log.error("Failed to backup event to file: eventId={}", eventId, e);
            log.error("CRITICAL: Event permanently lost - eventId={}", eventId);
        }
    }
}
