package com.project.curve.autoconfigure.metrics;

import com.project.curve.autoconfigure.actuator.CurveMetricsEndpoint;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import com.project.curve.spring.metrics.MicrometerCurveMetricsCollector;
import com.project.curve.spring.metrics.NoOpCurveMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
@ConditionalOnClass(MeterRegistry.class)
@AutoConfigureAfter({
    MetricsAutoConfiguration.class,
    PrometheusMetricsExportAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration.class
})
@Slf4j
public class CurveMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CurveMetricsCollector.class)
    public CurveMetricsCollector curveMetricsCollector(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            log.debug("MeterRegistry is available. Using MicrometerCurveMetricsCollector");
            return new MicrometerCurveMetricsCollector(meterRegistry);
        } else {
            return new NoOpCurveMetricsCollector();
        }
    }

    @Bean
    public CurveMetricsEndpoint curveMetricsEndpoint(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry != null) {
            return new CurveMetricsEndpoint(meterRegistry);
        }
        return null; // MeterRegistry가 없으면 엔드포인트 등록 안 함
    }
}
