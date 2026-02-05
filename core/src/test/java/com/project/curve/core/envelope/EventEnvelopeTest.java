package com.project.curve.core.envelope;

import com.project.curve.core.exception.InvalidEventException;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import com.project.curve.core.validation.DefaultEventValidator;
import com.project.curve.core.validation.EventValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventEnvelope test")
class EventEnvelopeTest {

    EventValidator eventValidator = new DefaultEventValidator();

    @Test
    @DisplayName("Create EventEnvelope with all valid fields")
    void createValidEventEnvelope() {
        // given
        EventId eventId = EventId.of("test-event-123");
        TestEventType eventType = new TestEventType("USER_CREATED");
        EventSeverity severity = EventSeverity.INFO;
        EventMetadata metadata = createValidMetadata();
        TestPayload payload = new TestPayload("test data");
        Instant occurredAt = Instant.now();
        Instant publishedAt = occurredAt.plusSeconds(1);

        // when
        EventEnvelope<TestPayload> envelope = EventEnvelope.of(
                eventId,
                eventType,
                severity,
                metadata,
                payload,
                occurredAt,
                publishedAt
        );

        // then
        assertNotNull(envelope);
        assertEquals(eventId, envelope.eventId());
        assertEquals(eventType, envelope.eventType());
        assertEquals(severity, envelope.severity());
        assertEquals(metadata, envelope.metadata());
        assertEquals(payload, envelope.payload());
        assertEquals(occurredAt, envelope.occurredAt());
        assertEquals(publishedAt, envelope.publishedAt());
    }

    @Test
    @DisplayName("EventValidator - valid envelope passes validation")
    void validateValidEnvelope_shouldNotThrowException() {
        // given
        EventEnvelope<TestPayload> envelope = createValidEnvelope();


        // when & then
        assertDoesNotThrow(() -> eventValidator.validate(envelope));
    }

