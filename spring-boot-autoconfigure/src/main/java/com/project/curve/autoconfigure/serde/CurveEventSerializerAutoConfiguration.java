package com.project.curve.autoconfigure.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
        ensureJavaTimeModule(objectMapper);
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
            ensureJavaTimeModule(objectMapper);
            return new AvroEventSerializer(objectMapper);
        }
    }

    /**
     * Ensures JavaTimeModule is registered on the shared ObjectMapper.
     *
     * <p>{@code Jackson2ObjectMapperBuilder.modules()} replaces its internal module map on each call,
     * so JavaTimeModule added by the Curve customizer can be silently wiped out when the PiiModule
     * customizer runs afterward. Registering the module here, at serializer construction time,
     * guarantees it is always present regardless of customizer ordering.
     */
    static void ensureJavaTimeModule(ObjectMapper objectMapper) {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.debug("JavaTimeModule ensured on ObjectMapper for Curve event serialization");
    }
}
