package com.project.curve.spring.pii.crypto.util;

import com.project.curve.spring.exception.PiiCryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AesUtil Test")
class AesUtilTest {

    private String base64Key;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) keyBytes[i] = (byte) i;
        base64Key = Base64.getEncoder().encodeToString(keyBytes);
    }

    // ── encrypt / decrypt round-trip ──────────────────────────────────────────

    @Test
    @DisplayName("Encrypts and decrypts a string with a Base64 key and returns the original value")
    void encrypt_decrypt_roundTrip_shouldReturnOriginalValue() {
        // Given
        String plainText = "sensitive-data";

        // When
        String encrypted = AesUtil.encrypt(plainText, base64Key);
        String decrypted = AesUtil.decrypt(encrypted, base64Key);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plainText);
        assertThat(encrypted).isBase64();
        assertThat(decrypted).isEqualTo(plainText);
    }

    // ── encryptToBytes / decryptFromBytes round-trip ──────────────────────────

    @Test
    @DisplayName("Encrypts to bytes and decrypts from bytes and returns the original value")
    void encryptToBytes_decryptFromBytes_roundTrip_shouldReturnOriginalValue() {
        // Given
        String plainText = "hello-bytes";

        // When
        byte[] encrypted = AesUtil.encryptToBytes(plainText, base64Key);
        String decrypted = AesUtil.decryptFromBytes(encrypted, base64Key);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted.length).isGreaterThan(12); // at least IV + some ciphertext
        assertThat(decrypted).isEqualTo(plainText);
    }

    // ── encryptWithKey / decryptWithKey round-trip ─────────────────────────────

    @Test
    @DisplayName("Encrypts and decrypts with a SecretKey directly and returns the original value")
    void encryptWithKey_decryptWithKey_roundTrip_shouldReturnOriginalValue() {
        // Given
        String plainText = "direct-key-test";
        SecretKey key = AesUtil.createKey(base64Key);

        // When
        byte[] encrypted = AesUtil.encryptWithKey(plainText, key);
        String decrypted = AesUtil.decryptWithKey(encrypted, key);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(decrypted).isEqualTo(plainText);
    }

    // ── Same plaintext produces different ciphertexts (random IV) ─────────────

    @Test
    @DisplayName("Encrypts the same string multiple times and returns different ciphertexts each time")
    void encrypt_samePlainText_shouldReturnDifferentCiphertexts() {
        // Given
        String plainText = "same-text";

        // When
        String encrypted1 = AesUtil.encrypt(plainText, base64Key);
        String encrypted2 = AesUtil.encrypt(plainText, base64Key);

        // Then
        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    // ── Null input returns null ───────────────────────────────────────────────

    @Test
    @DisplayName("Returns null when encrypt receives null plaintext")
    void encrypt_withNull_shouldReturnNull() {
        // When
        String result = AesUtil.encrypt(null, base64Key);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Returns null when encryptToBytes receives null plaintext")
    void encryptToBytes_withNull_shouldReturnNull() {
        // When
        byte[] result = AesUtil.encryptToBytes(null, base64Key);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Returns null when decrypt receives null ciphertext")
    void decrypt_withNull_shouldReturnNull() {
        // When
        String result = AesUtil.decrypt(null, base64Key);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Returns null when decryptFromBytes receives null combined bytes")
    void decryptFromBytes_withNull_shouldReturnNull() {
        // When
        String result = AesUtil.decryptFromBytes(null, base64Key);

        // Then
        assertThat(result).isNull();
    }

    // ── Blank key throws PiiCryptoException ───────────────────────────────────

    @Test
    @DisplayName("Throws PiiCryptoException when encrypt is called with a blank key")
    void encryptToBytes_withBlankKey_shouldThrowPiiCryptoException() {
        // When & Then
        assertThatThrownBy(() -> AesUtil.encryptToBytes("data", ""))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("key cannot be null or blank");
    }

    @Test
    @DisplayName("Throws PiiCryptoException when encrypt is called with a null key")
    void encryptToBytes_withNullKey_shouldThrowPiiCryptoException() {
        // When & Then
        assertThatThrownBy(() -> AesUtil.encryptToBytes("data", null))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("key cannot be null or blank");
    }

    @Test
    @DisplayName("Throws PiiCryptoException when decrypt is called with a blank key")
    void decrypt_withBlankKey_shouldThrowPiiCryptoException() {
        // Given
        String encrypted = AesUtil.encrypt("data", base64Key);

        // When & Then
        assertThatThrownBy(() -> AesUtil.decrypt(encrypted, ""))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("key cannot be null or blank");
    }

    @Test
    @DisplayName("Throws PiiCryptoException when decryptFromBytes is called with a blank key")
    void decryptFromBytes_withBlankKey_shouldThrowPiiCryptoException() {
        // Given
        byte[] encrypted = AesUtil.encryptToBytes("data", base64Key);

        // When & Then
        assertThatThrownBy(() -> AesUtil.decryptFromBytes(encrypted, "   "))
                .isInstanceOf(PiiCryptoException.class)
                .hasMessageContaining("key cannot be null or blank");
    }

    // ── Invalid Base64 key throws IllegalArgumentException ────────────────────

    @Test
    @DisplayName("Throws IllegalArgumentException when createKey receives an invalid Base64 string")
    void createKey_withInvalidBase64_shouldThrowIllegalArgumentException() {
        // When & Then
        assertThatThrownBy(() -> AesUtil.createKey("not-valid-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Base64 format");
    }

    // ── createKey with valid key ──────────────────────────────────────────────

    @Test
    @DisplayName("Creates a SecretKey from a valid 32-byte Base64 key")
    void createKey_withValidKey_shouldReturnSecretKey() {
        // When
        SecretKey key = AesUtil.createKey(base64Key);

        // Then
        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("AES");
        assertThat(key.getEncoded()).hasSize(32);
    }

    // ── createKey with short key (pads to 32 bytes) ───────────────────────────

    @Test
    @DisplayName("Creates a SecretKey from a short key by padding to 32 bytes")
    void createKey_withShortKey_shouldPadTo32Bytes() {
        // Given
        byte[] shortKeyBytes = new byte[16];
        for (int i = 0; i < 16; i++) shortKeyBytes[i] = (byte) i;
        String shortBase64Key = Base64.getEncoder().encodeToString(shortKeyBytes);

        // When
        SecretKey key = AesUtil.createKey(shortBase64Key);

        // Then
        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("AES");
        assertThat(key.getEncoded()).hasSize(32);
        // First 16 bytes should match the original key
        byte[] encoded = key.getEncoded();
        for (int i = 0; i < 16; i++) {
            assertThat(encoded[i]).isEqualTo(shortKeyBytes[i]);
        }
        // Remaining bytes should be zero-padded
        for (int i = 16; i < 32; i++) {
            assertThat(encoded[i]).isEqualTo((byte) 0);
        }
    }

    // ── Empty string encrypt/decrypt ──────────────────────────────────────────

    @Test
    @DisplayName("Encrypts and decrypts an empty string successfully")
    void encrypt_decrypt_emptyString_shouldWork() {
        // Given
        String emptyString = "";

        // When
        String encrypted = AesUtil.encrypt(emptyString, base64Key);
        String decrypted = AesUtil.decrypt(encrypted, base64Key);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEmpty();
        assertThat(decrypted).isEqualTo(emptyString);
    }

    @Test
    @DisplayName("Encrypts to bytes and decrypts from bytes an empty string successfully")
    void encryptToBytes_decryptFromBytes_emptyString_shouldWork() {
        // Given
        String emptyString = "";

        // When
        byte[] encrypted = AesUtil.encryptToBytes(emptyString, base64Key);
        String decrypted = AesUtil.decryptFromBytes(encrypted, base64Key);

        // Then
        assertThat(encrypted).isNotNull();
        assertThat(decrypted).isEqualTo(emptyString);
    }

    // ── Special characters (Unicode, emoji, CJK) ─────────────────────────────

    @Test
    @DisplayName("Encrypts and decrypts a string containing special characters and Unicode successfully")
    void encrypt_decrypt_specialCharacters_shouldWork() {
        // Given
        String specialString = "!@#$%^&*()_+-=[]{}|;':\",./<>?\\`~";

        // When
        String encrypted = AesUtil.encrypt(specialString, base64Key);
        String decrypted = AesUtil.decrypt(encrypted, base64Key);

        // Then
        assertThat(decrypted).isEqualTo(specialString);
    }

    @Test
    @DisplayName("Encrypts and decrypts a string containing CJK characters successfully")
    void encrypt_decrypt_cjkCharacters_shouldWork() {
        // Given
        String cjkString = "한글テスト中文繁體字";

        // When
        String encrypted = AesUtil.encrypt(cjkString, base64Key);
        String decrypted = AesUtil.decrypt(encrypted, base64Key);

        // Then
        assertThat(decrypted).isEqualTo(cjkString);
    }

    @Test
    @DisplayName("Encrypts and decrypts a string containing emojis successfully")
    void encrypt_decrypt_emojis_shouldWork() {
        // Given
        String emojiString = "\uD83D\uDE00\uD83D\uDE80\uD83C\uDF1F\uD83D\uDCA1\uD83D\uDD25";

        // When
        String encrypted = AesUtil.encrypt(emojiString, base64Key);
        String decrypted = AesUtil.decrypt(encrypted, base64Key);

        // Then
        assertThat(decrypted).isEqualTo(emojiString);
    }

    @Test
    @DisplayName("Encrypts and decrypts a mixed Unicode string via encryptWithKey/decryptWithKey")
    void encryptWithKey_decryptWithKey_mixedUnicode_shouldWork() {
        // Given
        String mixedUnicode = "Hello \uC138\uACC4 \u4E16\u754C \uD83C\uDF0D";
        SecretKey key = AesUtil.createKey(base64Key);

        // When
        byte[] encrypted = AesUtil.encryptWithKey(mixedUnicode, key);
        String decrypted = AesUtil.decryptWithKey(encrypted, key);

        // Then
        assertThat(decrypted).isEqualTo(mixedUnicode);
    }
}
