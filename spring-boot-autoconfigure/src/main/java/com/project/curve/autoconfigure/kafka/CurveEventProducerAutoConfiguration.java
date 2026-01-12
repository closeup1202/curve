package com.project.curve.autoconfigure.kafka;

import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.spring.context.EventContextProvider;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.core.port.EventProducer;
import com.project.curve.kafka.producer.KafkaEventProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class CurveEventProducerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventEnvelopeFactory eventEnvelopeFactory(
            ClockProvider clockProvider,
            IdGenerator idGenerator
    ) {
        return new EventEnvelopeFactory(clockProvider, idGenerator);
    }

    @Bean
    @ConditionalOnMissingBean(EventProducer.class)
    public EventProducer eventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            CurveProperties properties
    ) {
        return new KafkaEventProducer(
                envelopeFactory,
                eventContextProvider,
                kafkaTemplate,
                objectMapper,
                properties.getKafka().getTopic()
        );
    }
}
