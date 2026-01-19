package com.project.curve.spring.pii.crypto;

import com.project.curve.spring.exception.PiiCryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class DefaultPiiCryptoProviderTest {

    private DefaultPiiCryptoProvider cryptoProvider;
    private String defaultKeyBase64;
    private String salt;

    @BeforeEach
    void setUp() {
        // 32바이트 AES 키 생성 (Base64 인코딩)
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) i;
        }
        defaultKeyBase64 = Base64.getEncoder().encodeToString(keyBytes);
        salt = "test-salt";
        cryptoProvider = new DefaultPiiCryptoProvider(defaultKeyBase64, salt);
    }

    @Test
    @DisplayName("문자열을 암호화하면 원본과 다른 값을 반환한다")
    void encrypt_shouldReturnDifferentValue() {
        // Given
        String plainText = "sensitive-data";

        // When
        String encrypted = cryptoProvider.encrypt(plainText, null);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(encrypted).isBase64();
    }

    @Test
    @DisplayName("같은 문자열을 여러 번 암호화하면 매번 다른 값을 반환한다 (IV가 랜덤이므로)")
    void encrypt_samePlainText_shouldReturnDifferentValues() {
        // Given
        String plainText = "sensitive-data";

        // When
        String encrypted1 = cryptoProvider.encrypt(plainText, null);
        String encrypted2 = cryptoProvider.encrypt(plainText, null);

        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("암호화된 문자열을 복호화하면 원본을 반환한다")
    void decrypt_shouldReturnOriginalValue() {
        // Given
        String plainText = "sensitive-data";
        String encrypted = cryptoProvider.encrypt(plainText, null);

        // When
        String decrypted = cryptoProvider.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("null을 암호화하면 null을 반환한다")
    void encrypt_withNull_shouldReturnNull() {
        // When
        String encrypted = cryptoProvider.encrypt(null, null);

        // Then
        assertThat(encrypted).isNull();
    }

    @Test
    @DisplayName("null을 복호화하면 null을 반환한다")
    void decrypt_withNull_shouldReturnNull() {
        // When
        String decrypted = cryptoProvider.decrypt(null, null);

        // Then
        assertThat(decrypted).isNull();
    }

    @Test
    @DisplayName("잘못된 암호문을 복호화하면 예외가 발생한다")
    void decrypt_withInvalidCiphertext_shouldThrowException() {
        // Given
        String invalidCiphertext = "invalid-base64-!!!";

        // When & Then
        assertThatThrownBy(() -> cryptoProvider.decrypt(invalidCiphertext, null))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("복호화 실패");
    }

    @Test
    @DisplayName("해싱은 항상 같은 결과를 반환한다")
    void hash_sameValue_shouldReturnSameHash() {
        // Given
        String value = "test@example.com";

        // When
        String hash1 = cryptoProvider.hash(value);
        String hash2 = cryptoProvider.hash(value);

        // Then
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(value);
    }

    @Test
    @DisplayName("다른 값을 해싱하면 다른 결과를 반환한다")
    void hash_differentValues_shouldReturnDifferentHashes() {
        // Given
        String value1 = "test1@example.com";
        String value2 = "test2@example.com";

        // When
        String hash1 = cryptoProvider.hash(value1);
        String hash2 = cryptoProvider.hash(value2);

        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("null을 해싱하면 null을 반환한다")
    void hash_withNull_shouldReturnNull() {
        // When
        String hash = cryptoProvider.hash(null);

        // Then
        assertThat(hash).isNull();
    }

    @Test
    @DisplayName("추가 키를 등록하고 사용할 수 있다")
    void registerKey_shouldAllowEncryptionWithCustomKey() {
        // Given
        byte[] customKeyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            customKeyBytes[i] = (byte) (i + 100);
        }
        String customKeyBase64 = Base64.getEncoder().encodeToString(customKeyBytes);
        cryptoProvider.registerKey("custom-key", customKeyBase64);
        String plainText = "sensitive-data";

        // When
        String encryptedWithCustomKey = cryptoProvider.encrypt(plainText, "custom-key");
        String decryptedWithCustomKey = cryptoProvider.decrypt(encryptedWithCustomKey, "custom-key");

        // Then
        assertThat(decryptedWithCustomKey).isEqualTo(plainText);
    }

    @Test
    @DisplayName("다른 키로 암호화된 데이터는 다른 키로 복호화할 수 없다")
    void decrypt_withDifferentKey_shouldFail() {
        // Given
        byte[] customKeyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            customKeyBytes[i] = (byte) (i + 100);
        }
        String customKeyBase64 = Base64.getEncoder().encodeToString(customKeyBytes);
        cryptoProvider.registerKey("custom-key", customKeyBase64);
        String plainText = "sensitive-data";
        String encryptedWithDefaultKey = cryptoProvider.encrypt(plainText, null);

        // When & Then: 다른 키로 복호화 시도
        assertThatThrownBy(() -> cryptoProvider.decrypt(encryptedWithDefaultKey, "custom-key"))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("복호화 실패");
    }

    @Test
    @DisplayName("빈 문자열을 암호화하고 복호화할 수 있다")
    void encrypt_decrypt_emptyString_shouldWork() {
        // Given
        String emptyString = "";

        // When
        String encrypted = cryptoProvider.encrypt(emptyString, null);
        String decrypted = cryptoProvider.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(emptyString);
    }

    @Test
    @DisplayName("긴 문자열을 암호화하고 복호화할 수 있다")
    void encrypt_decrypt_longString_shouldWork() {
        // Given
        String longString = "a".repeat(10000);

        // When
        String encrypted = cryptoProvider.encrypt(longString, null);
        String decrypted = cryptoProvider.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(longString);
    }

    @Test
    @DisplayName("특수 문자를 포함한 문자열을 암호화하고 복호화할 수 있다")
    void encrypt_decrypt_specialCharacters_shouldWork() {
        // Given
        String specialString = "!@#$%^&*()_+-=[]{}|;':\",./<>?\\`~한글テスト中文";

        // When
        String encrypted = cryptoProvider.encrypt(specialString, null);
        String decrypted = cryptoProvider.decrypt(encrypted, null);

        // Then
        assertThat(decrypted).isEqualTo(specialString);
    }
}
