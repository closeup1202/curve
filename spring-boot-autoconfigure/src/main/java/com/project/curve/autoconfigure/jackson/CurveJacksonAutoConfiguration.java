package com.project.curve.autoconfigure.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper customization for Curve.
 *
 * <p>Registers JavaTimeModule and disables timestamp serialization via
 * {@link Jackson2ObjectMapperBuilderCustomizer}, ensuring compatibility with
 * Spring Boot's ObjectMapper lifecycle and other customizers (e.g., PiiModule).
 */
@Slf4j
@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class CurveJacksonAutoConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer curveJacksonCustomizer() {
        return builder -> {
            builder.modules(new JavaTimeModule());
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            log.debug("Curve Jackson customizer applied: JavaTimeModule registered, WRITE_DATES_AS_TIMESTAMPS disabled");
        };
    }
}
