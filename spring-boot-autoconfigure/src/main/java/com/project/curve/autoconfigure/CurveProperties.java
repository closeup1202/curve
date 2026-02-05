package com.project.curve.autoconfigure;

import com.project.curve.autoconfigure.outbox.InitializeSchema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "curve")
public class CurveProperties {

    private boolean enabled = true;

    @Valid
    private final Kafka kafka = new Kafka();

    @Valid
    private final Retry retry = new Retry();

    @Valid
    private final Aop aop = new Aop();

    @Valid
    private final IdGenerator idGenerator = new IdGenerator();

    @Valid
    private final Security security = new Security();

    @Valid
    private final Pii pii = new Pii();

    @Valid
    private final Outbox outbox = new Outbox();

    @Valid
    private final Serde serde = new Serde();

    @Data
    public static class Kafka {
        /**
         * Kafka topic name.
         */
        @NotBlank(message = "Kafka topic is required")
        private String topic = "event.audit.v1";

        /**
         * Dead Letter Queue topic name (optional).
         * <p>
         * If not set, DLQ feature will be disabled.
         */
        private String dlqTopic;

        /**
         * Number of retries for Kafka Producer (default: 3).
         * <p>
         * Kafka Producer's own retry setting (spring.kafka.producer.retries takes precedence).
         */
        @PositiveOrZero(message = "retries must be 0 or greater")
        private int retries = 3;

        /**
         * Retry backoff time in milliseconds (default: 1000ms = 1 second).
         */
        @Positive(message = "retryBackoffMs must be positive")
        private long retryBackoffMs = 1000L;

        /**
         * Request timeout in milliseconds (default: 30000ms = 30 seconds).
         */
        @Positive(message = "requestTimeoutMs must be positive")
        private int requestTimeoutMs = 30000;

        /**
         * Whether to enable async send mode (default: false - sync send).
         * <p>
         * true: async send (high performance, callback handling on send failure)
         * false: sync send (lower performance, guaranteed delivery)
         */
        private boolean asyncMode = false;

        /**
         * Async send timeout in milliseconds (default: 5000ms = 5 seconds).
         * <p>
         * Used only when asyncMode=true.
         */
        @Positive(message = "asyncTimeoutMs must be positive")
        private long asyncTimeoutMs = 5000L;

        /**
         * Sync send timeout in seconds (default: 30 seconds).
         * <p>
         * Used when asyncMode=false.
         */
        @Positive(message = "syncTimeoutSeconds must be positive")
        private long syncTimeoutSeconds = 30L;

        /**
         * Local backup directory path for DLQ send failures (default: ./dlq-backup).
         * <p>
         * Backs up events to local files when DLQ send also fails.
         */
        private String dlqBackupPath = "./dlq-backup";

        /**
         * Thread pool size for DLQ-dedicated ExecutorService (default: 2).
         * <p>
         * Separate thread pool to prevent main callback thread blocking during DLQ send in async mode.
         */
        @Min(value = 1, message = "dlqExecutorThreads must be at least 1")
        private int dlqExecutorThreads = 2;

        /**
         * DLQ ExecutorService graceful shutdown timeout in seconds (default: 30 seconds).
         * <p>
         * Time to wait for in-progress DLQ tasks to complete during application shutdown.
         * Forces shutdown if timeout is exceeded.
         */
        @Positive(message = "dlqExecutorShutdownTimeoutSeconds must be positive")
        private long dlqExecutorShutdownTimeoutSeconds = 30L;

        /**
         * Whether running in production environment (default: false).
         * <p>
         * true: production mode - throws exception on DLQ backup file security failure
         * false: development mode - only logs warning on DLQ backup file security failure
         * <p>
         * Security considerations:
         * - Must be set to true in production environments
         * - ACL support required in Windows environments
         * - POSIX systems (Linux, macOS) recommended
         */
        private boolean isProduction = false;

        /**
         * Backup strategy configuration.
         */
        @Valid
        private final Backup backup = new Backup();

        @Data
        public static class Backup {
            /**
             * Whether to enable local file backup (default: true).
             */
            private boolean localEnabled = true;

