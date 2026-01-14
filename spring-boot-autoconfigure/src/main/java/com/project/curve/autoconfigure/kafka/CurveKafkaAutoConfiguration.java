package com.project.curve.autoconfigure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.port.EventProducer;
import com.project.curve.kafka.producer.KafkaEventProducer;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class CurveKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventProducer.class)
    public EventProducer eventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            CurveProperties properties,
            @Autowired(required = false) @Qualifier("curveRetryTemplate") RetryTemplate retryTemplate
    ) {
        var kafkaConfig = properties.getKafka();
        String topic = kafkaConfig.getTopic();
        String dlqTopic = kafkaConfig.getDlqTopic();

        boolean hasDlq = dlqTopic != null && !dlqTopic.isBlank();
        boolean hasRetry = retryTemplate != null && properties.getRetry().isEnabled();

        log.info("Initializing KafkaEventProducer: topic={}, dlq={}, retry={}",
                topic,
                hasDlq ? dlqTopic : "disabled",
                hasRetry ? "enabled" : "disabled");

        return new KafkaEventProducer(
                envelopeFactory,
                eventContextProvider,
                kafkaTemplate,
                objectMapper,
                topic,
                hasDlq ? dlqTopic : null,
                hasRetry ? retryTemplate : null
        );
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> producerFactory
    ) {
        log.info("Creating default KafkaTemplate");
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @ConditionalOnMissingBean(ProducerFactory.class)
    public ProducerFactory<String, String> producerFactory(
            KafkaProperties kafkaProperties,
            CurveProperties curveProperties
    ) {
        var kafkaConfig = curveProperties.getKafka();

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.RETRIES_CONFIG, kafkaConfig.getRetries());
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, kafkaConfig.getRetryBackoffMs());
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, kafkaConfig.getRequestTimeoutMs());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        log.info("ProducerFactory configured: retries={}, retryBackoffMs={}, requestTimeoutMs={}",
                kafkaConfig.getRetries(), kafkaConfig.getRetryBackoffMs(), kafkaConfig.getRequestTimeoutMs());

        return new DefaultKafkaProducerFactory<>(props);
    }
}
