package com.project.curve.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.serde.EventSerializer;
import com.project.curve.kafka.dlq.FailedEventRecord;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import com.project.curve.spring.publisher.AbstractEventPublisher;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 기반 이벤트 발행자.
 * <p>
 * 이벤트를 직렬화하여 Kafka 토픽으로 발행합니다.
 *
 * <h2>주요 기능</h2>
 * <ul>
 *   <li>RetryTemplate을 통한 재시도 지원</li>
 *   <li>전송 실패 시 DLQ(Dead Letter Queue)로 전송하여 이벤트 유실 방지</li>
 *   <li>DLQ 전송도 실패할 경우 최후의 수단으로 로컬 파일 백업</li>
 *   <li>동기 및 비동기 전송 모드 지원</li>
 * </ul>
 *
 * <h2>PII (개인식별정보) 처리</h2>
 * <p>
 * 이벤트 직렬화 시 {@link com.project.curve.spring.pii.jackson.PiiModule}이 ObjectMapper에 등록되어 있다면,
 * {@code @PiiField}가 붙은 필드는 자동으로 마스킹/암호화됩니다.
 * <p>
 * <b>보안 참고사항:</b>
 * <ul>
 *   <li>PII 마스킹은 {@code curve.pii.enabled=true} (기본값)일 때만 적용됩니다.</li>
 *   <li>DLQ 및 로컬 백업 파일에 저장되는 데이터도 마스킹된 상태로 저장됩니다.</li>
 *   <li>로컬 백업 파일은 POSIX 시스템에서 600 권한(rw-------)으로 생성됩니다.</li>
 * </ul>
 *
 * @see com.project.curve.spring.pii.annotation.PiiField
 * @see com.project.curve.spring.pii.jackson.PiiModule
 */
