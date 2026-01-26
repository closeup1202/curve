package com.project.curve.autoconfigure.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.serde.EventSerializer;
import com.project.curve.spring.serde.AvroEventSerializer;
import com.project.curve.spring.serde.JsonEventSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CurveEventSerializerAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "curve.serde.type", havingValue = "JSON", matchIfMissing = true)
    @ConditionalOnMissingBean(EventSerializer.class)
    public EventSerializer jsonEventSerializer(ObjectMapper objectMapper) {
        log.info("Using JSON EventSerializer");
        return new JsonEventSerializer(objectMapper);
    }

    @Configuration
    @ConditionalOnClass(name = "org.apache.avro.generic.GenericRecord")
    @ConditionalOnProperty(name = "curve.serde.type", havingValue = "AVRO")
    static class AvroSerializerConfiguration {

        @Bean
        @ConditionalOnMissingBean(EventSerializer.class)
        public EventSerializer avroEventSerializer(ObjectMapper objectMapper) {
            log.info("Using Avro EventSerializer");
            return new AvroEventSerializer(objectMapper);
        }
    }
}
