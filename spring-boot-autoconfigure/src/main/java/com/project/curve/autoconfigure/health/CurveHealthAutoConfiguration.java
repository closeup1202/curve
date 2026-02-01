package com.project.curve.autoconfigure.health;

import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.autoconfigure.actuator.CurveMetricsEndpoint;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Curve Health Indicator &amp; Metrics Endpoint Auto-Configuration.
 * <p>
 * Activated only when Spring Boot Actuator is present on the classpath.
 *
 * <h3>Activation Conditions</h3>
 * <ul>
 *   <li>curve.enabled=true (default)</li>
 *   <li>spring-boot-actuator is present on the classpath</li>
 *   <li>management.health.curve.enabled=true (default)</li>
 * </ul>
 *
 * @see CurveHealthIndicator
 * @see CurveMetricsEndpoint
 * @see org.springframework.boot.actuate.health.HealthIndicator
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
@ConditionalOnProperty(name = "curve.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnEnabledHealthIndicator("curve")
@EnableConfigurationProperties(CurveProperties.class)
public class CurveHealthAutoConfiguration {

    @Bean
    public CurveHealthIndicator curveHealthIndicator(
            KafkaTemplate<String, Object> kafkaTemplate,
            CurveProperties properties
    ) {
        return new CurveHealthIndicator(
                kafkaTemplate,
                properties.getKafka().getTopic(),
                properties.getKafka().getDlqTopic()
        );
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public CurveMetricsEndpoint curveMetricsEndpoint(MeterRegistry meterRegistry) {
        return new CurveMetricsEndpoint(meterRegistry);
    }
}
