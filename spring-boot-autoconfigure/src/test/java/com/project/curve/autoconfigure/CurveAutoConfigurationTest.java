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
 * CurveAutoConfiguration 통합 테스트.
 *
 * Spring Boot Auto-Configuration이 올바르게 동작하는지 검증합니다.
 */
@DisplayName("CurveAutoConfiguration 테스트")
class CurveAutoConfigurationTest {

    // 기본 컨텍스트 러너
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
    @DisplayName("기본 설정 테스트")
    class DefaultConfigurationTest {

        @Test
        @DisplayName("curve.enabled=true일 때 모든 빈이 등록되어야 한다")
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
        @DisplayName("curve.enabled 미설정 시 기본값 true로 빈이 등록되어야 한다")
        void shouldRegisterBeansWhenEnabledPropertyMissing() {
            contextRunner
                    .run(context -> {
                        assertThat(context).hasSingleBean(CurveProperties.class);
                        assertThat(context).hasSingleBean(EventEnvelopeFactory.class);
                    });
        }

        @Test
        @DisplayName("curve.enabled=false일 때 주요 빈이 등록되지 않아야 한다")
        void shouldNotRegisterBeansWhenDisabled() {
            contextRunner
                    .withPropertyValues("curve.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(EventEnvelopeFactory.class);
                        assertThat(context).doesNotHaveBean(EventProducer.class);
                        assertThat(context).doesNotHaveBean(RetryTemplate.class);
                        // CurveProperties는 @EnableConfigurationProperties로 등록되므로
                        // CurveAutoConfiguration이 로드되지 않으면 등록되지 않아야 함
                        assertThat(context).doesNotHaveBean(CurveProperties.class);
                    });
        }
    }

    @Nested
    @DisplayName("Retry 설정 테스트")
    class RetryConfigurationTest {

        @Test
        @DisplayName("curve.retry.enabled=true일 때 RetryTemplate이 등록되어야 한다")
        void shouldRegisterRetryTemplateWhenEnabled() {
            contextRunner
                    .withPropertyValues("curve.retry.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(RetryTemplate.class);
                    });
        }

        @Test
        @DisplayName("curve.retry.enabled=false일 때 RetryTemplate이 등록되지 않아야 한다")
        void shouldNotRegisterRetryTemplateWhenDisabled() {
            contextRunner
                    .withPropertyValues("curve.retry.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(RetryTemplate.class);
                    });
        }
    }

    @Nested
    @DisplayName("Kafka 설정 테스트")
    class KafkaConfigurationTest {

        @Test
        @DisplayName("Kafka 설정이 있을 때 EventProducer가 등록되어야 한다")
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
        @DisplayName("DLQ 토픽이 설정되면 DLQ 기능이 활성화되어야 한다")
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
    @DisplayName("Properties 바인딩 테스트")
    class PropertiesBindingTest {

        @Test
        @DisplayName("커스텀 설정값이 올바르게 바인딩되어야 한다")
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
        @DisplayName("기본 설정값이 올바르게 적용되어야 한다")
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
    @DisplayName("AOP 설정 테스트")
    class AopConfigurationTest {

        @Test
        @DisplayName("curve.aop.enabled=false일 때 Aspect가 등록되지 않아야 한다")
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
        @DisplayName("curve.aop.enabled=true일 때 Aspect가 등록되어야 한다")
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
    @DisplayName("PII 설정 테스트")
    class PiiConfigurationTest {

        @Test
        @DisplayName("PII 암호화 키가 설정되면 올바르게 바인딩되어야 한다")
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
        @DisplayName("curve.pii.enabled=false일 때 PII 모듈이 비활성화되어야 한다")
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
    @DisplayName("Security 설정 테스트")
    class SecurityConfigurationTest {

        @Test
        @DisplayName("useForwardedHeaders 설정이 올바르게 바인딩되어야 한다")
        void shouldBindSecurityProperties() {
            contextRunner
                    .withPropertyValues("curve.security.use-forwarded-headers=true")
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);
                        assertThat(props.getSecurity().isUseForwardedHeaders()).isTrue();
                    });
        }

        @Test
        @DisplayName("기본값으로 useForwardedHeaders=false여야 한다")
        void shouldDefaultToFalseForUseForwardedHeaders() {
            contextRunner
                    .run(context -> {
                        CurveProperties props = context.getBean(CurveProperties.class);
                        assertThat(props.getSecurity().isUseForwardedHeaders()).isFalse();
                    });
        }
    }
}
