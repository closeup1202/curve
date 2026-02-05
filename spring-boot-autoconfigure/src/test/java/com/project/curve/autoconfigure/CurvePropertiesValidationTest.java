package com.project.curve.autoconfigure;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CurveProperties validation test.
 *
 * Verifies that @Validated annotation and validation constraints work correctly.
 */
@DisplayName("CurveProperties validation test")
class CurvePropertiesValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("Kafka configuration validation")
    class KafkaValidationTest {

        @Test
        @DisplayName("Validation should fail when topic is empty string")
        void shouldFailWhenTopicIsBlank() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setTopic("");

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.topic"));
        }

        @Test
        @DisplayName("Validation should fail when topic is null")
        void shouldFailWhenTopicIsNull() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setTopic(null);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.topic"));
        }

        @Test
        @DisplayName("Validation should fail when retries is negative")
        void shouldFailWhenRetriesIsNegative() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setRetries(-1);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.retries"));
        }

        @Test
        @DisplayName("Validation should fail when retryBackoffMs is 0")
        void shouldFailWhenRetryBackoffMsIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setRetryBackoffMs(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.retryBackoffMs"));
        }

        @Test
        @DisplayName("Validation should fail when asyncTimeoutMs is negative")
        void shouldFailWhenAsyncTimeoutMsIsNegative() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setAsyncTimeoutMs(-1);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.asyncTimeoutMs"));
        }

        @Test
        @DisplayName("Validation should fail when dlqExecutorThreads is 0")
        void shouldFailWhenDlqExecutorThreadsIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setDlqExecutorThreads(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.dlqExecutorThreads"));
        }
    }

    @Nested
    @DisplayName("IdGenerator configuration validation")
    class IdGeneratorValidationTest {

        @Test
        @DisplayName("Validation should fail when workerId is negative")
        void shouldFailWhenWorkerIdIsNegative() {
            CurveProperties properties = new CurveProperties();
            properties.getIdGenerator().setWorkerId(-1);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("idGenerator.workerId"));
        }

        @Test
        @DisplayName("Validation should fail when workerId exceeds 1023")
        void shouldFailWhenWorkerIdExceeds1023() {
            CurveProperties properties = new CurveProperties();
            properties.getIdGenerator().setWorkerId(1024);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("idGenerator.workerId"));
        }

        @Test
        @DisplayName("Validation should succeed when workerId is 0")
        void shouldPassWhenWorkerIdIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getIdGenerator().setWorkerId(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .noneMatch(v -> v.getPropertyPath().toString().contains("idGenerator.workerId"));
        }

        @Test
        @DisplayName("Validation should succeed when workerId is 1023")
        void shouldPassWhenWorkerIdIs1023() {
            CurveProperties properties = new CurveProperties();
            properties.getIdGenerator().setWorkerId(1023);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .noneMatch(v -> v.getPropertyPath().toString().contains("idGenerator.workerId"));
        }
    }

    @Nested
    @DisplayName("Retry configuration validation")
    class RetryValidationTest {

        @Test
        @DisplayName("Validation should fail when maxAttempts is 0")
        void shouldFailWhenMaxAttemptsIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getRetry().setMaxAttempts(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("retry.maxAttempts"));
        }

        @Test
        @DisplayName("Validation should fail when initialInterval is 0")
        void shouldFailWhenInitialIntervalIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getRetry().setInitialInterval(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("retry.initialInterval"));
        }

        @Test
        @DisplayName("Validation should fail when multiplier is less than 1")
        void shouldFailWhenMultiplierIsLessThanOne() {
            CurveProperties properties = new CurveProperties();
            properties.getRetry().setMultiplier(0.5);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("retry.multiplier"));
        }

        @Test
        @DisplayName("Validation should fail when maxInterval is 0")
        void shouldFailWhenMaxIntervalIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getRetry().setMaxInterval(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("retry.maxInterval"));
        }
    }

    @Nested
    @DisplayName("Default values validation")
    class DefaultValueValidationTest {

        @Test
        @DisplayName("Properties created with default values should pass validation")
        void shouldPassValidationWithDefaultValues() {
            CurveProperties properties = new CurveProperties();

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("All default values should be set correctly")
        void shouldHaveCorrectDefaultValues() {
            CurveProperties properties = new CurveProperties();

            // Kafka defaults
            assertThat(properties.getKafka().getTopic()).isEqualTo("event.audit.v1");
            assertThat(properties.getKafka().getRetries()).isEqualTo(3);
            assertThat(properties.getKafka().getRetryBackoffMs()).isEqualTo(1000L);
            assertThat(properties.getKafka().getRequestTimeoutMs()).isEqualTo(30000);
            assertThat(properties.getKafka().isAsyncMode()).isFalse();
            assertThat(properties.getKafka().getAsyncTimeoutMs()).isEqualTo(5000L);
            assertThat(properties.getKafka().getSyncTimeoutSeconds()).isEqualTo(30L);
            assertThat(properties.getKafka().getDlqBackupPath()).isEqualTo("./dlq-backup");
            assertThat(properties.getKafka().getDlqExecutorThreads()).isEqualTo(2);
            assertThat(properties.getKafka().getDlqExecutorShutdownTimeoutSeconds()).isEqualTo(30L);

            // Retry defaults
            assertThat(properties.getRetry().isEnabled()).isTrue();
            assertThat(properties.getRetry().getMaxAttempts()).isEqualTo(3);
            assertThat(properties.getRetry().getInitialInterval()).isEqualTo(1000L);
            assertThat(properties.getRetry().getMultiplier()).isEqualTo(2.0);
            assertThat(properties.getRetry().getMaxInterval()).isEqualTo(10000L);

            // IdGenerator defaults
            assertThat(properties.getIdGenerator().getWorkerId()).isEqualTo(1L);
            assertThat(properties.getIdGenerator().isAutoGenerate()).isFalse();

            // Security defaults
            assertThat(properties.getSecurity().isUseForwardedHeaders()).isFalse();

            // PII defaults
            assertThat(properties.getPii().isEnabled()).isTrue();

            // AOP defaults
            assertThat(properties.getAop().isEnabled()).isTrue();
        }
    }
}
