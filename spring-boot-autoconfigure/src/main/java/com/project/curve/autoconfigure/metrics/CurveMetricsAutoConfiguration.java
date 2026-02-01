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
 * Curve metrics collector auto-configuration.
 * <p>
 * If Micrometer's {@link MeterRegistry} exists, collects actual metrics,
 * otherwise registers a NoOp implementation to operate safely without null checks.
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
        return null; // Do not register endpoint if MeterRegistry is not available
    }
}
