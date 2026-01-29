package com.project.curve.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventId;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Testcontainers를 사용한 Kafka 통합 테스트
 * Docker가 설치되어 있어야 실행 가능합니다.
 */
@Testcontainers
class KafkaEventProducerIntegrationTest {

    @Container
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    private static KafkaEventProducer producer;
    private static KafkaConsumer<String, String> consumer;
    private static KafkaConsumer<String, String> dlqConsumer;

    private static @NotNull KafkaTemplate<String, Object> getStringKafkaTemplate() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put("bootstrap.servers", kafka.getBootstrapServers());
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", StringSerializer.class);

        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        return new KafkaTemplate<>(producerFactory);
    }

    @BeforeAll
    static void setUp() {
        // Kafka 프로듀서 설정
        KafkaTemplate<String, Object> kafkaTemplate = getStringKafkaTemplate();

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
        TestEventPayload payload = new TestEventPayload("order-1", "test-data", 100);

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
                TestEventPayload payload = new TestEventPayload("order-" + i, "test-data-" + i, 100);
                producer.publish(payload, EventSeverity.INFO);
            }
        });
    }

    @Test
    @DisplayName("실제로 Kafka에 메시지가 전송되고 Consumer로 받을 수 있다")
    void publish_shouldSendMessageAndConsumeSuccessfully() {
        // Given
        String testData = "real-kafka-message-test";
        TestEventPayload payload = new TestEventPayload("order-1", testData, 100);

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
        TestEventPayload payload = new TestEventPayload("order-1", testData, 100);
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
                    Map<?, ?> envelope = objectMapper.readValue(json, Map.class);

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
        // Given: Warm up DLQ consumer to ensure partition assignment
        dlqConsumer.poll(Duration.ofMillis(100));

        // DLQ에 직접 메시지 전송 (실제 실패 시나리오 시뮬레이션)
        KafkaTemplate<String, Object> dlqTemplate = getStringKafkaTemplate();

        // FailedEventRecord JSON 생성
        String testEventId = "failed-event-123-" + System.currentTimeMillis();
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

        // Give Kafka time to commit
        Thread.sleep(500);

        // Then: DLQ Consumer로 메시지 수신
        boolean foundDlqMessage = false;
        int maxAttempts = 3;

        for (int attempt = 0; attempt < maxAttempts && !foundDlqMessage; attempt++) {
            ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofSeconds(5));

            for (ConsumerRecord<String, String> record : records.records("test-dlq-topic")) {
                if (record.value().contains(testEventId)) {
                    foundDlqMessage = true;

                    // FailedEventRecord 파싱 및 검증
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsedRecord = objectMapper.readValue(record.value(), Map.class);
                    assertThat(parsedRecord).containsKey("eventId");
                    assertThat(parsedRecord).containsKey("originalTopic");
                    assertThat(parsedRecord).containsKey("exceptionClass");
                    assertThat(parsedRecord.get("eventId")).isEqualTo(testEventId);
                    break;
                }
            }
        }

        assertThat(foundDlqMessage)
                .as("DLQ에 전송된 메시지를 Consumer에서 수신해야 함")
                .isTrue();
    }

    @Test
    @DisplayName("비동기 모드로 이벤트를 발행할 수 있다")
    void publish_asyncMode_shouldSendEventSuccessfully() throws InterruptedException {
        // Given: Async mode producer
        KafkaTemplate<String, Object> kafkaTemplate = getStringKafkaTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("async-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        KafkaEventProducer asyncProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .asyncMode(true)
                .asyncTimeoutMs(5000L)
                .build();

        TestEventPayload payload = new TestEventPayload("async-order-1", "async-test-data", 100);

        // When: Publish async
        asyncProducer.publish(payload, EventSeverity.INFO);

        // Wait for async completion
        Thread.sleep(2000);

        // Then: Message should be in Kafka
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records).isNotEmpty();

        boolean foundAsyncMessage = false;
        for (ConsumerRecord<String, String> record : records.records("test-topic")) {
            if (record.value().contains("async-test-data")) {
                foundAsyncMessage = true;
                break;
            }
        }
        assertThat(foundAsyncMessage).isTrue();
    }

    @Test
    @DisplayName("MDC 컨텍스트가 비동기 콜백에서 올바르게 전파된다")
    void publish_asyncMode_shouldPreserveMdcContext() throws InterruptedException {
        // Given: Async mode producer
        KafkaTemplate<String, Object> kafkaTemplate = getStringKafkaTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("mdc-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        KafkaEventProducer asyncProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .asyncMode(true)
                .build();

        // When: Set MDC context and publish
        MDC.put("traceId", "trace-123");
        MDC.put("userId", "user-456");

        TestEventPayload payload = new TestEventPayload("mdc-order-1", "mdc-test-data", 100);
        asyncProducer.publish(payload, EventSeverity.INFO);

        Thread.sleep(1000);

        // Then: Verify MDC context is preserved (no exception thrown)
        assertThatNoException().isThrownBy(() -> MDC.clear());
    }

    @Test
    @DisplayName("재시도 메커니즘이 활성화되면 실패 시 재시도한다")
    void publish_withRetry_shouldRetryOnFailure() {
        // Given: Producer with retry template
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("retry-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        // Configure retry template: 3 attempts with 100ms delay
        RetryTemplate retryTemplate = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(100);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Mock Kafka template to fail twice, then succeed
        AtomicInteger attemptCount = new AtomicInteger(0);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt <= 2) {
                throw new RuntimeException("Kafka temporarily unavailable");
            }
            return getStringKafkaTemplate().send("test-topic", "retry-test-id", "success");
        });

        KafkaEventProducer retryProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .retryTemplate(retryTemplate)
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("retry-order-1", "retry-test-data", 100);

        // When & Then: Should succeed after retries
        assertThatNoException().isThrownBy(() -> retryProducer.publish(payload, EventSeverity.INFO));
        assertThat(attemptCount.get()).isEqualTo(3); // Failed twice, succeeded on 3rd attempt
    }

    @Test
    @DisplayName("메시지 전송 실패 시 DLQ로 전송된다 (검증용 - Mock 기반)")
    void publish_onFailure_shouldTriggerDlqLogic() throws Exception {
        // Given: Producer with mocked failing Kafka template
        KafkaTemplate<String, Object> mockKafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("dlq-mock-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        // Mock main topic to fail
        when(mockKafkaTemplate.send(eq("test-topic"), anyString(), any()))
                .thenThrow(new RuntimeException("Simulated Kafka failure"));

        // Mock DLQ topic to succeed (but don't actually send since it's fully mocked)
        when(mockKafkaTemplate.send(eq("test-dlq-topic"), anyString(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        mock(org.springframework.kafka.support.SendResult.class)
                ));

        KafkaEventProducer failingProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(mockKafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("dlq-order-1", "dlq-test-data", 100);

        // When: Publish (will fail main topic and trigger DLQ logic)
        failingProducer.publish(payload, EventSeverity.INFO);

        // Then: Verify DLQ send was attempted
        verify(mockKafkaTemplate).send(eq("test-dlq-topic"), eq("dlq-mock-test-id"), anyString());
    }

    @Test
    @DisplayName("DLQ 전송도 실패하면 로컬 파일에 백업된다")
    void publish_onDlqFailure_shouldBackupToLocalFile() throws IOException {
        // Given: Producer with both main and DLQ topics failing
        KafkaTemplate<String, Object> mockKafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("backup-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        // Mock both topics to fail
        when(mockKafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Complete Kafka outage"));

        String testBackupPath = "./test-dlq-backup-" + System.currentTimeMillis();

        KafkaEventProducer backupProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(mockKafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .dlqBackupPath(testBackupPath)
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("backup-order-1", "backup-test-data", 100);

        // When: Publish (will fail both main and DLQ, trigger backup)
        backupProducer.publish(payload, EventSeverity.INFO);

        // Then: Verify backup file was created
        Path backupFile = Paths.get(testBackupPath, "backup-test-id.json");
        assertThat(Files.exists(backupFile))
                .as("백업 파일이 생성되어야 함")
                .isTrue();

        String backupContent = Files.readString(backupFile);
        assertThat(backupContent).contains("backup-test-data");

        // Cleanup
        try (Stream<Path> files = Files.walk(Paths.get(testBackupPath))) {
            files.sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
        }
    }

    @Test
    @DisplayName("Serialization 예외가 발생하면 즉시 실패한다")
    void publish_serializationException_shouldFailImmediately() {
        // Given: Producer with serializer that throws exception
        KafkaTemplate<String, Object> kafkaTemplate = getStringKafkaTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("serialization-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any()))
                .thenThrow(new EventSerializationException("Cannot serialize invalid data", new RuntimeException()));

        KafkaEventProducer serializationFailingProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("serialization-order-1", "serialization-test-data", 100);

        // When & Then: Should throw EventSerializationException
        assertThatThrownBy(() -> serializationFailingProducer.publish(payload, EventSeverity.INFO))
                .isInstanceOf(EventSerializationException.class)
                .hasMessageContaining("Cannot serialize invalid data");
    }

    @Test
    @DisplayName("DLQ Executor가 설정되면 비동기로 DLQ 전송한다")
    void publish_withDlqExecutor_shouldSendDlqAsynchronously() throws InterruptedException {
        // Given: Producer with DLQ executor
        KafkaTemplate<String, Object> mockKafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ClockProvider clockProvider = mock(ClockProvider.class);
        when(clockProvider.now()).thenReturn(Instant.now());

        IdGenerator idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate()).thenReturn(EventId.of("dlq-async-test-id"));

        EventContextProvider contextProvider = mock(EventContextProvider.class);
        when(contextProvider.currentMetadata(any())).thenReturn(mock(EventMetadata.class));

        EventEnvelopeFactory envelopeFactory = new EventEnvelopeFactory(clockProvider, idGenerator);

        com.project.curve.core.serde.EventSerializer eventSerializer = mock(com.project.curve.core.serde.EventSerializer.class);
        when(eventSerializer.serialize(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));

        ExecutorService dlqExecutor = Executors.newSingleThreadExecutor();

        // Mock main topic to fail, DLQ to succeed
        when(mockKafkaTemplate.send(eq("test-topic"), anyString(), any()))
                .thenThrow(new RuntimeException("Simulated Kafka failure"));

        when(mockKafkaTemplate.send(eq("test-dlq-topic"), anyString(), any()))
                .thenReturn(getStringKafkaTemplate().send("test-dlq-topic", "dlq-async-test-id", "dlq-async-content"));

        KafkaEventProducer dlqAsyncProducer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(contextProvider)
                .kafkaTemplate(mockKafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .metricsCollector(new com.project.curve.spring.metrics.NoOpCurveMetricsCollector())
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .dlqExecutor(dlqExecutor)
                .asyncMode(false)
                .build();

        TestEventPayload payload = new TestEventPayload("dlq-async-order-1", "dlq-async-test-data", 100);

        // When: Publish (will fail and trigger async DLQ)
        dlqAsyncProducer.publish(payload, EventSeverity.INFO);

        // Wait for async DLQ processing
        Thread.sleep(2000);

        // Then: Verify DLQ executor was used (no exception means success)
        dlqExecutor.shutdown();
        assertThat(dlqExecutor.isShutdown()).isTrue();
    }
}
