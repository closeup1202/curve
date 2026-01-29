package com.project.curve.spring.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventPublishException 테스트")
class EventPublishExceptionTest {

    @Test
    @DisplayName("메시지로 예외 생성")
    void createExceptionWithMessage() {
        // given
        String message = "Failed to publish event";

        // when
        EventPublishException exception = new EventPublishException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    @DisplayName("메시지와 cause로 예외 생성")
    void createExceptionWithMessageAndCause() {
        // given
        String message = "Failed to publish event";
        Throwable cause = new RuntimeException("Kafka connection failed");

        // when
        EventPublishException exception = new EventPublishException(message, cause);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    @DisplayName("RuntimeException을 상속함")
    void extendsRuntimeException() {
        // given
        EventPublishException exception = new EventPublishException("test");

        // then
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    @DisplayName("null 메시지로 예외 생성")
    void createExceptionWithNullMessage() {
        // when
        EventPublishException exception = new EventPublishException(null);

        // then
        assertNotNull(exception);
        assertNull(exception.getMessage());
    }

    @Test
    @DisplayName("빈 메시지로 예외 생성")
    void createExceptionWithEmptyMessage() {
        // given
        String message = "";

        // when
        EventPublishException exception = new EventPublishException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }

    @Test
    @DisplayName("긴 메시지로 예외 생성")
    void createExceptionWithLongMessage() {
        // given
        String message = "Failed to publish event: " + "x".repeat(1000);

        // when
        EventPublishException exception = new EventPublishException(message);

        // then
        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertTrue(exception.getMessage().length() > 1000);
    }

    @Test
    @DisplayName("중첩된 예외 처리")
    void testNestedExceptions() {
        // given
        Throwable rootCause = new IllegalStateException("Invalid state");
        Throwable intermediateCause = new RuntimeException("Processing failed", rootCause);
        EventPublishException exception = new EventPublishException(
                "Failed to publish event", intermediateCause
        );

        // when
        Throwable cause = exception.getCause();
        Throwable rootCauseFound = cause.getCause();

        // then
        assertNotNull(cause);
        assertEquals(intermediateCause, cause);
        assertEquals(rootCause, rootCauseFound);
    }
}
