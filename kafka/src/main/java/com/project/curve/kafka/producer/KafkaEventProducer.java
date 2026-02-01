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
 * Kafka-based event publisher.
 * <p>
 * Serializes and publishes events to Kafka topics.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Retry support via RetryTemplate</li>
 *   <li>Sends to DLQ (Dead Letter Queue) on transmission failure to prevent event loss</li>
 *   <li>Local file backup as last resort if DLQ transmission also fails</li>
 *   <li>Supports both synchronous and asynchronous transmission modes</li>
 * </ul>
 *
 * <h2>PII (Personally Identifiable Information) Handling</h2>
 * <p>
 * During event serialization, if {@link com.project.curve.spring.pii.jackson.PiiModule} is registered
 * with ObjectMapper, fields annotated with {@code @PiiField} are automatically masked/encrypted.
 * <p>
 * <b>Security Notes:</b>
 * <ul>
 *   <li>PII masking is applied only when {@code curve.pii.enabled=true} (default).</li>
 *   <li>Data stored in DLQ and local backup files are also in masked state.</li>
 *   <li>Local backup files are created with 600 permissions (rw-------) on POSIX systems.</li>
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
     * Asynchronous transmission - CompletableFuture based
     * Handles transmission success/failure via callbacks without blocking the main thread
     */
    private void sendAsync(String eventId, String eventType, Object value, long startTime) {
        // Capture MDC context from current thread
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        kafkaTemplate.send(topic, eventId, value)
                .whenComplete((result, ex) -> {
                    // Restore MDC context in callback thread
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
                        // Clean up MDC context
                        if (contextMap != null) {
                            MDC.clear();
                        }
                    }
                })
                .orTimeout(asyncTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    // Restore MDC context when handling timeout exception
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
     * DLQ transmission dispatch - Decides async/sync transmission based on ExecutorService existence
     */
    private void dispatchToDlq(String eventId, Object originalValue, Throwable originalException) {
        if (dlqExecutor != null) {
            // Capture MDC context from current thread
            Map<String, String> contextMap = MDC.getCopyOfContextMap();

            // Asynchronous transmission - Use separate ExecutorService to prevent callback thread blocking
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
            // Synchronous transmission - Send immediately to prevent event loss
            executeDlqSend(eventId, originalValue, originalException);
        }
    }

    /**
     * Execute DLQ transmission - Actual Kafka DLQ transmission logic
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
     * Create DLQ payload - Serialize FailedEventRecord to JSON
     */
    private String createDlqPayload(String eventId, Object originalValue, Throwable originalException)
            throws JsonProcessingException {
        
        String payloadString;
        if (originalValue instanceof String) {
            payloadString = (String) originalValue;
        } else {
            // Avro objects etc. need toString() or separate serialization
            // Here we safely use toString() or attempt JSON conversion
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
     * Backup to local file when DLQ transmission also fails.
     * <p>
     * This is the last resort safety net to prevent event loss.
     *
     * <h3>Security Enhancements</h3>
     * <ul>
     *   <li>POSIX file system: Created with 600 permissions (rw-------)</li>
     *   <li>Windows: Uses ACL to make file accessible only to current user</li>
     *   <li>Production environment: Throws IllegalStateException if security setup fails</li>
     *   <li>Stored data is in masked state if PiiModule is applied</li>
     *   <li>Payload content is not logged</li>
     * </ul>
     *
     * <h3>Recovery Method</h3>
     * <p>
     * Backup files are in JSON format and can be manually resent when Kafka is recovered.
     * <pre>
     * # Check backup files
     * ls -la ./dlq-backup/
     *
     * # Resend (example)
     * cat ./dlq-backup/{eventId}.json | kafka-console-producer --topic event.audit.v1
     * </pre>
     *
     * @param eventId       Event ID
     * @param originalValue Serialized event payload (with PII masking applied)
     */
    private void backupToLocalFile(String eventId, Object originalValue) {
        try {
            Path backupDir = Paths.get(dlqBackupPath);
            Files.createDirectories(backupDir);

            Path backupFile = backupDir.resolve(eventId + ".json");

            String content = serializeContent(originalValue);

            // Write file
            Files.writeString(backupFile, content, StandardOpenOption.CREATE);

            // Apply security permissions
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
     * Serialize file content
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
     * Platform-specific file permission setup.
     * <p>
     * On POSIX systems, sets 600 permissions (rw-------).
     * On Windows, uses ACL to make file accessible only to current user.
     *
     * @param file File to set permissions on
     * @return Whether security setup succeeded
     */
    private boolean applyFilePermissions(Path file) {
        Set<String> supportedViews = FileSystems.getDefault().supportedFileAttributeViews();

        // POSIX systems (Linux, macOS)
        if (supportedViews.contains("posix")) {
            return applyPosixPermissions(file);
        }
        // Windows (ACL)
        else if (supportedViews.contains("acl")) {
            return applyWindowsAclPermissions(file);
        }
        // Other systems
        else {
            log.warn("File system does not support POSIX or ACL. File permissions cannot be restricted: {}", file);
            return false;
        }
    }

    /**
     * Set POSIX file permissions (Linux, macOS).
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
     * Set Windows ACL permissions.
     * <p>
     * Sets ACL to allow only current user to read/write.
     * Denies access to all other users/groups.
     */
    private boolean applyWindowsAclPermissions(Path file) {
        try {
            // Get ACL view
            AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (aclView == null) {
                log.error("Failed to get ACL view for file: {}", file);
                return false;
            }

            // Current user principal
            UserPrincipal owner = Files.getOwner(file);

            // Grant read/write permissions only to current user
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

            // Remove existing ACL and set only new ACL (only owner can access)
            aclView.setAcl(Collections.singletonList(entry));
            log.debug("Applied Windows ACL permissions (owner-only) to file: {}", file);
            return true;

        } catch (Exception e) {
            log.error("Failed to set Windows ACL permissions on file: {}", file, e);
            return false;
        }
    }

    /**
     * Handle security setup failure.
     * <p>
     * In production environments, security is critical so exception is thrown.
     * In development environments, only warning is logged.
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
