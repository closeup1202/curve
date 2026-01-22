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
 * Kafka 기반 이벤트 발행자.
 * <p>
 * 이벤트를 JSON으로 직렬화하여 Kafka 토픽에 발행합니다.
 *
 * <h2>주요 기능</h2>
 * <ul>
 *   <li>RetryTemplate을 통한 재시도 지원</li>
 *   <li>전송 실패 시 DLQ(Dead Letter Queue)로 전송하여 이벤트 손실 방지</li>
 *   <li>DLQ 전송도 실패 시 로컬 파일 백업 (최후의 안전망)</li>
 *   <li>동기/비동기 전송 모드 지원</li>
 * </ul>
 *
 * <h2>PII(개인식별정보) 처리</h2>
 * <p>
 * 이벤트 직렬화 시 {@link com.project.curve.spring.pii.jackson.PiiModule}이 ObjectMapper에
 * 등록되어 있으면, {@code @PiiField} 어노테이션이 붙은 필드는 자동으로 마스킹/암호화됩니다.
 * <p>
 * <b>보안 주의사항:</b>
 * <ul>
 *   <li>{@code curve.pii.enabled=true} (기본값)로 설정해야 PII 마스킹이 적용됩니다.</li>
 *   <li>DLQ 및 로컬 백업 파일에 저장되는 데이터도 마스킹된 상태입니다.</li>
 *   <li>로컬 백업 파일은 POSIX 시스템에서 600 권한(rw-------)으로 생성됩니다.</li>
 * </ul>
 *
 * @see com.project.curve.spring.pii.annotation.PiiField
 * @see com.project.curve.spring.pii.jackson.PiiModule
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

        log.debug("KafkaEventProducer initialized: topic={}, asyncMode={}, syncTimeout={}s, asyncTimeout={}ms, dlq={}, retry={}, backupPath={}, dlqExecutor={}",
                this.topic, this.asyncMode, this.syncTimeoutSeconds, this.asyncTimeoutMs,
                this.dlqEnabled ? this.dlqTopic : "disabled",
                this.retryTemplate != null ? "enabled" : "disabled",
                this.dlqBackupPath,
                this.dlqExecutor != null ? "enabled" : "disabled");
    }

    @Override
    protected <T extends DomainEventPayload> void send(EventEnvelope<T> envelope) {
        String eventId = envelope.eventId().value();
        String eventType = envelope.eventType().getValue();
        long startTime = System.currentTimeMillis();

        try {
            String value = serializeToJson(envelope);
            doSend(eventId, eventType, value, startTime);
        } catch (JsonProcessingException e) {
            handleSerializationError(eventId, eventType, startTime, e);
        } catch (Exception e) {
            handleSendError(eventType, startTime, e);
        }
    }

    private void doSend(String eventId, String eventType, String value, long startTime) {
        log.debug("Sending event to Kafka: eventId={}, topic={}, mode={}", eventId, topic, asyncMode ? "async" : "sync");

        if (asyncMode) {
            sendAsync(eventId, eventType, value, startTime);
        } else {
            sendSync(eventId, eventType, value, startTime);
        }
    }

    private void sendSync(String eventId, String eventType, String value, long startTime) {
        if (retryTemplate != null) {
            sendWithRetry(eventId, eventType, value, startTime);
        } else {
            sendWithoutRetry(eventId, eventType, value, startTime);
        }
    }

    private void handleSerializationError(String eventId, String eventType, long startTime, JsonProcessingException e) {
        log.error("Failed to serialize EventEnvelope: eventId={}", eventId, e);
        recordErrorMetrics(eventType, startTime, "SerializationException");
        throw new EventSerializationException("Failed to serialize EventEnvelope. eventId=" + eventId, e);
    }

    private void handleSendError(String eventType, long startTime, Exception e) {
        recordErrorMetrics(eventType, startTime, e.getClass().getSimpleName());
    }

    private void recordErrorMetrics(String eventType, long startTime, String errorType) {
        if (metricsCollector != null) {
            metricsCollector.recordEventPublished(eventType, false, System.currentTimeMillis() - startTime);
            metricsCollector.recordKafkaError(errorType);
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
            dispatchToDlq(eventId, originalValue, ex);
        } else {
            log.warn("DLQ not configured. Event may be lost: eventId={}", eventId);
        }
    }

    /**
     * DLQ 전송 디스패치 - ExecutorService 존재 여부에 따라 비동기/동기 전송 결정
     */
    private void dispatchToDlq(String eventId, String originalValue, Throwable originalException) {
        if (dlqExecutor != null) {
            // 비동기 전송 - 별도 ExecutorService를 사용하여 콜백 스레드 블로킹 방지
            dlqExecutor.submit(() -> executeDlqSend(eventId, originalValue, originalException));
        } else {
            // 동기 전송 - 이벤트 손실 방지를 위해 즉시 전송
            executeDlqSend(eventId, originalValue, originalException);
        }
    }

    /**
     * DLQ 전송 실행 - 실제 Kafka DLQ 전송 로직
     */
    private void executeDlqSend(String eventId, String originalValue, Throwable originalException) {
        String mode = dlqExecutor != null ? "async" : "sync";
        try {
            log.warn("Sending failed event to DLQ ({}): eventId={}, dlqTopic={}", mode, eventId, dlqTopic);

            String dlqValue = createDlqPayload(eventId, originalValue, originalException);

            SendResult<String, String> result = kafkaTemplate
                    .send(dlqTopic, eventId, dlqValue)
                    .get(syncTimeoutSeconds, TimeUnit.SECONDS);

            log.info("Event sent to DLQ successfully ({}): eventId={}, dlqTopic={}, partition={}, offset={}",
                    mode, eventId, dlqTopic,
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("Failed to send event to DLQ ({}): eventId={}, dlqTopic={}", mode, eventId, dlqTopic, e);
            backupToLocalFile(eventId, originalValue);
        }
    }

    /**
     * DLQ 페이로드 생성 - FailedEventRecord를 JSON으로 직렬화
     */
    private String createDlqPayload(String eventId, String originalValue, Throwable originalException)
            throws JsonProcessingException {
        FailedEventRecord failedRecord = new FailedEventRecord(
                eventId,
                topic,
                originalValue,
                originalException.getClass().getName(),
                originalException.getMessage(),
                System.currentTimeMillis()
        );
        return objectMapper.writeValueAsString(failedRecord);
    }

    /**
     * DLQ 전송도 실패한 경우 로컬 파일에 백업.
     * <p>
     * 이벤트 손실을 방지하기 위한 최후의 안전망입니다.
     *
     * <h3>보안 고려사항</h3>
     * <ul>
     *   <li>POSIX 파일 시스템: 600 권한(rw-------)으로 생성</li>
     *   <li>Windows: 수동 권한 설정 필요 (경고 로그 출력)</li>
     *   <li>저장되는 데이터는 PiiModule이 적용된 경우 마스킹된 상태</li>
     *   <li>페이로드 내용은 로그에 출력하지 않음</li>
     * </ul>
     *
     * <h3>복구 방법</h3>
     * <p>
     * 백업 파일은 JSON 형식이며, Kafka가 복구되면 수동으로 재전송할 수 있습니다.
     * <pre>
     * # 백업 파일 확인
     * ls -la ./dlq-backup/
     *
     * # 재전송 (예시)
     * cat ./dlq-backup/{eventId}.json | kafka-console-producer --topic event.audit.v1
     * </pre>
     *
     * @param eventId       이벤트 ID
     * @param originalValue 직렬화된 이벤트 페이로드 (PII 마스킹 적용된 상태)
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
                log.warn("Consider manual security configuration on non-POSIX systems like windows : {}", backupFile);
            }
        } catch (IOException e) {
            log.error("Failed to backup event to file: eventId={}", eventId, e);
            log.error("Event permanently lost. eventId={}, cause={}", eventId, e.getMessage());
        }
    }
}
