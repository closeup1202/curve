package com.project.curve.autoconfigure;

import com.project.curve.core.port.EventProducer;
import com.project.curve.spring.audit.aop.PublishEventAspect;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CurveAutoConfiguration Integration test.
 *
 * Verifies that Spring Boot Auto-Configuration works correctly.
 */
@DisplayName("CurveAutoConfiguration test")
class CurveAutoConfigurationTest {

    // Default context runner
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CurveAutoConfiguration.class,
                    KafkaAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    JacksonAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.kafka.bootstrap-servers=localhost:9092",
                    "curve.kafka.topic=test-topic",
                    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password="
            );

    @Nested
    @DisplayName("Default configuration test")
    class DefaultConfigurationTest {

        @Test
        @DisplayName("All beans should be registered when curve.enabled=true")
        void shouldRegisterAllBeansWhenEnabled() {
            contextRunner
                    .withPropertyValues("curve.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(CurveProperties.class);
                        assertThat(context).hasSingleBean(EventEnvelopeFactory.class);
                        assertThat(context).hasSingleBean(EventProducer.class);
                        assertThat(context).hasSingleBean(RetryTemplate.class);
                    });
        }

        @Test
        @DisplayName("Beans should be registered with default value true when curve.enabled is not set")
        void shouldRegisterBeansWhenEnabledPropertyMissing() {
            contextRunner
                    .run(context -> {
                        assertThat(context).hasSingleBean(CurveProperties.class);
                        assertThat(context).hasSingleBean(EventEnvelopeFactory.class);
                    });
        }

        @Test
        @DisplayName("Main beans should not be registered when curve.enabled=false")
        void shouldNotRegisterBeansWhenDisabled() {
            contextRunner
                    .withPropertyValues("curve.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(EventEnvelopeFactory.class);
                        assertThat(context).doesNotHaveBean(EventProducer.class);
                        assertThat(context).doesNotHaveBean(RetryTemplate.class);
                        // Since CurveProperties is registered via @EnableConfigurationProperties,
                        // it should not be registered if CurveAutoConfiguration is not loaded
                        assertThat(context).doesNotHaveBean(CurveProperties.class);
                    });
        }
    }

    @Nested
    @DisplayName("Retry configuration test")
    class RetryConfigurationTest {

        @Test
        @DisplayName("RetryTemplate should be registered when curve.retry.enabled=true")
        void shouldRegisterRetryTemplateWhenEnabled() {
            contextRunner
                    .withPropertyValues("curve.retry.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(RetryTemplate.class);
                    });
        }

        @Test
        @DisplayName("RetryTemplate should not be registered when curve.retry.enabled=false")
        void shouldNotRegisterRetryTemplateWhenDisabled() {
            contextRunner
                    .withPropertyValues("curve.retry.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(RetryTemplate.class);
                    });
        }
    }

    @Nested
    @DisplayName("Kafka configuration test")
    class KafkaConfigurationTest {

        @Test
        @DisplayName("EventProducer should be registered when Kafka configuration exists")
        void shouldRegisterEventProducerWithKafkaConfig() {
            contextRunner
                    .withPropertyValues(
                            "curve.kafka.topic=event.audit.v1",
                            "curve.kafka.async-mode=false"
                    )
                    .run(context -> {
                        assertThat(context).hasSingleBean(EventProducer.class);
                    });
        }

        @Test
        @DisplayName("DLQ feature should be activated when DLQ topic is configured")
        void shouldEnableDlqWhenDlqTopicConfigured() {
            contextRunner
                    .withPropertyValues(
                            "curve.kafka.topic=event.audit.v1",
                            "curve.kafka.dlq-topic=event.audit.dlq.v1"
                    )
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);
                        assertThat(props.getKafka().getDlqTopic()).isEqualTo("event.audit.dlq.v1");
                    });
        }
    }

    @Nested
    @DisplayName("Properties binding test")
    class PropertiesBindingTest {

        @Test
        @DisplayName("Custom configuration values should be bound correctly")
        void shouldBindCustomProperties() {
            contextRunner
                    .withPropertyValues(
                            "curve.kafka.topic=custom-topic",
                            "curve.kafka.retries=5",
                            "curve.kafka.async-mode=true",
                            "curve.kafka.async-timeout-ms=10000",
                            "curve.retry.max-attempts=5",
                            "curve.retry.initial-interval=2000",
                            "curve.id-generator.worker-id=100",
                            "curve.outbox.enabled=true",
                            "curve.outbox.poll-interval-ms=500",
                            "curve.serde.type=AVRO"
                    )
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);

                        assertThat(props.getKafka().getTopic()).isEqualTo("custom-topic");
                        assertThat(props.getKafka().getRetries()).isEqualTo(5);
                        assertThat(props.getKafka().isAsyncMode()).isTrue();
                        assertThat(props.getKafka().getAsyncTimeoutMs()).isEqualTo(10000L);
                        assertThat(props.getRetry().getMaxAttempts()).isEqualTo(5);
                        assertThat(props.getRetry().getInitialInterval()).isEqualTo(2000L);
                        assertThat(props.getIdGenerator().getWorkerId()).isEqualTo(100L);
                        assertThat(props.getOutbox().getPollIntervalMs()).isEqualTo(500L);
                        assertThat(props.getSerde().getType()).isEqualTo(CurveProperties.Serde.SerdeType.AVRO);
                    });
        }

        @Test
        @DisplayName("Default configuration values should be applied correctly")
        void shouldApplyDefaultValues() {
            contextRunner
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);

                        assertThat(props.isEnabled()).isTrue();
                        assertThat(props.getKafka().getRetries()).isEqualTo(3);
                        assertThat(props.getKafka().isAsyncMode()).isFalse();
                        assertThat(props.getKafka().getAsyncTimeoutMs()).isEqualTo(5000L);
                        assertThat(props.getKafka().getSyncTimeoutSeconds()).isEqualTo(30L);
                        assertThat(props.getRetry().isEnabled()).isTrue();
                        assertThat(props.getRetry().getMaxAttempts()).isEqualTo(3);
                        assertThat(props.getRetry().getMultiplier()).isEqualTo(2.0);
                        assertThat(props.getIdGenerator().getWorkerId()).isEqualTo(1L);
                        assertThat(props.getIdGenerator().isAutoGenerate()).isFalse();
                        assertThat(props.getOutbox().isEnabled()).isFalse();
                        assertThat(props.getSerde().getType()).isEqualTo(CurveProperties.Serde.SerdeType.JSON);
                    });
        }
    }

    @Nested
    @DisplayName("AOP configuration test")
    class AopConfigurationTest {

        @Test
        @DisplayName("Aspect should not be registered when curve.aop.enabled=false")
        void shouldNotRegisterAspectWhenDisabled() {
            contextRunner
                    .withPropertyValues("curve.aop.enabled=false")
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);
                        assertThat(props.getAop().isEnabled()).isFalse();
                        assertThat(context).doesNotHaveBean("publishEventAspect");
                    });
        }

        @Test
        @DisplayName("Aspect should be registered when curve.aop.enabled=true")
        void shouldRegisterAspectWhenEnabled() {
            contextRunner
                    .withPropertyValues("curve.aop.enabled=true")
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);
                        assertThat(props.getAop().isEnabled()).isTrue();
                        assertThat(context).hasSingleBean(PublishEventAspect.class);
                    });
        }
    }

    @Nested
    @DisplayName("PII configuration test")
    class PiiConfigurationTest {

        @Test
        @DisplayName("PII encryption key should be bound correctly when configured")
        void shouldBindPiiCryptoKey() {
            contextRunner
                    .withPropertyValues(
                            "curve.pii.enabled=true",
                            "curve.pii.crypto.default-key=dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRl",
                            "curve.pii.crypto.salt=test-salt"
                    )
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);

                        assertThat(props.getPii().isEnabled()).isTrue();
                        assertThat(props.getPii().getCrypto().getDefaultKey())
                                .isEqualTo("dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRl");
                        assertThat(props.getPii().getCrypto().getSalt())
                                .isEqualTo("test-salt");
                    });
        }

        @Test
        @DisplayName("PII module should be deactivated when curve.pii.enabled=false")
        void shouldDisablePiiModuleWhenDisabled() {
            contextRunner
                    .withPropertyValues("curve.pii.enabled=false")
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);
                        assertThat(props.getPii().isEnabled()).isFalse();
                    });
        }
    }

    @Nested
    @DisplayName("Security configuration test")
    class SecurityConfigurationTest {

        @Test
        @DisplayName("useForwardedHeaders setting should be bound correctly")
        void shouldBindSecurityProperties() {
            contextRunner
                    .withPropertyValues("curve.security.use-forwarded-headers=true")
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);
                        assertThat(props.getSecurity().isUseForwardedHeaders()).isTrue();
                    });
        }

        @Test
        @DisplayName("useForwardedHeaders should default to false")
        void shouldDefaultToFalseForUseForwardedHeaders() {
            contextRunner
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);
                        assertThat(props.getSecurity().isUseForwardedHeaders()).isFalse();
                    });
        }
    }
}
