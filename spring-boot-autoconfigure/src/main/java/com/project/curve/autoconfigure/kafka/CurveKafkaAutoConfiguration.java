package com.project.curve.autoconfigure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.port.*;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.kafka.producer.KafkaEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Curve Kafka 자동 구성
 *
 * - EventProducer 빈 생성 (KafkaEventProducer 구현)
 * - KafkaTemplate이 없을 경우 기본 KafkaTemplate 생성 (재시도 설정 포함)
 */
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
            CurveProperties properties
    ) {
        log.info("Initializing KafkaEventProducer with topic: {}", properties.getKafka().getTopic());
        return new KafkaEventProducer(
                envelopeFactory,
                eventContextProvider,
                kafkaTemplate,
                objectMapper,
                properties.getKafka().getTopic()
        );
    }

    /**
     * 기본 KafkaTemplate 생성 (사용자가 제공하지 않은 경우)
     * 재시도 및 타임아웃 설정 포함
     */
    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> producerFactory
    ) {
        log.info("Creating default KafkaTemplate with retry configuration");
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * ProducerFactory 생성 (재시도 설정 적용)
     */
    @Bean
    @ConditionalOnMissingBean(ProducerFactory.class)
    public ProducerFactory<String, String> producerFactory(
            KafkaProperties kafkaProperties,
            CurveProperties curveProperties
    ) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));

        // Curve 프로퍼티로 Kafka Producer 설정 오버라이드
        var kafkaConfig = curveProperties.getKafka();

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // 재시도 설정
        props.put(ProducerConfig.RETRIES_CONFIG, kafkaConfig.getRetries());
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, kafkaConfig.getRetryBackoffMs());
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, kafkaConfig.getRequestTimeoutMs());

        // 멱등성 보장 (재시도 시 중복 방지)
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Acks 설정 (all = 모든 복제본이 확인할 때까지 대기)
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // 최대 in-flight 요청 수 (멱등성 활성화 시 최대 5)
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        log.info("ProducerFactory configured with retries={}, retryBackoffMs={}, requestTimeoutMs={}",
                kafkaConfig.getRetries(), kafkaConfig.getRetryBackoffMs(), kafkaConfig.getRequestTimeoutMs());

        return new DefaultKafkaProducerFactory<>(props);
    }
}
