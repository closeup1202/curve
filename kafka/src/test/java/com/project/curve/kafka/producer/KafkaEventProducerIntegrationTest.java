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
import com.project.curve.core.type.EventType;
import com.project.curve.spring.audit.type.DefaultEventType;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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
    private static KafkaConsumer<String, String> consumer;
    private static KafkaConsumer<String, String> dlqConsumer;

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
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        // EventSerializer mock 설정
        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        // KafkaEventProducer 생성
        producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .asyncMode(false)
                .build();

        // Kafka 컨슈머 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("test-topic"));

        // DLQ 컨슈머 설정
        Map<String, Object> dlqConsumerProps = new HashMap<>();
        dlqConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        dlqConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-dlq-consumer-group");
        dlqConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dlqConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dlqConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dlqConsumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        dlqConsumer = new KafkaConsumer<>(dlqConsumerProps);
        dlqConsumer.subscribe(Collections.singletonList("test-dlq-topic"));
    }

    @AfterAll
    static void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        if (dlqConsumer != null) {
            dlqConsumer.close();
        }
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

    @Test
    @DisplayName("실제로 Kafka에 메시지가 전송되고 Consumer로 받을 수 있다")
    void publish_shouldSendMessageAndConsumeSuccessfully() {
        // Given
        String testData = "real-kafka-message-test";
        TestEventPayload payload = new TestEventPayload(testData);

        // When: 메시지 발행
        producer.publish(payload, EventSeverity.INFO);

        // Then: Consumer로 메시지 수신
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        assertThat(records.count()).isGreaterThan(0);

        // 메시지 내용 검증
        AtomicBoolean foundMessage = new AtomicBoolean(false);

        Iterable<ConsumerRecord<String, String>> recordsList = records.records("test-topic");
        recordsList.forEach(record -> {
            String value = record.value();
            foundMessage.set(value.contains(testData));
        });

        assertThat(foundMessage.get())
                .as("전송한 메시지가 Consumer에서 수신되어야 함")
                .isTrue();
    }

    @Test
    @DisplayName("발행된 메시지에 이벤트 메타데이터가 포함되어 있다")
    void publish_shouldIncludeEventMetadata() throws Exception {
        // Given
        String testData = "metadata-test";
        TestEventPayload payload = new TestEventPayload(testData);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // When: 메시지 발행
        producer.publish(payload, EventSeverity.INFO);

        // Then: Consumer로 메시지 수신 및 검증
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();

        Iterable<ConsumerRecord<String, String>> recordIterable = records.records("test-topic");

        // JSON 파싱하여 필수 필드 검증
        boolean hasValidMetadata = false;
        for (ConsumerRecord<String, String> record : recordIterable) {
            if (record.value().contains(testData)) {
                try {
                    String json = record.value();
                    Map envelope = objectMapper.readValue(json, Map.class);

                    // 필수 필드 검증
                    hasValidMetadata = envelope.containsKey("eventId") &&
                            envelope.containsKey("eventType") &&
                            envelope.containsKey("severity") &&
                            envelope.containsKey("metadata") &&
                            envelope.containsKey("payload") &&
                            envelope.containsKey("occurredAt") &&
                            envelope.containsKey("publishedAt");

                    if (hasValidMetadata) {
                        break;
                    }
                } catch (Exception e) {
                    // 파싱 실패 시 계속 진행
                }
            }
        }

        assertThat(hasValidMetadata)
                .as("메시지에 모든 필수 메타데이터가 포함되어야 함")
                .isTrue();
    }

    @Test
    @DisplayName("DLQ 토픽이 정상적으로 설정되어 있다")
    void dlq_shouldBeConfigured() {
        // Given & When: DLQ Consumer가 구독되어 있음

        // Then: DLQ Consumer가 정상 동작
        assertThat(dlqConsumer.subscription()).contains("test-dlq-topic");
    }

    @Test
    @DisplayName("DLQ Consumer가 메시지를 수신할 수 있다")
    void dlq_shouldConsumeMessages() throws Exception {
        // Given: DLQ에 직접 메시지 전송 (실제 실패 시나리오 시뮬레이션)
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put("bootstrap.servers", kafka.getBootstrapServers());
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, String> dlqTemplate = new KafkaTemplate<>(producerFactory);

        // FailedEventRecord JSON 생성
        String testEventId = "failed-event-123";
        String testPayload = "{\"eventId\":\"" + testEventId + "\",\"data\":\"test-failure\"}";
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> failedRecord = new HashMap<>();
        failedRecord.put("eventId", testEventId);
        failedRecord.put("originalTopic", "test-topic");
        failedRecord.put("originalValue", testPayload);
        failedRecord.put("exceptionClass", "org.apache.kafka.common.errors.TimeoutException");
        failedRecord.put("exceptionMessage", "Timeout after 30 seconds");
        failedRecord.put("failedAt", System.currentTimeMillis());

        String dlqMessage = objectMapper.writeValueAsString(failedRecord);

        // When: DLQ로 메시지 전송
        dlqTemplate.send("test-dlq-topic", testEventId, dlqMessage).get();

        // Then: DLQ Consumer로 메시지 수신
        ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        assertThat(records.count()).isGreaterThan(0);

        // 메시지 내용 검증
        boolean foundDlqMessage = false;
        for (ConsumerRecord<String, String> record : records.records("test-dlq-topic")) {
            if (record.value().contains(testEventId)) {
                foundDlqMessage = true;

                // FailedEventRecord 파싱 및 검증
                Map<String, Object> parsedRecord = objectMapper.readValue(record.value(), Map.class);
                assertThat(parsedRecord).containsKey("eventId");
                assertThat(parsedRecord).containsKey("originalTopic");
                assertThat(parsedRecord).containsKey("exceptionClass");
                assertThat(parsedRecord.get("eventId")).isEqualTo(testEventId);

                break;
            }
        }

        assertThat(foundDlqMessage)
                .as("DLQ에 전송된 메시지를 Consumer에서 수신해야 함")
                .isTrue();
    }

    // Test payload
    private record TestEventPayload(String data) implements DomainEventPayload {
        @Override
        public EventType getEventType() {
            return new DefaultEventType("TEST");
        }
    }
}
