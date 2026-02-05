package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventId test")
class EventIdTest {

    @Test
    @DisplayName("Create EventId with valid value")
    void createValidEventId() {
        // given
        String value = "event-12345";

        // when
        EventId eventId = EventId.of(value);

        // then
        assertNotNull(eventId);
        assertEquals(value, eventId.value());
    }

    @Test
    @DisplayName("EventId creation fails with null value")
    void createEventIdWithNullValue_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventId.of(null)
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventId creation fails with empty string")
    void createEventIdWithEmptyValue_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventId.of("")
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventId creation fails with blank string")
    void createEventIdWithBlankValue_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> EventId.of("   ")
        );
        assertEquals("eventId must not be blank", exception.getMessage());
    }

    @Test
    @DisplayName("EventId equality test - same value means equal objects")
    void eventIdEquality() {
        // given
        String value = "event-123";
        EventId eventId1 = EventId.of(value);
        EventId eventId2 = EventId.of(value);

        // then
        assertEquals(eventId1, eventId2);
        assertEquals(eventId1.hashCode(), eventId2.hashCode());
    }
}
