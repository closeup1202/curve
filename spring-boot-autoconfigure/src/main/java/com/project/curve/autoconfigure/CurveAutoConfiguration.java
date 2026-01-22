package com.project.curve.autoconfigure;

import com.project.curve.autoconfigure.aop.CurveAopAutoConfiguration;
import com.project.curve.autoconfigure.context.CurveContextAutoConfiguration;
import com.project.curve.autoconfigure.envelope.CurveEnvelopeAutoConfiguration;
import com.project.curve.autoconfigure.jackson.CurveJacksonAutoConfiguration;
import com.project.curve.autoconfigure.kafka.CurveKafkaAutoConfiguration;
import com.project.curve.autoconfigure.pii.CurvePiiAutoConfiguration;
import com.project.curve.autoconfigure.retry.CurveRetryAutoConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(CurveProperties.class)
@ConditionalOnProperty(
        name = "curve.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableAsync
@Import({
        CurveJacksonAutoConfiguration.class,
        CurveRetryAutoConfiguration.class,
        CurveKafkaAutoConfiguration.class,
        CurveEnvelopeAutoConfiguration.class,
        CurveContextAutoConfiguration.class,
        CurveAopAutoConfiguration.class,
        CurvePiiAutoConfiguration.class,
})
public class CurveAutoConfiguration {

    @PostConstruct
    public void startUp() {
        log.info("Curve auto-configuration enabled (disable with curve.enabled=false)");
    }
}
