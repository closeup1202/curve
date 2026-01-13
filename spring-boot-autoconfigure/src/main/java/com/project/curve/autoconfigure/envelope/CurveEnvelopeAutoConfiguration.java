package com.project.curve.autoconfigure.envelope;

import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.port.SnowflakeIdGenerator;
import com.project.curve.core.port.UtcClockProvider;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CurveEnvelopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ClockProvider.class)
    public ClockProvider clockProvider() {
        return new UtcClockProvider();
    }

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator idGenerator() {
        return new SnowflakeIdGenerator(1L);
    }

    @Bean
    @ConditionalOnMissingBean(EventEnvelopeFactory.class)
    public EventEnvelopeFactory eventEnvelopeFactory(
            ClockProvider clockProvider,
            IdGenerator idGenerator
    ) {
        return new EventEnvelopeFactory(clockProvider, idGenerator);
    }
}