    @Test
    @DisplayName("EventValidator - null envelope fails validation")
    void validateNullEnvelope_shouldThrowException() {
        // when & then
        InvalidEventException exception = assertThrows(
                InvalidEventException.class,
                () -> eventValidator.validate(null)
        );
        assertEquals("event must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("EventEnvelope - creation fails when eventId is null")
    void createEnvelopeWithNullEventId_shouldThrowNullPointerException() {
        // when & then
        assertThrows(
                NullPointerException.class,
                () -> new EventEnvelope<>(
                        null,
                        new TestEventType("TEST"),
                        EventSeverity.INFO,
                        createValidMetadata(),
                        new TestPayload("test"),
                        Instant.now(),
                        Instant.now()
                )
        );
    }

    @Test
    @DisplayName("EventEnvelope - creation fails when eventType is null")
    void createEnvelopeWithNullEventType_shouldThrowNullPointerException() {
        // when & then
        assertThrows(
                NullPointerException.class,
                () -> new EventEnvelope<>(
                        EventId.of("test-123"),
                        null,
                        EventSeverity.INFO,
                        createValidMetadata(),
                        new TestPayload("test"),
                        Instant.now(),
                        Instant.now()
                )
        );
    }

    @Test
    @DisplayName("EventEnvelope - creation fails when metadata is null")
    void createEnvelopeWithNullMetadata_shouldThrowNullPointerException() {
        // when & then
        assertThrows(
                NullPointerException.class,
                () -> new EventEnvelope<>(
                        EventId.of("test-123"),
                        new TestEventType("TEST"),
                        EventSeverity.INFO,
                        null,
                        new TestPayload("test"),
                        Instant.now(),
                        Instant.now()
                )
        );
    }

    @Test
    @DisplayName("EventEnvelope - creation fails when payload is null")
    void createEnvelopeWithNullPayload_shouldThrowNullPointerException() {
        // when & then
        assertThrows(
                NullPointerException.class,
                () -> new EventEnvelope<>(
                        EventId.of("test-123"),
                        new TestEventType("TEST"),
                        EventSeverity.INFO,
                        createValidMetadata(),
                        null,
                        Instant.now(),
                        Instant.now()
                )
        );
    }

    @Test
    @DisplayName("EventEnvelope - creation fails when occurredAt is null")
    void createEnvelopeWithNullOccurredAt_shouldThrowNullPointerException() {
        // when & then
        assertThrows(
                NullPointerException.class,
                () -> new EventEnvelope<>(
                        EventId.of("test-123"),
                        new TestEventType("TEST"),
                        EventSeverity.INFO,
                        createValidMetadata(),
                        new TestPayload("test"),
                        null,
                        Instant.now()
                )
        );
    }

    @Test
    @DisplayName("EventEnvelope - creation fails when publishedAt is null")
    void createEnvelopeWithNullPublishedAt_shouldThrowNullPointerException() {
        // when & then
        assertThrows(
                NullPointerException.class,
                () -> new EventEnvelope<>(
                        EventId.of("test-123"),
                        new TestEventType("TEST"),
                        EventSeverity.INFO,
                        createValidMetadata(),
                        new TestPayload("test"),
                        Instant.now(),
                        null
                )
        );
    }

    @Test
    @DisplayName("EventValidator - validation fails when occurredAt is after publishedAt")
    void validateEnvelopeWithOccurredAtAfterPublishedAt_shouldThrowException() {
        // given
        Instant publishedAt = Instant.now();
        Instant occurredAt = publishedAt.plusSeconds(10);

        EventEnvelope<TestPayload> envelope = new EventEnvelope<>(
                EventId.of("test-123"),
                new TestEventType("TEST"),
                EventSeverity.INFO,
                createValidMetadata(),
                new TestPayload("test"),
                occurredAt,
                publishedAt
        );

        // when & then
        InvalidEventException exception = assertThrows(
                InvalidEventException.class,
                () -> eventValidator.validate(envelope)
        );
        assertEquals("occurredAt must be <= publishedAt", exception.getMessage());
    }

    @Test
    @DisplayName("EventValidator - validation succeeds when occurredAt equals publishedAt")
    void validateEnvelopeWithSameTimestamps_shouldNotThrowException() {
        // given
        Instant timestamp = Instant.now();

        EventEnvelope<TestPayload> envelope = new EventEnvelope<>(
                EventId.of("test-123"),
                new TestEventType("TEST"),
                EventSeverity.INFO,
                createValidMetadata(),
                new TestPayload("test"),
                timestamp,
                timestamp
        );

        // when & then
        assertDoesNotThrow(() -> eventValidator.validate(envelope));
    }

    @Test
    @DisplayName("EventEnvelope - various Severity level test")
    void createEnvelopeWithDifferentSeverityLevels() {
        // given
        EventSeverity[] severities = {
                EventSeverity.INFO,
                EventSeverity.WARN,
                EventSeverity.ERROR,
                EventSeverity.CRITICAL
        };

        // when & then
        for (EventSeverity severity : severities) {
            EventEnvelope<TestPayload> envelope = createValidEnvelopeWithSeverity(severity);
            assertNotNull(envelope);
            assertEquals(severity, envelope.severity());
            assertDoesNotThrow(() -> eventValidator.validate(envelope));
        }
    }

    @Test
    @DisplayName("EventMetadata - tags initialized as empty map when null")
    void createMetadataWithNullTags_shouldInitializeEmptyMap() {
        // given & when
        EventMetadata metadata = new EventMetadata(
                createValidSource(),
                createValidActor(),
                createValidTrace(),
                createValidSchema(),
                null
        );

        // then
        assertNotNull(metadata.tags());
        assertTrue(metadata.tags().isEmpty());
    }

    @Test
    @DisplayName("EventMetadata - tags immutability test")
    void metadataTags_shouldBeImmutable() {
        // given
        Map<String, String> mutableTags = new java.util.HashMap<>();
        mutableTags.put("key1", "value1");

        EventMetadata metadata = new EventMetadata(
                createValidSource(),
                createValidActor(),
                createValidTrace(),
                createValidSchema(),
                mutableTags
        );

        // when
        mutableTags.put("key2", "value2");

        // then
        assertEquals(1, metadata.tags().size());
        assertTrue(metadata.tags().containsKey("key1"));
        assertFalse(metadata.tags().containsKey("key2"));
    }

    // Helper methods
    private EventEnvelope<TestPayload> createValidEnvelope() {
        return EventEnvelope.of(
                EventId.of("test-event-123"),
                new TestEventType("USER_CREATED"),
                EventSeverity.INFO,
                createValidMetadata(),
                new TestPayload("test data"),
                Instant.now(),
                Instant.now().plusSeconds(1)
        );
    }

    private EventEnvelope<TestPayload> createValidEnvelopeWithSeverity(EventSeverity severity) {
        return EventEnvelope.of(
                EventId.of("test-event-123"),
                new TestEventType("TEST"),
                severity,
                createValidMetadata(),
                new TestPayload("test data"),
                Instant.now(),
                Instant.now().plusSeconds(1)
        );
    }

    private EventMetadata createValidMetadata() {
        return new EventMetadata(
                createValidSource(),
                createValidActor(),
                createValidTrace(),
                createValidSchema(),
                Map.of("environment", "test", "region", "us-west-2")
        );
    }

    private EventSource createValidSource() {
        return new EventSource(
                "test-service",
                "test",
                "instance-1",
                "localhost",
                "1.0.0"
        );
    }

    private EventActor createValidActor() {
        return new EventActor("user-123", "ROLE_USER", "127.0.0.1");
    }

    private EventTrace createValidTrace() {
        return new EventTrace("trace-123", "span-456", "correlation-789");
    }

    private EventSchema createValidSchema() {
        return EventSchema.of("TestEvent", 1);
    }

    // Test fixtures
    private record TestEventType(String name) implements EventType {
        @Override
        public String getValue() {
            return name;
        }
    }

    private record TestPayload(String data) implements DomainEventPayload {
        @Override
        public EventType getEventType() {
            return new TestEventType("TEST_PAYLOAD");
        }
    }
}
