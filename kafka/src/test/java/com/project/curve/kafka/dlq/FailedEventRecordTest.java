package com.project.curve.kafka.dlq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FailedEventRecord test")
class FailedEventRecordTest {

    @Test
    @DisplayName("Create FailedEventRecord with valid parameters")
    void createValidFailedEventRecord() {
        // given
        String eventId = "evt-123";
        String originalTopic = "order.events";
        String originalPayload = "{\"orderId\":\"order-1\",\"amount\":100}";
        String exceptionType = "org.springframework.kafka.KafkaException";
        String exceptionMessage = "Failed to send message";
        long failedAt = System.currentTimeMillis();

        // when
        FailedEventRecord record = new FailedEventRecord(
                eventId,
                originalTopic,
                originalPayload,
                exceptionType,
                exceptionMessage,
                failedAt
        );

        // then
        assertNotNull(record);
        assertEquals(eventId, record.eventId());
        assertEquals(originalTopic, record.originalTopic());
        assertEquals(originalPayload, record.originalPayload());
        assertEquals(exceptionType, record.exceptionType());
        assertEquals(exceptionMessage, record.exceptionMessage());
        assertEquals(failedAt, record.failedAt());
    }

    @Test
    @DisplayName("Create FailedEventRecord with null values")
    void createFailedEventRecordWithNullValues() {
        // given
        String eventId = "evt-123";
        String originalTopic = null;
        String originalPayload = null;
        String exceptionType = "TimeoutException";
        String exceptionMessage = null;
        long failedAt = 1234567890L;

        // when
        FailedEventRecord record = new FailedEventRecord(
                eventId,
                originalTopic,
                originalPayload,
                exceptionType,
                exceptionMessage,
                failedAt
        );

        // then
        assertNotNull(record);
        assertEquals(eventId, record.eventId());
        assertNull(record.originalTopic());
        assertNull(record.originalPayload());
        assertEquals(exceptionType, record.exceptionType());
        assertNull(record.exceptionMessage());
        assertEquals(failedAt, record.failedAt());
    }

    @Test
    @DisplayName("Create FailedEventRecord with empty strings")
    void createFailedEventRecordWithEmptyStrings() {
        // given
        String eventId = "";
        String originalTopic = "";
        String originalPayload = "";
        String exceptionType = "";
        String exceptionMessage = "";
        long failedAt = 0L;

        // when
        FailedEventRecord record = new FailedEventRecord(
                eventId,
                originalTopic,
                originalPayload,
                exceptionType,
                exceptionMessage,
                failedAt
        );

        // then
        assertNotNull(record);
        assertEquals("", record.eventId());
        assertEquals("", record.originalTopic());
        assertEquals("", record.originalPayload());
        assertEquals("", record.exceptionType());
        assertEquals("", record.exceptionMessage());
        assertEquals(0L, record.failedAt());
    }

    @Test
    @DisplayName("FailedEventRecord toString test")
    void testToString() {
        // given
        FailedEventRecord record = new FailedEventRecord(
                "evt-123",
                "order.events",
                "{\"data\":\"test\"}",
                "KafkaException",
                "Send failed",
                1234567890L
        );

        // when
        String toString = record.toString();

        // then
        assertNotNull(toString);
        assertTrue(toString.contains("evt-123"));
        assertTrue(toString.contains("order.events"));
    }

    @Test
    @DisplayName("FailedEventRecord equals and hashCode test")
    void testEqualsAndHashCode() {
        // given
        FailedEventRecord record1 = new FailedEventRecord(
                "evt-123",
                "order.events",
                "{\"data\":\"test\"}",
                "KafkaException",
                "Send failed",
                1234567890L
        );

        FailedEventRecord record2 = new FailedEventRecord(
                "evt-123",
                "order.events",
                "{\"data\":\"test\"}",
                "KafkaException",
                "Send failed",
                1234567890L
        );

        FailedEventRecord record3 = new FailedEventRecord(
                "evt-456",
                "order.events",
                "{\"data\":\"test\"}",
                "KafkaException",
                "Send failed",
                1234567890L
        );

        // then
        assertEquals(record1, record2);
        assertNotEquals(record1, record3);
        assertEquals(record1.hashCode(), record2.hashCode());
    }

    @Test
    @DisplayName("FailedEventRecord - long payload handling")
    void testWithLongPayload() {
        // given
        String longPayload = "x".repeat(10000);
        FailedEventRecord record = new FailedEventRecord(
                "evt-123",
                "order.events",
                longPayload,
                "KafkaException",
                "Send failed",
                System.currentTimeMillis()
        );

        // then
        assertNotNull(record);
        assertEquals(10000, record.originalPayload().length());
    }

    @Test
    @DisplayName("FailedEventRecord - special character handling")
    void testWithSpecialCharacters() {
        // given
        String payload = "{\"message\":\"Hello\\nWorld\\t\\\"Test\\\"\"}";
        String exceptionMessage = "Error: Connection refused @ 192.168.1.1:9092";

        FailedEventRecord record = new FailedEventRecord(
                "evt-123",
                "order.events",
                payload,
                "NetworkException",
                exceptionMessage,
                System.currentTimeMillis()
        );

        // then
        assertNotNull(record);
        assertEquals(payload, record.originalPayload());
        assertEquals(exceptionMessage, record.exceptionMessage());
    }
}
