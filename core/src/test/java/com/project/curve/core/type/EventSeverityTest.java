package com.project.curve.core.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSeverity test")
class EventSeverityTest {

    @Test
    @DisplayName("EventSeverity - verify all values exist")
    void allSeverityValuesExist() {
        // when
        EventSeverity[] severities = EventSeverity.values();

        // then
        assertEquals(4, severities.length);
        assertNotNull(EventSeverity.INFO);
        assertNotNull(EventSeverity.WARN);
        assertNotNull(EventSeverity.ERROR);
        assertNotNull(EventSeverity.CRITICAL);
    }

    @Test
    @DisplayName("EventSeverity - get value using valueOf()")
    void getValueByName() {
        // when & then
        assertEquals(EventSeverity.INFO, EventSeverity.valueOf("INFO"));
        assertEquals(EventSeverity.WARN, EventSeverity.valueOf("WARN"));
        assertEquals(EventSeverity.ERROR, EventSeverity.valueOf("ERROR"));
        assertEquals(EventSeverity.CRITICAL, EventSeverity.valueOf("CRITICAL"));
    }

    @Test
    @DisplayName("EventSeverity - valueOf() throws IllegalArgumentException for invalid value")
    void getValueByInvalidName_shouldThrowException() {
        // when & then
        assertThrows(
                IllegalArgumentException.class,
                () -> EventSeverity.valueOf("INVALID")
        );
    }
}
