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

    @Test
    @DisplayName("키가 설정되면 암호화가 활성화된다")
    void isEncryptionEnabled_withKey_shouldReturnTrue() {
        // Given & When & Then
        assertThat(cryptoProvider.isEncryptionEnabled()).isTrue();
    }

    @Test
    @DisplayName("키가 null이면 암호화가 비활성화된다")
    void isEncryptionEnabled_withNullKey_shouldReturnFalse() {
        // Given
        DefaultPiiCryptoProvider providerWithoutKey = new DefaultPiiCryptoProvider(null, salt);

        // When & Then
        assertThat(providerWithoutKey.isEncryptionEnabled()).isFalse();
    }

    @Test
    @DisplayName("키가 빈 문자열이면 암호화가 비활성화된다")
    void isEncryptionEnabled_withEmptyKey_shouldReturnFalse() {
        // Given
        DefaultPiiCryptoProvider providerWithEmptyKey = new DefaultPiiCryptoProvider("", salt);

        // When & Then
        assertThat(providerWithEmptyKey.isEncryptionEnabled()).isFalse();
    }

    @Test
    @DisplayName("키가 공백만 있으면 암호화가 비활성화된다")
    void isEncryptionEnabled_withBlankKey_shouldReturnFalse() {
        // Given
        DefaultPiiCryptoProvider providerWithBlankKey = new DefaultPiiCryptoProvider("   ", salt);

        // When & Then
        assertThat(providerWithBlankKey.isEncryptionEnabled()).isFalse();
    }

    @Test
    @DisplayName("암호화가 비활성화된 상태에서 encrypt 호출 시 예외가 발생한다")
    void encrypt_withoutKey_shouldThrowException() {
        // Given
        DefaultPiiCryptoProvider providerWithoutKey = new DefaultPiiCryptoProvider(null, salt);
        String plainText = "sensitive-data";

        // When & Then
        assertThatThrownBy(() -> providerWithoutKey.encrypt(plainText, null))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("PII 암호화가 비활성화되어 있습니다")
                .hasMessageContaining("curve.pii.crypto.default-key");
    }

    @Test
    @DisplayName("암호화가 비활성화된 상태에서 decrypt 호출 시 예외가 발생한다")
    void decrypt_withoutKey_shouldThrowException() {
        // Given
        DefaultPiiCryptoProvider providerWithoutKey = new DefaultPiiCryptoProvider(null, salt);
        String encrypted = cryptoProvider.encrypt("test", null);

        // When & Then
        assertThatThrownBy(() -> providerWithoutKey.decrypt(encrypted, null))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("PII 암호화가 비활성화되어 있습니다");
    }

    @Test
    @DisplayName("암호화가 비활성화된 상태에서도 해싱은 가능하다")
    void hash_withoutKey_shouldWork() {
        // Given
        DefaultPiiCryptoProvider providerWithoutKey = new DefaultPiiCryptoProvider(null, salt);
        String value = "test@example.com";

        // When
        String hash = providerWithoutKey.hash(value);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).isNotEqualTo(value);
    }

    @Test
    @DisplayName("잘못된 Base64 형식의 키로 초기화하면 예외가 발생한다")
    void constructor_withInvalidBase64Key_shouldThrowException() {
        // Given
        String invalidBase64Key = "not-valid-base64!!!";

        // When & Then
        assertThatThrownBy(() -> new DefaultPiiCryptoProvider(invalidBase64Key, salt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잘못된 Base64 형식");
    }

    @Test
    @DisplayName("빈 키로 registerKey를 호출하면 예외가 발생한다")
    void registerKey_withEmptyKey_shouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> cryptoProvider.registerKey("alias", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null이거나 비어있을 수 없습니다");
    }

    @Test
    @DisplayName("null 키로 registerKey를 호출하면 예외가 발생한다")
    void registerKey_withNullKey_shouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> cryptoProvider.registerKey("alias", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null이거나 비어있을 수 없습니다");
    }
}
