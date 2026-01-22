package com.project.curve.autoconfigure.retry;

import com.project.curve.autoconfigure.CurveProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Slf4j
@Configuration
@ConditionalOnProperty(
        name = "curve.retry.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CurveRetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "curveRetryTemplate")
    public RetryTemplate curveRetryTemplate(CurveProperties properties) {
        var retryConfig = properties.getRetry();

        RetryTemplate retryTemplate = new RetryTemplate();

        // Retry Policy
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(retryConfig.getMaxAttempts());
        retryTemplate.setRetryPolicy(retryPolicy);

        // Backoff Policy (Exponential)
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryConfig.getInitialInterval());
        backOffPolicy.setMultiplier(retryConfig.getMultiplier());
        backOffPolicy.setMaxInterval(retryConfig.getMaxInterval());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        log.debug("Curve RetryTemplate configured: maxAttempts={}, initialInterval={}ms, multiplier={}, maxInterval={}ms",
                retryConfig.getMaxAttempts(),
                retryConfig.getInitialInterval(),
                retryConfig.getMultiplier(),
                retryConfig.getMaxInterval());

        return retryTemplate;
    }
}
