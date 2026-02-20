package com.project.curve.autoconfigure.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper customization for Curve.
 *
 * <p>Registers {@link JavaTimeModule} as a Spring bean so that Spring Boot's
 * {@code StandardJackson2ObjectMapperBuilderCustomizer} always picks it up via
 * {@code configureModules()}. This is necessary because multiple customizers
 * each calling {@code builder.modules(...)} would overwrite each other (the method
 * replaces the internal module map rather than appending to it).
 *
 * <p>Also disables {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} so that
 * {@code java.time} types are serialized as ISO-8601 strings.
 */
@Slf4j
@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class CurveJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JavaTimeModule.class)
    public JavaTimeModule javaTimeModule() {
        return new JavaTimeModule();
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer curveJacksonCustomizer() {
        return builder -> {
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            log.debug("Curve Jackson customizer applied: WRITE_DATES_AS_TIMESTAMPS disabled");
        };
    }
}
