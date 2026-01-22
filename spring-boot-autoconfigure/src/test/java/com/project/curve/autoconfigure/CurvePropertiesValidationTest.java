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
 * CurveProperties 검증 테스트.
 *
 * @Validated 어노테이션과 검증 제약조건이 올바르게 동작하는지 검증합니다.
 */
@DisplayName("CurveProperties 검증 테스트")
class CurvePropertiesValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("Kafka 설정 검증")
    class KafkaValidationTest {

        @Test
        @DisplayName("topic이 빈 문자열이면 검증 실패해야 한다")
        void shouldFailWhenTopicIsBlank() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setTopic("");

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.topic"));
        }

        @Test
        @DisplayName("topic이 null이면 검증 실패해야 한다")
        void shouldFailWhenTopicIsNull() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setTopic(null);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.topic"));
        }

        @Test
        @DisplayName("retries가 음수이면 검증 실패해야 한다")
        void shouldFailWhenRetriesIsNegative() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setRetries(-1);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.retries"));
        }

        @Test
        @DisplayName("retryBackoffMs가 0이면 검증 실패해야 한다")
        void shouldFailWhenRetryBackoffMsIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setRetryBackoffMs(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.retryBackoffMs"));
        }

        @Test
        @DisplayName("asyncTimeoutMs가 음수이면 검증 실패해야 한다")
        void shouldFailWhenAsyncTimeoutMsIsNegative() {
            CurveProperties properties = new CurveProperties();
            properties.getKafka().setAsyncTimeoutMs(-1);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("kafka.asyncTimeoutMs"));
        }

        @Test
        @DisplayName("dlqExecutorThreads가 0이면 검증 실패해야 한다")
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
    @DisplayName("IdGenerator 설정 검증")
    class IdGeneratorValidationTest {

        @Test
        @DisplayName("workerId가 음수이면 검증 실패해야 한다")
        void shouldFailWhenWorkerIdIsNegative() {
            CurveProperties properties = new CurveProperties();
            properties.getIdGenerator().setWorkerId(-1);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("idGenerator.workerId"));
        }

        @Test
        @DisplayName("workerId가 1023을 초과하면 검증 실패해야 한다")
        void shouldFailWhenWorkerIdExceeds1023() {
            CurveProperties properties = new CurveProperties();
            properties.getIdGenerator().setWorkerId(1024);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("idGenerator.workerId"));
        }

        @Test
        @DisplayName("workerId가 0이면 검증 성공해야 한다")
        void shouldPassWhenWorkerIdIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getIdGenerator().setWorkerId(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .noneMatch(v -> v.getPropertyPath().toString().contains("idGenerator.workerId"));
        }

        @Test
        @DisplayName("workerId가 1023이면 검증 성공해야 한다")
        void shouldPassWhenWorkerIdIs1023() {
            CurveProperties properties = new CurveProperties();
            properties.getIdGenerator().setWorkerId(1023);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations)
                    .noneMatch(v -> v.getPropertyPath().toString().contains("idGenerator.workerId"));
        }
    }

    @Nested
    @DisplayName("Retry 설정 검증")
    class RetryValidationTest {

        @Test
        @DisplayName("maxAttempts가 0이면 검증 실패해야 한다")
        void shouldFailWhenMaxAttemptsIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getRetry().setMaxAttempts(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("retry.maxAttempts"));
        }

        @Test
        @DisplayName("initialInterval이 0이면 검증 실패해야 한다")
        void shouldFailWhenInitialIntervalIsZero() {
            CurveProperties properties = new CurveProperties();
            properties.getRetry().setInitialInterval(0);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("retry.initialInterval"));
        }

        @Test
        @DisplayName("multiplier가 1 미만이면 검증 실패해야 한다")
        void shouldFailWhenMultiplierIsLessThanOne() {
            CurveProperties properties = new CurveProperties();
            properties.getRetry().setMultiplier(0.5);

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().contains("retry.multiplier"));
        }

        @Test
        @DisplayName("maxInterval이 0이면 검증 실패해야 한다")
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
    @DisplayName("기본값 검증")
    class DefaultValueValidationTest {

        @Test
        @DisplayName("기본값으로 생성된 Properties는 검증을 통과해야 한다")
        void shouldPassValidationWithDefaultValues() {
            CurveProperties properties = new CurveProperties();

            Set<ConstraintViolation<CurveProperties>> violations = validator.validate(properties);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("모든 기본값이 올바르게 설정되어야 한다")
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
