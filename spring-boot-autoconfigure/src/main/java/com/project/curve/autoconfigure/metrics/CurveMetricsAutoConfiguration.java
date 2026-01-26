package com.project.curve.autoconfigure.metrics;

import com.project.curve.autoconfigure.actuator.CurveMetricsEndpoint;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import com.project.curve.spring.metrics.MicrometerCurveMetricsCollector;
import com.project.curve.spring.metrics.NoOpCurveMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Curve 메트릭 수집기 Auto-Configuration.
 * <p>
 * Micrometer의 {@link MeterRegistry}가 존재하면 실제 메트릭을 수집하고,
 * 없으면 NoOp 구현체를 등록하여 null 체크 없이 안전하게 동작합니다.
 */
@Configuration
public class CurveMetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public CurveMetricsCollector micrometerCurveMetricsCollector(MeterRegistry meterRegistry) {
        return new MicrometerCurveMetricsCollector(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(CurveMetricsCollector.class)
    public CurveMetricsCollector noOpCurveMetricsCollector() {
        return new NoOpCurveMetricsCollector();
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public CurveMetricsEndpoint curveMetricsEndpoint(MeterRegistry meterRegistry) {
        return new CurveMetricsEndpoint(meterRegistry);
    }
}
