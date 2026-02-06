package com.project.curve.spring.pii.crypto;

import com.project.curve.core.key.EnvelopeDataKey;
import com.project.curve.core.key.KeyProvider;
import com.project.curve.spring.exception.PiiCryptoException;
import com.project.curve.spring.pii.crypto.util.AesUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * KMS-based PII encryption provider.
 * <p>
 * Supports two modes depending on the {@link KeyProvider} implementation:
 * <ul>
 *   <li><b>Envelope encryption</b> (AWS KMS): Generates a DEK per encryption, stores the encrypted DEK
 *       alongside the ciphertext. Ciphertext format: {@code Base64([2-byte encDEK len][encDEK][IV+ciphertext])}</li>
 *   <li><b>Static key</b> (Vault K/V): Fetches a pre-existing key and encrypts locally.</li>
 * </ul>
 */
public class KmsPiiCryptoProvider implements PiiCryptoProvider {

    private final KeyProvider keyProvider;
    private final String salt;

    public KmsPiiCryptoProvider(KeyProvider keyProvider, String salt) {
        this.keyProvider = keyProvider;
        this.salt = salt != null ? salt : "";
    }

    @Override
    public String encrypt(String plainText, String keyAlias) {
        if (plainText == null) return null;

        try {
            if (keyProvider.supportsEnvelopeEncryption()) {
                return envelopeEncrypt(plainText, keyAlias);
            }
            String encryptionKey = keyProvider.getDataKey(keyAlias);
            return AesUtil.encrypt(plainText, encryptionKey);
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("KMS encryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String decrypt(String encryptedText, String keyAlias) {
        if (encryptedText == null) return null;

        try {
            if (keyProvider.supportsEnvelopeEncryption()) {
                return envelopeDecrypt(encryptedText);
            }
            String encryptionKey = keyProvider.getDataKey(keyAlias);
            return AesUtil.decrypt(encryptedText, encryptionKey);
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("KMS decryption failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String hash(String value) {
        if (value == null) return null;

        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String saltedValue = salt + value;
            byte[] hashBytes = digest.digest(saltedValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new PiiCryptoException("Hashing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Envelope encryption: generates DEK via KMS, encrypts data locally,
     * packs encrypted DEK + ciphertext together.
     * <p>
     * Format: Base64([2-byte encDEK length (big-endian)][encrypted DEK][IV + AES-GCM ciphertext])
     */
    private String envelopeEncrypt(String plainText, String keyAlias) {
        EnvelopeDataKey dataKey = keyProvider.generateDataKey(keyAlias);

        String base64PlaintextKey = Base64.getEncoder().encodeToString(dataKey.plaintextKey());
        byte[] localCiphertext = AesUtil.encryptToBytes(plainText, base64PlaintextKey);

        byte[] encryptedDek = dataKey.encryptedKey();

        // Pack: [2-byte encDEK length][encrypted DEK][local ciphertext (IV + encrypted data)]
        ByteBuffer buffer = ByteBuffer.allocate(2 + encryptedDek.length + localCiphertext.length);
        buffer.putShort((short) encryptedDek.length);
        buffer.put(encryptedDek);
        buffer.put(localCiphertext);

        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * Envelope decryption: extracts encrypted DEK from ciphertext,
     * decrypts DEK via KMS, then decrypts data locally.
     */
    private String envelopeDecrypt(String encryptedText) {
        byte[] packed = Base64.getDecoder().decode(encryptedText);
        ByteBuffer buffer = ByteBuffer.wrap(packed);

        // Read encrypted DEK length
        short encDekLength = buffer.getShort();
        if (encDekLength <= 0 || encDekLength > packed.length - 2) {
            throw new PiiCryptoException("Invalid envelope ciphertext: bad encrypted DEK length");
        }

        // Read encrypted DEK
        byte[] encryptedDek = new byte[encDekLength];
        buffer.get(encryptedDek);

        // Read local ciphertext (IV + AES-GCM ciphertext)
        byte[] localCiphertext = new byte[buffer.remaining()];
        buffer.get(localCiphertext);

        // Decrypt DEK via KMS
        byte[] plaintextDek = keyProvider.decryptDataKey(encryptedDek);
        String base64PlaintextKey = Base64.getEncoder().encodeToString(plaintextDek);

        return AesUtil.decryptFromBytes(localCiphertext, base64PlaintextKey);
    }
}
