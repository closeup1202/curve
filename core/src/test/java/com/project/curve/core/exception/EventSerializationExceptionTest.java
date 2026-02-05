package com.project.curve.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSerializationException test")
class EventSerializationExceptionTest {

    @Test
    @DisplayName("Create EventSerializationException with message")
    void createExceptionWithMessage() {
        // given
        String message = "Failed to serialize event";

        // when
        EventSerializationException exception = new EventSerializationException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("Create EventSerializationException with message and cause")
    void createExceptionWithMessageAndCause() {
        // given
        String message = "Failed to serialize event";
        Throwable cause = new RuntimeException("JSON parsing error");

        // when
        EventSerializationException exception = new EventSerializationException(
                message, cause
        );

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("Verify RuntimeException inheritance")
    void testIsRuntimeException() {
        // given
        EventSerializationException exception = new EventSerializationException(
                "Test message"
        );

        // then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("Nested exception handling test")
    void testNestedExceptions() {
        // given
        Throwable rootCause = new IllegalArgumentException("Invalid field");
        Throwable intermediateCause = new RuntimeException("Processing failed", rootCause);
        EventSerializationException exception = new EventSerializationException(
                "Failed to serialize event", intermediateCause
        );

        // when
        Throwable cause = exception.getCause();
        Throwable rootCauseFound = cause.getCause();

        // then
        assertNotNull(cause);
        assertEquals(intermediateCause, cause);
        assertEquals(rootCause, rootCauseFound);
    }

    @Test
    @DisplayName("Create exception with null message")
    void createExceptionWithNullMessage() {
        // when
        EventSerializationException exception = new EventSerializationException(null);

        // then
        assertNotNull(exception);
        assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("Create exception with empty message")
    void createExceptionWithEmptyMessage() {
        // given
        String message = "";

        // when
        EventSerializationException exception = new EventSerializationException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("Create exception with long message")
    void createExceptionWithLongMessage() {
        // given
        String message = "Failed to serialize event: " + "x".repeat(1000);

        // when
        EventSerializationException exception = new EventSerializationException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertTrue(exception.getMessage().length() > 1000);
    }

    @Test
    @DisplayName("Create exception with special characters in message")
    void createExceptionWithSpecialCharactersMessage() {
        // given
        String message = "Failed to serialize: \\n\\t\\r\"quotes\"";

        // when
        EventSerializationException exception = new EventSerializationException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }
}
