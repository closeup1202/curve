package com.project.curve.core.key;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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

    @Test
    @DisplayName("equals and hashCode work correctly with array content")
    void equalsAndHashCode_workCorrectly() {
        // given
        byte[] plain1 = {1, 2, 3};
        byte[] plain2 = {1, 2, 3}; // Same content, different reference
        byte[] enc1 = {4, 5, 6};
        byte[] enc2 = {4, 5, 6}; // Same content, different reference

        // when
        EnvelopeDataKey key1 = new EnvelopeDataKey(plain1, enc1);
        EnvelopeDataKey key2 = new EnvelopeDataKey(plain2, enc2);
        EnvelopeDataKey key3 = new EnvelopeDataKey(plain1, new byte[]{7, 8, 9}); // Different content

        // then
        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        assertThat(key1).isNotEqualTo(key3);
    }

    @Test
    @DisplayName("toString masks plaintext key and shows encrypted key")
    void toString_masksPlaintextKey() {
        // given
        byte[] plain = {1, 2, 3};
        byte[] enc = {4, 5, 6};
        EnvelopeDataKey key = new EnvelopeDataKey(plain, enc);

        // when
        String result = key.toString();

        // then
        assertThat(result).contains("[PROTECTED]");
        assertThat(result).contains(Arrays.toString(enc));
        assertThat(result).doesNotContain(Arrays.toString(plain));
    }
}
