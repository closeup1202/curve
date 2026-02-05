package com.project.curve.core.key;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("KeyProvider Test")
class KeyProviderTest {

    @Test
    @DisplayName("Default supportsEnvelopeEncryption returns false")
    void defaultSupportsEnvelopeEncryption_returnsFalse() {
        // given
        KeyProvider provider = keyId -> "base64-key";

        // then
        assertThat(provider.supportsEnvelopeEncryption()).isFalse();
    }

    @Test
    @DisplayName("Default generateDataKey throws UnsupportedOperationException")
    void defaultGenerateDataKey_throwsException() {
        // given
        KeyProvider provider = keyId -> "base64-key";

        // when & then
        assertThatThrownBy(() -> provider.generateDataKey("key-id"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Envelope encryption not supported");
    }

    @Test
    @DisplayName("Default decryptDataKey throws UnsupportedOperationException")
    void defaultDecryptDataKey_throwsException() {
        // given
        KeyProvider provider = keyId -> "base64-key";

        // when & then
        assertThatThrownBy(() -> provider.decryptDataKey(new byte[]{1, 2, 3}))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Envelope encryption not supported");
    }

    @Test
    @DisplayName("getDataKey returns expected key")
    void getDataKey_returnsKey() {
        // given
        KeyProvider provider = keyId -> "expected-key-for-" + keyId;

        // when
        String result = provider.getDataKey("my-key");

        // then
        assertThat(result).isEqualTo("expected-key-for-my-key");
    }
}
