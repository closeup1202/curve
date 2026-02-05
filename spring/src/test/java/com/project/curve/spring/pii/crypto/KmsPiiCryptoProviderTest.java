package com.project.curve.spring.pii.crypto;

import com.project.curve.core.key.EnvelopeDataKey;
import com.project.curve.core.key.KeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@DisplayName("KmsPiiCryptoProvider Test")
@ExtendWith(MockitoExtension.class)
class KmsPiiCryptoProviderTest {

    private static final String SALT = "test-salt";
    private static final String KEY_ALIAS = "test-key";

    @Mock
    private KeyProvider keyProvider;

    @Nested
    @DisplayName("Static key mode (supportsEnvelopeEncryption = false)")
    class StaticKeyMode {

        private KmsPiiCryptoProvider provider;
        private String staticKeyBase64;

        @BeforeEach
        void setUp() {
            byte[] keyBytes = new byte[32];
            for (int i = 0; i < 32; i++) {
                keyBytes[i] = (byte) i;
            }
            staticKeyBase64 = Base64.getEncoder().encodeToString(keyBytes);

            lenient().when(keyProvider.supportsEnvelopeEncryption()).thenReturn(false);
            lenient().when(keyProvider.getDataKey(KEY_ALIAS)).thenReturn(staticKeyBase64);

            provider = new KmsPiiCryptoProvider(keyProvider, SALT);
        }

        @Test
        @DisplayName("Encrypt and decrypt round-trip returns the original plaintext")
        void encryptDecrypt_roundTrip_shouldReturnOriginal() {
            // Given
            String plainText = "sensitive-pii-data";

            // When
            String encrypted = provider.encrypt(plainText, KEY_ALIAS);
            String decrypted = provider.decrypt(encrypted, KEY_ALIAS);

            // Then
            assertThat(encrypted).isNotNull();
            assertThat(encrypted).isNotEqualTo(plainText);
            assertThat(encrypted).isBase64();
            assertThat(decrypted).isEqualTo(plainText);
        }

        @Test
        @DisplayName("Encrypt with null plaintext returns null")
        void encrypt_withNull_shouldReturnNull() {
            // When
            String result = provider.encrypt(null, KEY_ALIAS);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Decrypt with null ciphertext returns null")
        void decrypt_withNull_shouldReturnNull() {
            // When
            String result = provider.decrypt(null, KEY_ALIAS);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Hash produces consistent results for the same input")
        void hash_shouldProduceConsistentResults() {
            // Given
            String value = "test@example.com";

            // When
            String hash1 = provider.hash(value);
            String hash2 = provider.hash(value);

            // Then
            assertThat(hash1).isNotNull();
            assertThat(hash1).isEqualTo(hash2);
            assertThat(hash1).isNotEqualTo(value);
        }
    }

    @Nested
    @DisplayName("Envelope encryption mode (supportsEnvelopeEncryption = true)")
    class EnvelopeEncryptionMode {

        private KmsPiiCryptoProvider provider;
        private byte[] plaintextDek;
        private byte[] encryptedDek;

        @BeforeEach
        void setUp() {
            plaintextDek = new byte[32];
            for (int i = 0; i < 32; i++) {
                plaintextDek[i] = (byte) i;
            }
            encryptedDek = "mock-encrypted-dek".getBytes();

            lenient().when(keyProvider.supportsEnvelopeEncryption()).thenReturn(true);
            lenient().when(keyProvider.generateDataKey(KEY_ALIAS))
                    .thenReturn(new EnvelopeDataKey(plaintextDek, encryptedDek));
            lenient().when(keyProvider.decryptDataKey(encryptedDek)).thenReturn(plaintextDek);

            provider = new KmsPiiCryptoProvider(keyProvider, SALT);
        }

        @Test
        @DisplayName("Encrypt and decrypt round-trip returns the original plaintext")
        void encryptDecrypt_roundTrip_shouldReturnOriginal() {
            // Given
            String plainText = "sensitive-envelope-data";

            // When
            String encrypted = provider.encrypt(plainText, KEY_ALIAS);
            String decrypted = provider.decrypt(encrypted, KEY_ALIAS);

            // Then
            assertThat(encrypted).isNotNull();
            assertThat(encrypted).isNotEqualTo(plainText);
            assertThat(encrypted).isBase64();
            assertThat(decrypted).isEqualTo(plainText);
        }

        @Test
        @DisplayName("Encrypted output contains the encrypted DEK in the binary format")
        void encrypt_shouldContainEncryptedDekInOutput() {
            // Given
            String plainText = "data-to-encrypt";

            // When
            String encrypted = provider.encrypt(plainText, KEY_ALIAS);

            // Then - Verify the binary format: [2-byte encDEK length][encDEK][IV + ciphertext]
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            short storedDekLength = buffer.getShort();
            assertThat(storedDekLength).isEqualTo((short) encryptedDek.length);

            byte[] storedEncryptedDek = new byte[storedDekLength];
            buffer.get(storedEncryptedDek);
            assertThat(storedEncryptedDek).isEqualTo(encryptedDek);

            // Remaining bytes should be IV (12 bytes) + AES-GCM ciphertext
            int remainingBytes = buffer.remaining();
            assertThat(remainingBytes).isGreaterThan(12); // At least IV + some ciphertext
        }

        @Test
        @DisplayName("Encrypt with null plaintext returns null")
        void encrypt_withNull_shouldReturnNull() {
            // When
            String result = provider.encrypt(null, KEY_ALIAS);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Decrypt with null ciphertext returns null")
        void decrypt_withNull_shouldReturnNull() {
            // When
            String result = provider.decrypt(null, KEY_ALIAS);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Hash")
    class Hash {

        private KmsPiiCryptoProvider provider;

        @BeforeEach
        void setUp() {
            provider = new KmsPiiCryptoProvider(keyProvider, SALT);
        }

        @Test
        @DisplayName("Hash with salt produces the correct SHA-256 output")
        void hash_withSalt_shouldProduceCorrectOutput() throws Exception {
            // Given
            String value = "test@example.com";
            String saltedValue = SALT + value;

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expectedBytes = digest.digest(saltedValue.getBytes(StandardCharsets.UTF_8));
            String expectedHash = Base64.getEncoder().encodeToString(expectedBytes);

            // When
            String actualHash = provider.hash(value);

            // Then
            assertThat(actualHash).isEqualTo(expectedHash);
        }

        @Test
        @DisplayName("Hash of null returns null")
        void hash_withNull_shouldReturnNull() {
            // When
            String result = provider.hash(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Same value produces the same hash")
        void hash_sameValue_shouldReturnSameHash() {
            // Given
            String value = "consistent-value";

            // When
            String hash1 = provider.hash(value);
            String hash2 = provider.hash(value);

            // Then
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("Different values produce different hashes")
        void hash_differentValues_shouldReturnDifferentHashes() {
            // Given
            String value1 = "value-one";
            String value2 = "value-two";

            // When
            String hash1 = provider.hash(value1);
            String hash2 = provider.hash(value2);

            // Then
            assertThat(hash1).isNotEqualTo(hash2);
        }
    }
}
