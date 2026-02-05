package com.project.curve.core.key;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("EnvelopeDataKey Test")
class EnvelopeDataKeyTest {

    @Test
    @DisplayName("Creates EnvelopeDataKey with valid plaintext and encrypted keys")
    void create_withValidKeys() {
        // given
        byte[] plaintextKey = new byte[]{1, 2, 3, 4};
        byte[] encryptedKey = new byte[]{5, 6, 7, 8};

        // when
        EnvelopeDataKey dataKey = new EnvelopeDataKey(plaintextKey, encryptedKey);

        // then
        assertThat(dataKey.plaintextKey()).isEqualTo(plaintextKey);
        assertThat(dataKey.encryptedKey()).isEqualTo(encryptedKey);
    }

    @Test
    @DisplayName("Throws exception when plaintextKey is null")
    void create_withNullPlaintextKey_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> new EnvelopeDataKey(null, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaintextKey must not be null or empty");
    }

    @Test
    @DisplayName("Throws exception when plaintextKey is empty")
    void create_withEmptyPlaintextKey_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> new EnvelopeDataKey(new byte[0], new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plaintextKey must not be null or empty");
    }

    @Test
    @DisplayName("Throws exception when encryptedKey is null")
    void create_withNullEncryptedKey_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> new EnvelopeDataKey(new byte[]{1}, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encryptedKey must not be null or empty");
    }

    @Test
    @DisplayName("Throws exception when encryptedKey is empty")
    void create_withEmptyEncryptedKey_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> new EnvelopeDataKey(new byte[]{1}, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encryptedKey must not be null or empty");
    }
}
