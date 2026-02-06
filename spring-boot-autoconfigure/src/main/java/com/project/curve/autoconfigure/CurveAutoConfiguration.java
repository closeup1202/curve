package com.project.curve.autoconfigure;

import com.project.curve.autoconfigure.aop.CurveAopAutoConfiguration;
import com.project.curve.autoconfigure.context.CurveContextAutoConfiguration;
import com.project.curve.autoconfigure.envelope.CurveEnvelopeAutoConfiguration;
import com.project.curve.autoconfigure.jackson.CurveJacksonAutoConfiguration;
import com.project.curve.autoconfigure.kafka.CurveKafkaAutoConfiguration;
import com.project.curve.autoconfigure.metrics.CurveMetricsAutoConfiguration;
import com.project.curve.autoconfigure.outbox.CurveOutboxAutoConfiguration;
import com.project.curve.autoconfigure.pii.CurvePiiAutoConfiguration;
import com.project.curve.autoconfigure.retry.CurveRetryAutoConfiguration;
import com.project.curve.autoconfigure.serde.CurveEventSerializerAutoConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(CurveProperties.class)
@ConditionalOnProperty(
        name = "curve.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Import({
        CurveJacksonAutoConfiguration.class,
        CurveRetryAutoConfiguration.class,
        CurveKafkaAutoConfiguration.class,
        CurveEnvelopeAutoConfiguration.class,
        CurveContextAutoConfiguration.class,
        CurveAopAutoConfiguration.class,
        CurvePiiAutoConfiguration.class,
        CurveOutboxAutoConfiguration.class,
        CurveEventSerializerAutoConfiguration.class,
        CurveMetricsAutoConfiguration.class
})
public class CurveAutoConfiguration {

    @Autowired(required = false)
    private TaskDecorator taskDecorator;

    @PostConstruct
    public void startUp() {
        log.info("Curve auto-configuration enabled (disable with curve.enabled=false)");
    }

    @Bean(name = "curveAsyncExecutor")
    @ConditionalOnMissingBean(name = "curveAsyncExecutor")
    @ConditionalOnProperty(name = "curve.async.enabled", havingValue = "true")
    public Executor curveAsyncExecutor(CurveProperties properties) {
        CurveProperties.Async asyncProps = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncProps.getCorePoolSize());
        executor.setMaxPoolSize(asyncProps.getMaxPoolSize());
        executor.setQueueCapacity(asyncProps.getQueueCapacity());
        executor.setThreadNamePrefix("CurveAsync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        if (taskDecorator != null) {
            executor.setTaskDecorator(taskDecorator);
        }
        executor.initialize();
        return executor;
    }
}
