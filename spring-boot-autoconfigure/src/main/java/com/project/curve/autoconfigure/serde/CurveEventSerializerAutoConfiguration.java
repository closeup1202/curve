package com.project.curve.autoconfigure.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.serde.EventSerializer;
import com.project.curve.spring.serde.JsonEventSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CurveEventSerializerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventSerializer.class)
    public EventSerializer eventSerializer(ObjectMapper objectMapper) {
        return new JsonEventSerializer(objectMapper);
    }
}