@Slf4j
public class KafkaEventProducer extends AbstractEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventSerializer eventSerializer;
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
    private final boolean isProduction;

    @Builder
    public KafkaEventProducer(
            @NonNull EventEnvelopeFactory envelopeFactory,
            @NonNull EventContextProvider eventContextProvider,
            @NonNull KafkaTemplate<String, Object> kafkaTemplate,
            @NonNull EventSerializer eventSerializer,
            @NonNull ObjectMapper objectMapper,
            @NonNull String topic,
            String dlqTopic,
            RetryTemplate retryTemplate,
            Boolean asyncMode,
            Long asyncTimeoutMs,
            Long syncTimeoutSeconds,
            String dlqBackupPath,
            ExecutorService dlqExecutor,
            @NonNull CurveMetricsCollector metricsCollector,
            Boolean isProduction
    ) {
        super(envelopeFactory, eventContextProvider);
        this.kafkaTemplate = kafkaTemplate;
        this.eventSerializer = eventSerializer;
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
        this.isProduction = isProduction != null ? isProduction : false;

        log.debug("KafkaEventProducer initialized: topic={}, asyncMode={}, syncTimeout={}s, asyncTimeout={}ms, dlq={}, retry={}, backupPath={}, dlqExecutor={}, isProduction={}",
                this.topic, this.asyncMode, this.syncTimeoutSeconds, this.asyncTimeoutMs,
                this.dlqEnabled ? this.dlqTopic : "disabled",
                this.retryTemplate != null ? "enabled" : "disabled",
                this.dlqBackupPath,
                this.dlqExecutor != null ? "enabled" : "disabled",
                this.isProduction);
    }

    @Override
    protected <T extends DomainEventPayload> void send(EventEnvelope<T> envelope) {
        String eventId = envelope.eventId().value();
        String eventType = envelope.eventType().getValue();
        long startTime = System.currentTimeMillis();

        try {
            Object value = eventSerializer.serialize(envelope);
            doSend(eventId, eventType, value, startTime);
        } catch (EventSerializationException e) {
            handleSerializationError(eventId, eventType, startTime, e);
        } catch (Exception e) {
            handleSendError(eventType, startTime, e);
        }
    }

    private void doSend(String eventId, String eventType, Object value, long startTime) {
        log.debug("Sending event to Kafka: eventId={}, topic={}, mode={}", eventId, topic, asyncMode ? "async" : "sync");

        if (asyncMode) {
            sendAsync(eventId, eventType, value, startTime);
        } else {
            sendSync(eventId, eventType, value, startTime);
        }
    }

    private void sendSync(String eventId, String eventType, Object value, long startTime) {
        if (retryTemplate != null) {
            sendWithRetry(eventId, eventType, value, startTime);
        } else {
            sendWithoutRetry(eventId, eventType, value, startTime);
        }
    }

    private void handleSerializationError(String eventId, String eventType, long startTime, EventSerializationException e) {
        log.error("Failed to serialize EventEnvelope: eventId={}", eventId, e);
        recordErrorMetrics(eventType, startTime, "SerializationException");
        throw e;
    }

    private void handleSendError(String eventType, long startTime, Exception e) {
        recordErrorMetrics(eventType, startTime, e.getClass().getSimpleName());
    }

    private void recordErrorMetrics(String eventType, long startTime, String errorType) {
        metricsCollector.recordEventPublished(eventType, false, System.currentTimeMillis() - startTime);
        metricsCollector.recordKafkaError(errorType);
    }

    private void sendWithRetry(String eventId, String eventType, Object value, long startTime) {
        try {
            retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.warn("Retrying event send: eventId={}, attempt={}", eventId, context.getRetryCount() + 1);
                    metricsCollector.recordRetry(eventType, context.getRetryCount(), "in_progress");
                }
                return doSendSync(eventId, eventType, value, startTime);
            });
        } catch (Exception e) {
            log.error("All retry attempts exhausted for event: eventId={}", eventId, e);
            metricsCollector.recordRetry(eventType, 3, "failure");
            handleSendFailure(eventId, eventType, value, e);
        }
    }

    private void sendWithoutRetry(String eventId, String eventType, Object value, long startTime) {
        try {
            doSendSync(eventId, eventType, value, startTime);
        } catch (Exception e) {
            log.error("Failed to send event to Kafka: eventId={}, topic={}", eventId, topic, e);
            metricsCollector.recordKafkaError(e.getClass().getSimpleName());
            handleSendFailure(eventId, eventType, value, e);
        }
    }

    /**
     * 비동기 전송 - CompletableFuture 기반
     * 메인 스레드 차단 없이 콜백을 통해 전송 성공/실패 처리
     */
    private void sendAsync(String eventId, String eventType, Object value, long startTime) {
        // 현재 스레드의 MDC 컨텍스트 캡처
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        kafkaTemplate.send(topic, eventId, value)
                .whenComplete((result, ex) -> {
                    // 콜백 스레드에서 MDC 컨텍스트 복원
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    try {
                        if (ex != null) {
                            log.error("Async send failed: eventId={}, topic={}", eventId, topic, ex);
                            metricsCollector.recordEventPublished(eventType, false, System.currentTimeMillis() - startTime);
                            metricsCollector.recordKafkaError(ex.getClass().getSimpleName());
                            handleSendFailure(eventId, eventType, value, ex);
                        } else {
                            metricsCollector.recordEventPublished(eventType, true, System.currentTimeMillis() - startTime);
                            handleSendSuccess(eventId, result);
                        }
                    } finally {
                        // MDC 컨텍스트 정리
                        if (contextMap != null) {
                            MDC.clear();
                        }
                    }
                })
                .orTimeout(asyncTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    // 타임아웃 예외 처리 시 MDC 컨텍스트 복원
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    try {
                        log.error("Async send timeout: eventId={}, topic={}, timeout={}ms",
                                eventId, topic, asyncTimeoutMs, ex);
                        metricsCollector.recordEventPublished(eventType, false, System.currentTimeMillis() - startTime);
                        metricsCollector.recordKafkaError("TimeoutException");
                        handleSendFailure(eventId, eventType, value, ex);
                        return null;
                    } finally {
                        if (contextMap != null) {
                            MDC.clear();
                        }
                    }
                });

        log.debug("Event sent asynchronously (non-blocking): eventId={}, topic={}", eventId, topic);
    }

    private SendResult<String, Object> doSendSync(String eventId, String eventType, Object value, long startTime) throws Exception {
        SendResult<String, Object> result = kafkaTemplate
                .send(topic, eventId, value)
                .get(syncTimeoutSeconds, TimeUnit.SECONDS);

        metricsCollector.recordEventPublished(eventType, true, System.currentTimeMillis() - startTime);
        handleSendSuccess(eventId, result);
        return result;
    }

    private void handleSendSuccess(String eventId, SendResult<String, Object> result) {
        var metadata = result.getRecordMetadata();
        log.debug("Event sent successfully: eventId={}, topic={}, partition={}, offset={}",
                eventId, metadata.topic(), metadata.partition(), metadata.offset());
    }

    private void handleSendFailure(String eventId, String eventType, Object originalValue, Throwable ex) {
        if (dlqEnabled) {
            metricsCollector.recordDlqEvent(eventType, ex.getClass().getSimpleName());
            dispatchToDlq(eventId, originalValue, ex);
        } else {
            log.warn("DLQ not configured. Event may be lost: eventId={}", eventId);
        }
    }

    /**
     * DLQ 전송 디스패치 - ExecutorService 존재 여부에 따라 비동기/동기 전송 결정
     */
    private void dispatchToDlq(String eventId, Object originalValue, Throwable originalException) {
        if (dlqExecutor != null) {
            // 현재 스레드의 MDC 컨텍스트 캡처
            Map<String, String> contextMap = MDC.getCopyOfContextMap();

            // 비동기 전송 - 별도 ExecutorService를 사용하여 콜백 스레드 차단 방지
            dlqExecutor.submit(() -> {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                try {
                    executeDlqSend(eventId, originalValue, originalException);
                } finally {
                    if (contextMap != null) {
                        MDC.clear();
                    }
                }
            });
        } else {
            // 동기 전송 - 이벤트 유실 방지를 위해 즉시 전송
            executeDlqSend(eventId, originalValue, originalException);
        }
    }

    /**
     * DLQ 전송 실행 - 실제 Kafka DLQ 전송 로직
     */
    private void executeDlqSend(String eventId, Object originalValue, Throwable originalException) {
        String mode = dlqExecutor != null ? "async" : "sync";
        try {
            log.warn("Sending failed event to DLQ ({}): eventId={}, dlqTopic={}", mode, eventId, dlqTopic);

            String dlqValue = createDlqPayload(eventId, originalValue, originalException);

            SendResult<String, Object> result = kafkaTemplate
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
    private String createDlqPayload(String eventId, Object originalValue, Throwable originalException)
            throws JsonProcessingException {
        
        String payloadString;
        if (originalValue instanceof String) {
            payloadString = (String) originalValue;
        } else {
            // Avro 객체 등은 toString() 또는 별도 직렬화 필요
            // 여기서는 안전하게 toString() 사용하거나 JSON 변환 시도
            try {
                payloadString = objectMapper.writeValueAsString(originalValue);
            } catch (Exception e) {
                payloadString = String.valueOf(originalValue);
            }
        }
        
        FailedEventRecord failedRecord = new FailedEventRecord(
                eventId,
                topic,
                payloadString,
                originalException.getClass().getName(),
                originalException.getMessage(),
                System.currentTimeMillis()
        );
        return objectMapper.writeValueAsString(failedRecord);
    }

    /**
     * DLQ 전송 실패 시 로컬 파일로 백업.
     * <p>
     * 이벤트 유실을 막기 위한 최후의 안전장치입니다.
     *
     * <h3>보안 강화</h3>
     * <ul>
     *   <li>POSIX 파일 시스템: 600 권한(rw-------)으로 생성</li>
     *   <li>Windows: ACL을 사용하여 현재 사용자만 접근 가능하도록 설정</li>
     *   <li>운영 환경: 보안 설정 실패 시 예외 발생</li>
     *   <li>저장되는 데이터는 PiiModule이 적용된 경우 마스킹된 상태</li>
     *   <li>페이로드 내용은 로그에 남기지 않음</li>
     * </ul>
     *
     * <h3>복구 방법</h3>
     * <p>
     * 백업 파일은 JSON 형식이므로 Kafka 복구 시 수동으로 재전송 가능합니다.
     * <pre>
     * # 백업 파일 확인
     * ls -la ./dlq-backup/
     *
     * # 재전송 (예시)
     * cat ./dlq-backup/{eventId}.json | kafka-console-producer --topic event.audit.v1
     * </pre>
     *
     * @param eventId       이벤트 ID
     * @param originalValue 직렬화된 이벤트 페이로드 (PII 마스킹 적용됨)
     */
    private void backupToLocalFile(String eventId, Object originalValue) {
        try {
            Path backupDir = Paths.get(dlqBackupPath);
            Files.createDirectories(backupDir);

            Path backupFile = backupDir.resolve(eventId + ".json");

            String content = serializeContent(originalValue);

            // 파일 쓰기
            Files.writeString(backupFile, content, StandardOpenOption.CREATE);

            // 보안 권한 적용
            boolean securityApplied = applyFilePermissions(backupFile);

            if (!securityApplied) {
                handleSecurityFailure(eventId, backupFile);
            } else {
                log.error("Event backed up to file with restricted permissions: eventId={}, file={}", eventId, backupFile);
            }

        } catch (IOException e) {
            log.error("Failed to backup event to file: eventId={}", eventId, e);
            log.error("Event permanently lost. eventId={}, cause={}", eventId, e.getMessage());
        }
    }

    /**
     * 파일 내용 직렬화
     */
    private String serializeContent(Object originalValue) {
        if (originalValue instanceof String) {
            return (String) originalValue;
        }

        try {
            return objectMapper.writeValueAsString(originalValue);
        } catch (Exception e) {
            log.warn("Failed to serialize value as JSON, using toString()", e);
            return String.valueOf(originalValue);
        }
    }

    /**
     * 플랫폼별 파일 권한 설정.
     * <p>
     * POSIX 시스템에서는 600 권한(rw-------) 설정.
     * Windows에서는 ACL을 사용하여 현재 사용자만 접근 가능하도록 설정.
     *
     * @param file 권한을 설정할 파일
     * @return 보안 설정 성공 여부
     */
    private boolean applyFilePermissions(Path file) {
        Set<String> supportedViews = FileSystems.getDefault().supportedFileAttributeViews();

        // POSIX 시스템 (Linux, macOS)
        if (supportedViews.contains("posix")) {
            return applyPosixPermissions(file);
        }
        // Windows (ACL)
        else if (supportedViews.contains("acl")) {
            return applyWindowsAclPermissions(file);
        }
        // 기타 시스템
        else {
            log.warn("File system does not support POSIX or ACL. File permissions cannot be restricted: {}", file);
            return false;
        }
    }

    /**
     * POSIX 파일 권한 설정 (Linux, macOS).
     */
    private boolean applyPosixPermissions(Path file) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(file, perms);
            log.debug("Applied POSIX permissions (600) to file: {}", file);
            return true;
        } catch (Exception e) {
            log.error("Failed to set POSIX permissions on file: {}", file, e);
            return false;
        }
    }

    /**
     * Windows ACL 권한 설정.
     * <p>
     * 현재 사용자에게만 읽기/쓰기 권한 부여.
     * 다른 모든 사용자/그룹의 접근 차단.
     */
    private boolean applyWindowsAclPermissions(Path file) {
        try {
            // ACL 뷰 가져오기
            AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (aclView == null) {
                log.error("Failed to get ACL view for file: {}", file);
                return false;
            }

            // 현재 사용자 주체(Principal)
            UserPrincipal owner = Files.getOwner(file);

            // 현재 사용자에게만 읽기/쓰기 권한 부여
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(
                            EnumSet.of(
                                    AclEntryPermission.READ_DATA,
                                    AclEntryPermission.WRITE_DATA,
                                    AclEntryPermission.APPEND_DATA,
                                    AclEntryPermission.READ_ATTRIBUTES,
                                    AclEntryPermission.WRITE_ATTRIBUTES,
                                    AclEntryPermission.READ_ACL,
                                    AclEntryPermission.SYNCHRONIZE
                            )
                    )
                    .build();

            // 기존 ACL 제거하고 새 ACL만 설정 (소유자만 접근 가능)
            aclView.setAcl(Collections.singletonList(entry));
            log.debug("Applied Windows ACL permissions (owner-only) to file: {}", file);
            return true;

        } catch (Exception e) {
            log.error("Failed to set Windows ACL permissions on file: {}", file, e);
            return false;
        }
    }

    /**
     * 보안 설정 실패 처리.
     * <p>
     * 운영 환경에서는 보안이 중요하므로 예외 발생.
     * 개발 환경에서는 경고 로그만 출력.
     */
    private void handleSecurityFailure(String eventId, Path backupFile) {
        String errorMessage = String.format(
                "Failed to apply secure file permissions. " +
                        "File may be accessible to unauthorized users: %s. " +
                        "Please configure file system security manually or use a POSIX/ACL-compliant file system.",
                backupFile);

        if (isProduction) {
            log.error("SECURITY VIOLATION in production: {}", errorMessage);
            throw new IllegalStateException(
                    "Cannot backup event with insecure file permissions in production environment. " +
                            "EventId: " + eventId + ". " + errorMessage);
        } else {
            log.warn("Event backed up without secure permissions (development mode): eventId={}, file={}",
                    eventId, backupFile);
            log.warn(errorMessage);
        }
    }
}
