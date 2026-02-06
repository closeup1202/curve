package com.project.curve.core.key;

import lombok.NonNull;

import java.util.Arrays;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnvelopeDataKey that = (EnvelopeDataKey) o;
        return Arrays.equals(plaintextKey, that.plaintextKey) &&
                Arrays.equals(encryptedKey, that.encryptedKey);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(plaintextKey);
        result = 31 * result + Arrays.hashCode(encryptedKey);
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return "EnvelopeDataKey{" +
                "plaintextKey=[PROTECTED]" +
                ", encryptedKey=" + Arrays.toString(encryptedKey) +
                '}';
    }
}
