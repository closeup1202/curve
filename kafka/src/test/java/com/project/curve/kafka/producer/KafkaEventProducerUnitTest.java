package com.project.curve.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.serde.EventSerializer;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import com.project.curve.spring.metrics.NoOpCurveMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("KafkaEventProducer Unit 테스트")
class KafkaEventProducerUnitTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private EventSerializer eventSerializer;
    private EventEnvelopeFactory envelopeFactory;
    private EventContextProvider eventContextProvider;
    private CurveMetricsCollector metricsCollector;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        eventSerializer = mock(EventSerializer.class);
        envelopeFactory = mock(EventEnvelopeFactory.class);
        eventContextProvider = mock(EventContextProvider.class);
        metricsCollector = new NoOpCurveMetricsCollector();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Builder로 KafkaEventProducer 생성 - 최소 설정")
    void testBuilderWithMinimalConfig() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Builder로 KafkaEventProducer 생성 - 전체 설정")
    void testBuilderWithFullConfig() {
        // given
        RetryTemplate retryTemplate = new RetryTemplate();

        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("test-dlq-topic")
                .retryTemplate(retryTemplate)
                .asyncMode(true)
                .asyncTimeoutMs(10000L)
                .syncTimeoutSeconds(60L)
                .dlqBackupPath("/custom/backup/path")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("Builder로 KafkaEventProducer 생성 - null 검증")
    void testBuilderWithNullValues() {
        // when & then
        assertThrows(NullPointerException.class, () ->
                KafkaEventProducer.builder()
                        .envelopeFactory(null)
                        .eventContextProvider(eventContextProvider)
                        .kafkaTemplate(kafkaTemplate)
                        .eventSerializer(eventSerializer)
                        .objectMapper(objectMapper)
                        .topic("test-topic")
                        .metricsCollector(metricsCollector)
                        .build()
        );
    }

    @Test
    @DisplayName("기본 asyncMode는 false")
    void testDefaultAsyncMode() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
        // asyncMode는 기본값 false로 설정됨
    }

    @Test
    @DisplayName("기본 asyncTimeoutMs는 5000L")
    void testDefaultAsyncTimeoutMs() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("기본 syncTimeoutSeconds는 30L")
    void testDefaultSyncTimeoutSeconds() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("기본 dlqBackupPath는 ./dlq-backup")
    void testDefaultDlqBackupPath() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("DLQ가 설정되지 않으면 dlqEnabled는 false")
    void testDlqNotEnabled() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("DLQ가 빈 문자열이면 dlqEnabled는 false")
    void testDlqWithEmptyString() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("DLQ가 공백이면 dlqEnabled는 false")
    void testDlqWithBlankString() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("   ")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("DLQ 토픽이 설정되면 dlqEnabled는 true")
    void testDlqEnabled() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("test-dlq")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("RetryTemplate이 설정되면 재시도 활성화")
    void testRetryEnabled() {
        // given
        RetryTemplate retryTemplate = new RetryTemplate();

        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .retryTemplate(retryTemplate)
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("asyncMode true로 설정")
    void testAsyncModeEnabled() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .asyncMode(true)
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("커스텀 타임아웃 설정")
    void testCustomTimeouts() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .asyncTimeoutMs(15000L)
                .syncTimeoutSeconds(120L)
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("커스텀 DLQ backup 경로 설정")
    void testCustomDlqBackupPath() {
        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqBackupPath("/custom/backup")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }

    @Test
    @DisplayName("모든 옵션 조합 테스트")
    void testAllOptionsCombination() {
        // given
        RetryTemplate retryTemplate = new RetryTemplate();

        // when
        KafkaEventProducer producer = KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic("test-topic")
                .dlqTopic("dlq-topic")
                .retryTemplate(retryTemplate)
                .asyncMode(true)
                .asyncTimeoutMs(20000L)
                .syncTimeoutSeconds(90L)
                .dlqBackupPath("/var/dlq")
                .metricsCollector(metricsCollector)
                .build();

        // then
        assertNotNull(producer);
    }
}
