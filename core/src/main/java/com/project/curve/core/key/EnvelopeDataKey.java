package com.project.curve.core.key;

/**
 * Holds both plaintext and encrypted Data Encryption Key (DEK)
 * for envelope encryption.
 *
 * @param plaintextKey  Plaintext DEK bytes (used for local AES encryption)
 * @param encryptedKey  Encrypted DEK bytes (stored alongside ciphertext for later decryption)
 */
public record EnvelopeDataKey(byte[] plaintextKey, byte[] encryptedKey) {

    public EnvelopeDataKey {
        if (plaintextKey == null || plaintextKey.length == 0) {
            throw new IllegalArgumentException("plaintextKey must not be null or empty");
        }
        if (encryptedKey == null || encryptedKey.length == 0) {
            throw new IllegalArgumentException("encryptedKey must not be null or empty");
        }
    }
}
