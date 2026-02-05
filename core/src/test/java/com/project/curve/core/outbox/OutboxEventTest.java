package com.project.curve.core.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxEvent test")
class OutboxEventTest {

    @Test
    @DisplayName("Create OutboxEvent with valid parameters")
    void createValidOutboxEvent() {
        // given
        String eventId = "evt-123";
        String aggregateType = "Order";
        String aggregateId = "order-123";
        String eventType = "ORDER_CREATED";
        String payload = "{\"orderId\":\"order-123\"}";
        Instant occurredAt = Instant.now();

        // when
        OutboxEvent event = new OutboxEvent(
                eventId, aggregateType, aggregateId, eventType, payload, occurredAt
        );

        // then
        assertNotNull(event);
        assertEquals(eventId, event.getEventId());
        assertEquals(aggregateType, event.getAggregateType());
        assertEquals(aggregateId, event.getAggregateId());
        assertEquals(eventType, event.getEventType());
        assertEquals(payload, event.getPayload());
        assertEquals(occurredAt, event.getOccurredAt());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getRetryCount());
        assertNull(event.getPublishedAt());
        assertNull(event.getErrorMessage());
        assertNotNull(event.getNextRetryAt());
    }

    @Test
    @DisplayName("Throws exception when eventId is null")
    void createOutboxEventWithNullEventId_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent(null, "Order", "order-123", "ORDER_CREATED",
                        "{}", Instant.now())
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("Throws exception when eventId is empty string")
    void createOutboxEventWithBlankEventId_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("", "Order", "order-123", "ORDER_CREATED",
                        "{}", Instant.now())
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("Throws exception when aggregateType is null")
    void createOutboxEventWithNullAggregateType_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", null, "order-123", "ORDER_CREATED",
                        "{}", Instant.now())
        );
        assertEquals("aggregateType must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("Throws exception when aggregateId is null")
    void createOutboxEventWithNullAggregateId_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", "Order", null, "ORDER_CREATED",
                        "{}", Instant.now())
        );
        assertEquals("aggregateId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("Throws exception when eventType is null")
    void createOutboxEventWithNullEventType_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", "Order", "order-123", null,
                        "{}", Instant.now())
        );
        assertEquals("eventType must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("Throws exception when payload is null")
    void createOutboxEventWithNullPayload_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", "Order", "order-123", "ORDER_CREATED",
                        null, Instant.now())
        );
        assertEquals("payload must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("Throws exception when occurredAt is null")
    void createOutboxEventWithNullOccurredAt_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new OutboxEvent("evt-123", "Order", "order-123", "ORDER_CREATED",
                        "{}", null)
        );
        assertEquals("occurredAt must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("markAsPublished test")
    void testMarkAsPublished() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );

        // when
        event.markAsPublished();

        // then
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getErrorMessage());
        assertNull(event.getNextRetryAt());
    }

    @Test
    @DisplayName("markAsFailed test")
    void testMarkAsFailed() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        String errorMessage = "Kafka connection failed";

        // when
        event.markAsFailed(errorMessage);

        // then
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(errorMessage, event.getErrorMessage());
        assertNull(event.getNextRetryAt());
    }

    @Test
    @DisplayName("scheduleNextRetry test")
    void testScheduleNextRetry() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        long backoffMs = 5000L;

        // when
        int retryCount = event.scheduleNextRetry(backoffMs);

        // then
        assertEquals(1, retryCount);
        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getNextRetryAt());
        assertTrue(event.getNextRetryAt().isAfter(Instant.now().minusSeconds(1)));
    }

    @Test
    @DisplayName("scheduleNextRetry multiple invocations test")
    void testScheduleNextRetryMultipleTimes() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );

        // when
        event.scheduleNextRetry(1000L);
        event.scheduleNextRetry(2000L);
        int retryCount = event.scheduleNextRetry(3000L);

        // then
        assertEquals(3, retryCount);
        assertEquals(3, event.getRetryCount());
    }

    @Test
    @DisplayName("exceededMaxRetries test - not exceeded")
    void testExceededMaxRetries_notExceeded() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        event.scheduleNextRetry(1000L);
        event.scheduleNextRetry(1000L);

        // when
        boolean exceeded = event.exceededMaxRetries(3);

        // then
        assertFalse(exceeded);
    }

    @Test
    @DisplayName("exceededMaxRetries test - exceeded")
    void testExceededMaxRetries_exceeded() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        event.scheduleNextRetry(1000L);
        event.scheduleNextRetry(1000L);
        event.scheduleNextRetry(1000L);

        // when
        boolean exceeded = event.exceededMaxRetries(3);

        // then
        assertTrue(exceeded);
    }

    @Test
    @DisplayName("canPublish test - PENDING status")
    void testCanPublish_pending() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );

        // when
        boolean canPublish = event.canPublish();

        // then
        assertTrue(canPublish);
    }

    @Test
    @DisplayName("canPublish test - PUBLISHED status")
    void testCanPublish_published() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        event.markAsPublished();

        // when
        boolean canPublish = event.canPublish();

        // then
        assertFalse(canPublish);
    }

    @Test
    @DisplayName("canPublish test - FAILED status")
    void testCanPublish_failed() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );
        event.markAsFailed("Error");

        // when
        boolean canPublish = event.canPublish();

        // then
        assertFalse(canPublish);
    }

    @Test
    @DisplayName("restore method test")
    void testRestore() {
        // given
        String eventId = "evt-123";
        String aggregateType = "Order";
        String aggregateId = "order-123";
        String eventType = "ORDER_CREATED";
        String payload = "{}";
        Instant occurredAt = Instant.now();
        OutboxStatus status = OutboxStatus.PUBLISHED;
        int retryCount = 2;
        Instant publishedAt = Instant.now();
        String errorMessage = "Some error";
        Instant nextRetryAt = Instant.now().plusSeconds(10);

        // when
        OutboxEvent event = OutboxEvent.restore(
                eventId, aggregateType, aggregateId, eventType, payload,
                occurredAt, status, retryCount, publishedAt, errorMessage, nextRetryAt
        );

        // then
        assertEquals(eventId, event.getEventId());
        assertEquals(aggregateType, event.getAggregateType());
        assertEquals(aggregateId, event.getAggregateId());
        assertEquals(eventType, event.getEventType());
        assertEquals(payload, event.getPayload());
        assertEquals(occurredAt, event.getOccurredAt());
        assertEquals(status, event.getStatus());
        assertEquals(retryCount, event.getRetryCount());
        assertEquals(publishedAt, event.getPublishedAt());
        assertEquals(errorMessage, event.getErrorMessage());
        assertEquals(nextRetryAt, event.getNextRetryAt());
    }

    @Test
    @DisplayName("restore method test - nextRetryAt is null")
    void testRestore_withNullNextRetryAt() {
        // given
        Instant occurredAt = Instant.now();

        // when
        OutboxEvent event = OutboxEvent.restore(
                "evt-123", "Order", "order-123", "ORDER_CREATED", "{}",
                occurredAt, OutboxStatus.PENDING, 0, null, null, null
        );

        // then
        assertEquals(occurredAt, event.getNextRetryAt());
    }

    @Test
    @DisplayName("toString test")
    void testToString() {
        // given
        OutboxEvent event = new OutboxEvent(
                "evt-123", "Order", "order-123", "ORDER_CREATED",
                "{}", Instant.now()
        );

        // when
        String toString = event.toString();

        // then
        assertNotNull(toString);
        assertTrue(toString.contains("evt-123"));
        assertTrue(toString.contains("Order"));
        assertTrue(toString.contains("order-123"));
        assertTrue(toString.contains("ORDER_CREATED"));
        assertTrue(toString.contains("PENDING"));
    }
}
