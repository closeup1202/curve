package com.project.curve.autoconfigure.envelope;

import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.spring.generator.SnowflakeIdGenerator;
import com.project.curve.spring.provider.UtcClockProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CurveEnvelopeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ClockProvider.class)
    public ClockProvider clockProvider() {
        return new UtcClockProvider();
    }

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator idGenerator(CurveProperties properties) {
        var idGeneratorConfig = properties.getIdGenerator();

        if (idGeneratorConfig.isAutoGenerate()) {
            log.info("Creating SnowflakeIdGenerator with auto-generated worker ID");
            return SnowflakeIdGenerator.createWithAutoWorkerId();
        } else {
            long workerId = idGeneratorConfig.getWorkerId();
            log.info("Creating SnowflakeIdGenerator with configured worker ID: {}", workerId);
            return new SnowflakeIdGenerator(workerId);
        }
    }

    @Bean
    @ConditionalOnMissingBean(EventEnvelopeFactory.class)
    public EventEnvelopeFactory eventEnvelopeFactory(ClockProvider clockProvider, IdGenerator idGenerator) {
        return new EventEnvelopeFactory(clockProvider, idGenerator);
    }
}