            /**
             * Whether to enable S3 backup (default: false).
             */
            private boolean s3Enabled = false;

            /**
             * S3 bucket name (required if s3Enabled=true).
             */
            private String s3Bucket;

            /**
             * S3 object key prefix (default: dlq-backup).
             */
            private String s3Prefix = "dlq-backup";
        }
    }

    @Data
    public static class Retry {
        /**
         * Whether to enable retry on send failure (default: true).
         */
        private boolean enabled = true;

        /**
         * Maximum number of retry attempts (default: 3).
         */
        @Min(value = 1, message = "maxAttempts must be at least 1")
        private int maxAttempts = 3;

        /**
         * Initial delay for retry backoff in milliseconds (default: 1000ms = 1 second).
         */
        @Positive(message = "initialInterval must be positive")
        private long initialInterval = 1000L;

        /**
         * Retry backoff multiplier (default: 2.0).
         * <p>
         * Example: 1 second -> 2 seconds -> 4 seconds
         */
        @Min(value = 1, message = "multiplier must be at least 1")
        private double multiplier = 2.0;

        /**
         * Maximum delay for retry backoff in milliseconds (default: 10000ms = 10 seconds).
         */
        @Positive(message = "maxInterval must be positive")
        private long maxInterval = 10000L;
    }

    @Data
    public static class Aop {
        /**
         * Whether to enable @PublishEvent AOP (default: true).
         */
        private boolean enabled = true;
    }

    @Data
    public static class IdGenerator {
        /**
         * Worker ID for Snowflake ID Generator (0 ~ 1023).
         * <p>
         * Must be set to a unique value for each instance in distributed environments.
         * Default: 1
         */
        @Min(value = 0, message = "workerId must be at least 0")
        @Max(value = 1023, message = "workerId must be at most 1023")
        private long workerId = 1L;

        /**
         * Worker ID auto-generation mode (default: false).
         * <p>
         * true: auto-generate based on MAC address (possible collision)
         * false: use configured workerId value
         */
        private boolean autoGenerate = false;
    }

    @Data
    public static class Security {
        /**
         * Whether to use X-Forwarded-For header (default: false).
         * <p>
         * true: recommended to use Spring Boot's ForwardedHeaderFilter
         * false: use only request.getRemoteAddr() (most secure)
         * <p>
         * When running behind proxy/load balancer:
         * server.forward-headers-strategy=framework setting is required
         * <p>
         * Security considerations:
         * - Set to false in untrusted proxy environments
         * - Be aware of X-Forwarded-For header spoofing attacks
         * - In production, server.tomcat.remoteip.internal-proxies setting is required
         */
        private boolean useForwardedHeaders = false;
    }

    @Data
    public static class Pii {
        /**
         * Whether to enable PII processing feature (default: true).
         */
        private boolean enabled = true;

        /**
         * Encryption/hashing configuration.
         */
        private final Crypto crypto = new Crypto();

        /**
         * KMS configuration.
         */
        private final Kms kms = new Kms();

        @Data
        public static class Crypto {
            /**
             * Default encryption key (Base64-encoded AES-256 key).
             * <p>
             * Recommended to use PII_ENCRYPTION_KEY environment variable.
             * Encryption feature is unavailable if not set.
             */
            private String defaultKey;

            /**
             * Salt to use for hashing.
             * <p>
             * Recommended to use PII_HASH_SALT environment variable.
             * Hashes without salt if not set.
             */
            private String salt;
        }

        @Data
        public static class Kms {
            /**
             * Whether to enable KMS for PII encryption keys (default: false).
             * <p>
             * When enabled, configure type and provider-specific settings in
             * {@code curve.pii.kms.*} properties (handled by kms module's KmsProperties).
             */
            private boolean enabled = false;

            /**
             * KMS type: "aws" or "vault".
             */
            private String type;
        }
    }

    @Data
    public static class Outbox {
        /**
         * Whether to enable Transactional Outbox Pattern (default: false).
         * <p>
         * true: guarantees atomicity between DB transaction and event publishing
         * - Can use outbox=true in @PublishEvent
         * - Periodically publishes PENDING events to Kafka
         * <p>
         * false: legacy mode (immediately publish to Kafka)
         */
        private boolean enabled = false;

