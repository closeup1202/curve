package com.project.curve.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.*;
import com.project.curve.core.exception.EventSerializationException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.serde.EventSerializer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import com.project.curve.kafka.backup.EventBackupStrategy;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import com.project.curve.spring.metrics.NoOpCurveMetricsCollector;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.support.RetryTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("KafkaEventProducer Unit Test")
@ExtendWith(MockitoExtension.class)
class KafkaEventProducerUnitTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private EventSerializer eventSerializer;
    @Mock
    private EventEnvelopeFactory envelopeFactory;
    @Mock
    private EventContextProvider eventContextProvider;
    @Mock
    private EventBackupStrategy backupStrategy;

    private CurveMetricsCollector metricsCollector;
    private ObjectMapper objectMapper;

    private static final String TOPIC = "test-topic";
    private static final String DLQ_TOPIC = "test-dlq-topic";

    @BeforeEach
    void setUp() {
        metricsCollector = spy(new NoOpCurveMetricsCollector());
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Builder Validation")
    class BuilderValidation {

        @Test
        @DisplayName("Builds with minimal configuration")
        void testBuilderWithMinimalConfig() {
            KafkaEventProducer producer = createMinimalProducer();
            assertThat(producer).isNotNull();
        }

        @Test
        @DisplayName("Throws NullPointerException when required field is null")
        void testBuilderWithNullValues() {
            assertThatThrownBy(() ->
                    KafkaEventProducer.builder()
                            .envelopeFactory(null)
                            .eventContextProvider(eventContextProvider)
                            .kafkaTemplate(kafkaTemplate)
                            .eventSerializer(eventSerializer)
                            .objectMapper(objectMapper)
                            .topic(TOPIC)
                            .metricsCollector(metricsCollector)
                            .build()
            ).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Sync Send")
    class SyncSend {

        @Test
        @DisplayName("Publishes event to Kafka successfully in sync mode")
        void testSyncSendSuccess() {
            // given
            KafkaEventProducer producer = createMinimalProducer();
            TestPayload payload = new TestPayload();
            EventEnvelope<TestPayload> envelope = createTestEnvelope(payload);
            String serialized = "{\"test\":\"data\"}";

            when(eventContextProvider.currentMetadata(any())).thenReturn(envelope.metadata());
            doReturn(envelope).when(envelopeFactory).create(any(), any(), any(), any());
            when(eventSerializer.serialize(any())).thenReturn(serialized);
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(completedFuture("evt-1", TOPIC));

            // when
            producer.publish(payload);

            // then
            verify(kafkaTemplate).send(eq(TOPIC), eq("evt-1"), eq(serialized));
            verify(metricsCollector).recordEventPublished(eq("TEST_EVENT"), eq(true), anyLong());
        }

        @Test
        @DisplayName("Handles serialization error by throwing exception")
        void testSerializationError() {
            // given
            KafkaEventProducer producer = createMinimalProducer();
            TestPayload payload = new TestPayload();
            EventEnvelope<TestPayload> envelope = createTestEnvelope(payload);

            when(eventContextProvider.currentMetadata(any())).thenReturn(envelope.metadata());
            doReturn(envelope).when(envelopeFactory).create(any(), any(), any(), any());
            when(eventSerializer.serialize(any()))
                    .thenThrow(new EventSerializationException("Serialization failed"));

            // when & then
            assertThatThrownBy(() -> producer.publish(payload))
                    .isInstanceOf(EventSerializationException.class);
            verify(metricsCollector).recordKafkaError("SerializationException");
        }

        @Test
        @DisplayName("Sends to DLQ when Kafka send fails")
        void testSyncSendFailureTriggersDlq() {
            // given
            KafkaEventProducer producer = createProducerWithDlq();
            TestPayload payload = new TestPayload();
            EventEnvelope<TestPayload> envelope = createTestEnvelope(payload);
            String serialized = "{\"test\":\"data\"}";

            when(eventContextProvider.currentMetadata(any())).thenReturn(envelope.metadata());
            doReturn(envelope).when(envelopeFactory).create(any(), any(), any(), any());
            when(eventSerializer.serialize(any())).thenReturn(serialized);
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(failedFuture(new RuntimeException("Kafka down")));
            when(kafkaTemplate.send(eq(DLQ_TOPIC), anyString(), any()))
                    .thenReturn(completedFuture("evt-1", DLQ_TOPIC));

            // when
            producer.publish(payload);

            // then
            verify(kafkaTemplate).send(eq(DLQ_TOPIC), eq("evt-1"), anyString());
            verify(metricsCollector).recordDlqEvent(eq("TEST_EVENT"), anyString());
        }

        @Test
        @DisplayName("Executes backup strategy when DLQ also fails")
        void testDlqFailureTriggersBackup() {
            // given
            KafkaEventProducer producer = KafkaEventProducer.builder()
                    .envelopeFactory(envelopeFactory)
                    .eventContextProvider(eventContextProvider)
                    .kafkaTemplate(kafkaTemplate)
                    .eventSerializer(eventSerializer)
                    .objectMapper(objectMapper)
                    .topic(TOPIC)
                    .dlqTopic(DLQ_TOPIC)
                    .metricsCollector(metricsCollector)
                    .backupStrategy(backupStrategy)
                    .build();

            TestPayload payload = new TestPayload();
            EventEnvelope<TestPayload> envelope = createTestEnvelope(payload);
            String serialized = "{\"test\":\"data\"}";

            when(eventContextProvider.currentMetadata(any())).thenReturn(envelope.metadata());
            doReturn(envelope).when(envelopeFactory).create(any(), any(), any(), any());
            when(eventSerializer.serialize(any())).thenReturn(serialized);
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(failedFuture(new RuntimeException("Kafka down")));
            when(kafkaTemplate.send(eq(DLQ_TOPIC), anyString(), any()))
                    .thenReturn(failedFuture(new RuntimeException("DLQ also down")));

            // when
            producer.publish(payload);

            // then
            verify(backupStrategy).backup(eq("evt-1"), any(), any());
        }
    }

    @Nested
    @DisplayName("Retry")
    class RetryTests {

        @Test
        @DisplayName("Retries on transient failure and succeeds")
        void testRetrySuccess() {
            // given
            RetryTemplate retryTemplate = RetryTemplate.builder()
                    .maxAttempts(3)
                    .fixedBackoff(10)
                    .build();

            KafkaEventProducer producer = KafkaEventProducer.builder()
                    .envelopeFactory(envelopeFactory)
                    .eventContextProvider(eventContextProvider)
                    .kafkaTemplate(kafkaTemplate)
                    .eventSerializer(eventSerializer)
                    .objectMapper(objectMapper)
                    .topic(TOPIC)
                    .retryTemplate(retryTemplate)
                    .metricsCollector(metricsCollector)
                    .build();

            TestPayload payload = new TestPayload();
            EventEnvelope<TestPayload> envelope = createTestEnvelope(payload);
            String serialized = "{\"test\":\"data\"}";

            when(eventContextProvider.currentMetadata(any())).thenReturn(envelope.metadata());
            doReturn(envelope).when(envelopeFactory).create(any(), any(), any(), any());
            when(eventSerializer.serialize(any())).thenReturn(serialized);
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenReturn(failedFuture(new RuntimeException("Transient error")))
                    .thenReturn(completedFuture("evt-1", TOPIC));

            // when
            producer.publish(payload);

            // then
            verify(kafkaTemplate, times(2)).send(eq(TOPIC), anyString(), any());
            verify(metricsCollector).recordRetry(eq("TEST_EVENT"), eq(1), eq("in_progress"));
            verify(metricsCollector).recordEventPublished(eq("TEST_EVENT"), eq(true), anyLong());
        }
    }

    @Nested
    @DisplayName("Metrics Recording")
    class MetricsTests {

        @Test
        @DisplayName("Records error metrics on general send failure")
        void testErrorMetricsRecorded() {
            // given
            KafkaEventProducer producer = createMinimalProducer();
            TestPayload payload = new TestPayload();
            EventEnvelope<TestPayload> envelope = createTestEnvelope(payload);

            when(eventContextProvider.currentMetadata(any())).thenReturn(envelope.metadata());
            doReturn(envelope).when(envelopeFactory).create(any(), any(), any(), any());
            when(eventSerializer.serialize(any())).thenReturn("{\"test\":\"data\"}");
            when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                    .thenThrow(new RuntimeException("Connection refused"));

            // when
            producer.publish(payload);

            // then
            verify(metricsCollector).recordEventPublished(eq("TEST_EVENT"), eq(false), anyLong());
            verify(metricsCollector).recordKafkaError("RuntimeException");
        }
    }

    // --- Helper methods ---

    private KafkaEventProducer createMinimalProducer() {
        return KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic(TOPIC)
                .metricsCollector(metricsCollector)
                .build();
    }

    private KafkaEventProducer createProducerWithDlq() {
        return KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic(TOPIC)
                .dlqTopic(DLQ_TOPIC)
                .metricsCollector(metricsCollector)
                .build();
    }

    private EventEnvelope<TestPayload> createTestEnvelope(TestPayload payload) {
        return EventEnvelope.of(
                EventId.of("evt-1"),
                payload.getEventType(),
                EventSeverity.INFO,
                new EventMetadata(
                        new EventSource("test-service", "test", "inst-1", "localhost", "1.0"),
                        new EventActor("user-1", "USER", "127.0.0.1"),
                        new EventTrace("trace-1", "span-1", "corr-1"),
                        EventSchema.of("TestEvent", 1),
                        Collections.emptyMap()
                ),
                payload,
                Instant.now(),
                Instant.now()
        );
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> completedFuture(String eventId, String topic) {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, 0), 0, 0, 0, 0, 0
        );
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, eventId, "{}");
        SendResult<String, Object> result = new SendResult<>(record, metadata);
        return CompletableFuture.completedFuture(result);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> failedFuture(Throwable ex) {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    static class TestPayload implements DomainEventPayload {
        @Override
        public EventType getEventType() {
            return () -> "TEST_EVENT";
        }
    }
}
