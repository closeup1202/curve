package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.crypto.DefaultPiiCryptoProvider;
import com.project.curve.spring.pii.crypto.PiiCryptoProvider;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncryptingPiiProcessorTest {

    private EncryptingPiiProcessor processor;
    private PiiCryptoProvider cryptoProvider;

    @BeforeEach
    void setUp() {
        // 32바이트 AES 키 생성
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) i;
        }
        String keyBase64 = Base64.getEncoder().encodeToString(keyBytes);
        cryptoProvider = new DefaultPiiCryptoProvider(keyBase64, "test-salt");
        processor = new EncryptingPiiProcessor(cryptoProvider);
    }

    @Test
    @DisplayName("지원하는 전략은 ENCRYPT이다")
    void supportedStrategy_shouldBeEncrypt() {
        // When
        PiiStrategy strategy = processor.supportedStrategy();

        // Then
        assertThat(strategy).isEqualTo(PiiStrategy.ENCRYPT);
    }

    @Test
    @DisplayName("문자열을 암호화하면 ENC() 형식으로 감싸진다")
    void process_shouldWrapWithEncPrefix() {
        // Given
        String value = "sensitive-data";
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process(value, piiField);

        // Then
        assertThat(encrypted).startsWith("ENC(");
        assertThat(encrypted).endsWith(")");
        assertThat(encrypted).isNotEqualTo(value);
    }

    @Test
    @DisplayName("암호화된 값은 원본과 달라야 한다")
    void process_shouldReturnDifferentValue() {
        // Given
        String value = "sensitive-data";
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process(value, piiField);

        // Then
        assertThat(encrypted).isNotEqualTo(value);
        assertThat(encrypted).isNotEqualTo("ENC(" + value + ")");
    }

    @Test
    @DisplayName("null을 암호화하면 null을 반환한다")
    void process_null_shouldReturnNull() {
        // Given
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process(null, piiField);

        // Then
        assertThat(encrypted).isNull();
    }

    @Test
    @DisplayName("빈 문자열을 암호화하면 빈 문자열을 반환한다")
    void process_emptyString_shouldReturnEmpty() {
        // Given
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process("", piiField);

        // Then
        assertThat(encrypted).isEmpty();
    }

    @Test
    @DisplayName("같은 값을 여러 번 암호화하면 매번 다른 결과를 반환한다")
    void process_sameValue_shouldReturnDifferentResults() {
        // Given
        String value = "sensitive-data";
        PiiField piiField = createPiiField("");

        // When
        String encrypted1 = processor.process(value, piiField);
        String encrypted2 = processor.process(value, piiField);

        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("encryptKey가 지정되면 해당 키를 사용한다")
    void process_withCustomKey_shouldUseCustomKey() {
        // Given
        String value = "sensitive-data";
        String customKeyAlias = "custom-key";
        PiiField piiField = createPiiField(customKeyAlias);

        // When
        String encrypted = processor.process(value, piiField);

        // Then
        assertThat(encrypted).startsWith("ENC(");
        assertThat(encrypted).endsWith(")");
    }

    @Test
    @DisplayName("암호화된 값을 복호화하면 원본을 얻을 수 있다")
    void process_encrypt_decrypt_roundTrip() {
        // Given
        String value = "sensitive-data";
        PiiField piiField = createPiiField("");

        // When
        String encrypted = processor.process(value, piiField);
        String encryptedValue = encrypted.substring(4, encrypted.length() - 1); // "ENC(" 제거
        String decrypted = cryptoProvider.decrypt(encryptedValue, null);

        // Then
        assertThat(decrypted).isEqualTo(value);
    }

    private PiiField createPiiField(String encryptKey) {
        PiiField piiField = mock(PiiField.class);
        when(piiField.type()).thenReturn(PiiType.CUSTOM);
        when(piiField.level()).thenReturn(MaskingLevel.STRONG);
        when(piiField.strategy()).thenReturn(PiiStrategy.ENCRYPT);
        when(piiField.encryptKey()).thenReturn(encryptKey);
        return piiField;
    }
}
