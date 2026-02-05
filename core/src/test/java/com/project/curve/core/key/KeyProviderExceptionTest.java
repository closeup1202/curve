package com.project.curve.core.key;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("KeyProviderException Test")
class KeyProviderExceptionTest {

    @Test
    @DisplayName("Creates exception with message")
    void create_withMessage() {
        // when
        KeyProviderException exception = new KeyProviderException("test error");

        // then
        assertThat(exception.getMessage()).isEqualTo("test error");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("Creates exception with message and cause")
    void create_withMessageAndCause() {
        // given
        RuntimeException cause = new RuntimeException("root cause");

        // when
        KeyProviderException exception = new KeyProviderException("test error", cause);

        // then
        assertThat(exception.getMessage()).isEqualTo("test error");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Is a RuntimeException")
    void isRuntimeException() {
        // when
        KeyProviderException exception = new KeyProviderException("test");

        // then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
