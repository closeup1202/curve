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
 * Curve Health Indicator & Metrics Endpoint Auto-Configuration.
 * <p>
 * Spring Boot Actuator가 클래스패스에 있을 때만 활성화됩니다.
 *
 * <h3>활성화 조건</h3>
 * <ul>
 *   <li>curve.enabled=true (기본값)</li>
 *   <li>spring-boot-actuator가 클래스패스에 존재</li>
 *   <li>management.health.curve.enabled=true (기본값)</li>
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
