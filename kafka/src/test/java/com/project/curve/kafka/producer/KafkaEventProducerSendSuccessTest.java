package com.project.curve.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.curve.core.envelope.*;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KafkaEventProducer sendSuccess 테스트
 * - EventEnvelope 생성 및 JSON 직렬화 테스트
 * - 실제 Kafka 연결 없이 직렬화 로직만 검증
 */
class KafkaEventProducerSendSuccessTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // ObjectMapper 설정
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 테스트: 이벤트 전송 성공 시나리오
     * - EventEnvelope이 올바르게 생성되는지 검증
     * - JSON 직렬화가 정상적으로 동작하는지 검증
     * - 모든 필수 필드가 포함되어 있는지 검증
     */
    @Test
    void sendSuccess() throws Exception {
        // Given: 테스트용 이벤트 생성
        TestEventPayload payload = new TestEventPayload("test-order-123", "Test Product", 100);

        EventId eventId = EventId.of("test-event-123");
        EventType eventType = payload.getEventType();
        EventSource source = new EventSource("test-service", "test", "instance-1", "localhost", "1.0.0");
        EventActor actor = new EventActor("test-user", "ROLE_USER", "127.0.0.1");
        EventTrace trace = new EventTrace(null, null, null);
        EventSchema schema = EventSchema.of("TestEvent", 1);
        Map<String, String> tags = Collections.emptyMap();
        EventMetadata metadata = new EventMetadata(source, actor, trace, schema, tags);

        Instant occurredAt = Instant.now();
        Instant publishedAt = occurredAt.plusMillis(100);

        // When: EventEnvelope 생성
        EventEnvelope<TestEventPayload> envelope = EventEnvelope.of(
                eventId,
                eventType,
                EventSeverity.INFO,
                metadata,
                payload,
                occurredAt,
                publishedAt
        );

        // Then: EventEnvelope 필드 검증
        assertNotNull(envelope, "EventEnvelope should not be null");
        assertNotNull(envelope.eventId(), "eventId should not be null");
        assertNotNull(envelope.eventType(), "eventType should not be null");
        assertEquals(EventSeverity.INFO, envelope.severity(), "severity should be INFO");
        assertNotNull(envelope.metadata(), "metadata should not be null");
        assertNotNull(envelope.payload(), "payload should not be null");
        assertNotNull(envelope.occurredAt(), "occurredAt should not be null");
        assertNotNull(envelope.publishedAt(), "publishedAt should not be null");

        // EventId 검증
        String eventIdValue = envelope.eventId().value();
        assertNotNull(eventIdValue, "eventId value should not be null");
        assertFalse(eventIdValue.isEmpty(), "eventId value should not be empty");
        System.out.println("✅ EventId validated: " + eventIdValue);

        // EventType 검증
        assertEquals("TEST_ORDER_CREATED", envelope.eventType().getValue(), "eventType should match");

        // Metadata 검증
        EventMetadata envelopeMetadata = envelope.metadata();
        assertEquals("test-service", envelopeMetadata.source().service());
        assertEquals("test", envelopeMetadata.source().environment());
        assertEquals("test-user", envelopeMetadata.actor().id());
        assertEquals("ROLE_USER", envelopeMetadata.actor().role());
        assertEquals("127.0.0.1", envelopeMetadata.actor().ip());
        System.out.println("✅ Metadata validated");

        // Payload 검증
        TestEventPayload envelopePayload = envelope.payload();
        assertEquals("test-order-123", envelopePayload.orderId());
        assertEquals("Test Product", envelopePayload.productName());
        assertEquals(100, envelopePayload.amount());
        System.out.println("✅ Payload validated");

        // JSON 직렬화 테스트
        String json = objectMapper.writeValueAsString(envelope);
        assertNotNull(json, "JSON should not be null");
        assertFalse(json.isEmpty(), "JSON should not be empty");
        assertTrue(json.contains("test-order-123"), "JSON should contain orderId");
        assertTrue(json.contains("Test Product"), "JSON should contain productName");
        assertTrue(json.contains("INFO"), "JSON should contain severity");
        System.out.println("✅ JSON serialization successful");

        // JSON 역직렬화 테스트
        @SuppressWarnings("unchecked")
        Map<String, Object> envelopeMap = objectMapper.readValue(json, Map.class);
        assertNotNull(envelopeMap.get("eventId"), "JSON should have eventId");
        assertNotNull(envelopeMap.get("eventType"), "JSON should have eventType");
        assertEquals("INFO", envelopeMap.get("severity"), "JSON severity should match");
        assertNotNull(envelopeMap.get("metadata"), "JSON should have metadata");
        assertNotNull(envelopeMap.get("payload"), "JSON should have payload");
        assertNotNull(envelopeMap.get("occurredAt"), "JSON should have occurredAt");
        assertNotNull(envelopeMap.get("publishedAt"), "JSON should have publishedAt");

        // Payload 내용 검증
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String, Object>) envelopeMap.get("payload");
        assertEquals("test-order-123", payloadMap.get("orderId"));
        assertEquals("Test Product", payloadMap.get("productName"));
        assertEquals(100, payloadMap.get("amount"));
        System.out.println("✅ JSON deserialization successful");

        System.out.println("\n✅ ✅ ✅ All tests passed! ✅ ✅ ✅");
        System.out.println("Event ID: " + eventIdValue);
        System.out.println("JSON Length: " + json.length() + " bytes");
    }
}
