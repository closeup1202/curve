package com.project.curve.core.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClockMovedBackwardsException test")
class ClockMovedBackwardsExceptionTest {

    @Test
    @DisplayName("Create ClockMovedBackwardsException with timestamps")
    void createExceptionWithTimestamps() {
        // given
        long lastTimestamp = 1000L;
        long currentTimestamp = 500L;

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                lastTimestamp, currentTimestamp
        );

        // then
        assertNotNull(exception);
        assertEquals(lastTimestamp, exception.getLastTimestamp());
        assertEquals(currentTimestamp, exception.getCurrentTimestamp());
        assertTrue(exception.getMessage().contains("Clock moved backwards"));
        assertTrue(exception.getMessage().contains("1000"));
        assertTrue(exception.getMessage().contains("500"));
    }

    @Test
    @DisplayName("getDifferenceMs test")
    void testGetDifferenceMs() {
        // given
        long lastTimestamp = 1000L;
        long currentTimestamp = 500L;
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                lastTimestamp, currentTimestamp
        );

        // when
        long difference = exception.getDifferenceMs();

        // then
        assertEquals(500L, difference);
    }

    @Test
    @DisplayName("Create ClockMovedBackwardsException with message")
    void createExceptionWithMessage() {
        // given
        String message = "Custom error message";

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(-1, exception.getLastTimestamp());
        assertEquals(-1, exception.getCurrentTimestamp());
        assertEquals(0, exception.getDifferenceMs());
    }

    @Test
    @DisplayName("Create ClockMovedBackwardsException with message and cause")
    void createExceptionWithMessageAndCause() {
        // given
        String message = "Custom error message";
        Throwable cause = new RuntimeException("Original cause");

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                message, cause
        );

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals(-1, exception.getLastTimestamp());
        assertEquals(-1, exception.getCurrentTimestamp());
        assertEquals(0, exception.getDifferenceMs());
    }

    @Test
    @DisplayName("Large timestamp difference test")
    void testLargeTimestampDifference() {
        // given
        long lastTimestamp = System.currentTimeMillis();
        long currentTimestamp = lastTimestamp - 60000L; // 1 minute difference

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                lastTimestamp, currentTimestamp
        );

        // then
        assertEquals(60000L, exception.getDifferenceMs());
        assertTrue(exception.getMessage().contains("60000ms"));
    }

    @Test
    @DisplayName("Exception message format test")
    void testExceptionMessageFormat() {
        // given
        long lastTimestamp = 2000L;
        long currentTimestamp = 1500L;

        // when
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                lastTimestamp, currentTimestamp
        );

        // then
        String message = exception.getMessage();
        assertTrue(message.contains("Clock moved backwards"));
        assertTrue(message.contains("Refusing to generate ID"));
        assertTrue(message.contains("lastTimestamp=2000"));
        assertTrue(message.contains("currentTimestamp=1500"));
        assertTrue(message.contains("diff=500ms"));
    }

    @Test
    @DisplayName("Verify RuntimeException inheritance")
    void testIsRuntimeException() {
        // given
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                1000L, 500L
        );

        // then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("getDifferenceMs when timestamps are equal")
    void testGetDifferenceMs_sameTimestamp() {
        // given
        long timestamp = 1000L;
        ClockMovedBackwardsException exception = new ClockMovedBackwardsException(
                timestamp, timestamp
        );

        // when
        long difference = exception.getDifferenceMs();

        // then
        assertEquals(0L, difference);
    }
}
