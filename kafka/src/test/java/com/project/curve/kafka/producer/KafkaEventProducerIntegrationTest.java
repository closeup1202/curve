package com.project.curve.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventId;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testcontainers를 사용한 Kafka 통합 테스트
 * Docker가 설치되어 있어야 실행 가능합니다.
 */
@Testcontainers
class KafkaEventProducerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    private static KafkaEventProducer producer;

    @BeforeAll
    static void setUp() {
        // Kafka 프로듀서 설정
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put("bootstrap.servers", kafka.getBootstrapServers());
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        DefaultKafkaProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // ObjectMapper 설정
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Mock dependencies
        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("test-id-123"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.provide()).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        // KafkaEventProducer 생성
        producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .asyncMode(false)
                .build();
    }

    @AfterAll
    static void tearDown() {
        if (kafka != null) {
            kafka.stop();
        }
    }

    @Test
    @DisplayName("Kafka 컨테이너가 정상적으로 시작되었는지 확인")
    void kafka_shouldBeRunning() {
        // Then
        assertThat(kafka.isRunning()).isTrue();
        assertThat(kafka.getBootstrapServers()).isNotEmpty();
    }

    @Test
    @DisplayName("이벤트를 Kafka에 발행할 수 있다")
    void publish_shouldSendEventToKafka() {
        // Given
        TestEventPayload payload = new TestEventPayload("test-data");

        // When & Then
        assertThatNoException().isThrownBy(() ->
                producer.publish(payload, EventSeverity.INFO)
        );
    }

    @Test
    @DisplayName("여러 이벤트를 연속으로 발행할 수 있다")
    void publish_multipleEvents_shouldSucceed() {
        // Given
        int eventCount = 10;

        // When & Then
        assertThatNoException().isThrownBy(() -> {
            for (int i = 0; i < eventCount; i++) {
                TestEventPayload payload = new TestEventPayload("test-data-" + i);
                producer.publish(payload, EventSeverity.INFO);
            }
        });
    }

    // Test payload
    private record TestEventPayload(String data) implements DomainEventPayload {
    }
}