        /**
         * Whether to enable Outbox event publisher (default: true).
         * <p>
         * true: periodically queries PENDING events within the application and publishes to Kafka (polling method)
         * false: only saves to DB without publishing. Use when publishing externally via CDC (Debezium, etc.)
         */
        private boolean publisherEnabled = true;

        /**
         * Outbox table schema initialization mode (default: embedded).
         * <p>
         * embedded: auto-create only for embedded DB (H2, HSQLDB)
         * always: always auto-create (only when table doesn't exist)
         * never: manually manage in service (Flyway/Liquibase, etc.)
         */
        private InitializeSchema initializeSchema = InitializeSchema.EMBEDDED;

        /**
         * Outbox event polling interval in milliseconds (default: 1000ms = 1 second).
         * <p>
         * Configures how often to query events in PENDING status.
         */
        @Positive(message = "pollIntervalMs must be positive")
        private long pollIntervalMs = 1000L;

        /**
         * Event batch size to process at once (default: 100).
         * <p>
         * Maximum number of events to process at once when there are many PENDING events.
         */
        @Min(value = 1, message = "batchSize must be at least 1")
        @Max(value = 1000, message = "batchSize must be at most 1000")
        private int batchSize = 100;

        /**
         * Maximum retry count for Outbox event publishing (default: 3).
         * <p>
         * Maximum number of retries when publishing fails in PENDING status.
         * Changes to FAILED status when exceeded.
         */
        @Min(value = 1, message = "maxRetries must be at least 1")
        private int maxRetries = 3;

        /**
         * Kafka send timeout in seconds (default: 10 seconds).
         * <p>
         * Maximum time to wait for response when publishing events to Kafka.
         * Treated as retry candidate when timeout is exceeded.
         */
        @Positive(message = "sendTimeoutSeconds must be positive")
        private int sendTimeoutSeconds = 10;

        /**
         * Whether to enable automatic cleanup of PUBLISHED events (default: false).
         * <p>
         * true: periodically delete old PUBLISHED events to manage table size
         * false: manual cleanup required
         */
        private boolean cleanupEnabled = false;

        /**
         * Retention period for PUBLISHED events in days (default: 7 days).
         * <p>
         * When cleanupEnabled=true, deletes PUBLISHED events older than this period.
         */
        @Min(value = 1, message = "retentionDays must be at least 1")
        private int retentionDays = 7;

        /**
         * Cleanup task execution schedule (cron expression) (default: 2 AM daily).
         * <p>
         * Used when cleanupEnabled=true.
         */
        private String cleanupCron = "0 0 2 * * *";

        /**
         * Whether to enable dynamic batch size adjustment (default: true).
         * <p>
         * true: automatically adjusts batch size based on queue depth (pending event count)
         *   - pending > 1000: batchSize * 2 (max 500)
         *   - pending > 500: batchSize * 1.5 (max 300)
         *   - pending < 10: min(batchSize, 10)
         * false: use fixed batchSize
         * <p>
         * Automatically increases throughput in high-load situations to quickly drain the queue.
         */
        private boolean dynamicBatchingEnabled = true;

        /**
         * Whether to enable Circuit Breaker (default: true).
         * <p>
         * true: automatically blocks requests to protect system when Kafka failure is detected
         *   - Circuit OPEN after 5 consecutive failures
         *   - Automatically transitions to Half-Open state after 1 minute to attempt recovery
         * false: continues retrying even on failure
         * <p>
         * Prevents meaningless retries and reduces system load when Kafka is down for extended periods.
         */
        private boolean circuitBreakerEnabled = true;
    }

    @Data
    public static class Serde {
        /**
         * Serialization type (default: JSON).
         * <p>
         * - JSON: Jackson-based JSON serialization
         * - AVRO: Avro-based binary serialization (implementation required)
         * - PROTOBUF: Protocol Buffers-based serialization (implementation required)
         */
        private SerdeType type = SerdeType.JSON;

        /**
         * Schema Registry URL (required when using Avro).
         */
        private String schemaRegistryUrl;

        public enum SerdeType {
            JSON, AVRO, PROTOBUF
        }
    }
}
