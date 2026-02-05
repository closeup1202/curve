package com.project.curve.core.key;

/**
 * Key Management System (KMS) Provider Interface.
 * Responsible for fetching encryption keys (DEK - Data Encryption Key).
 * <p>
 * Supports two modes:
 * <ul>
 *   <li><b>Static key mode</b>: {@link #getDataKey(String)} returns a pre-existing key (e.g., Vault K/V)</li>
 *   <li><b>Envelope encryption mode</b>: {@link #generateDataKey(String)} generates a new DEK
 *       and {@link #decryptDataKey(byte[])} recovers the plaintext DEK from the encrypted DEK</li>
 * </ul>
 */
public interface KeyProvider {

    /**
     * Get the encryption key for the given alias/ID.
     * Used by static key providers (e.g., Vault K/V).
     *
     * @param keyId Key identifier (ARN, Alias, Path, etc.)
     * @return Base64 encoded encryption key
     */
    String getDataKey(String keyId);

    /**
     * Generate a new Data Encryption Key (DEK) using the master key.
     * Returns both the plaintext DEK (for local encryption) and the encrypted DEK
     * (to be stored alongside the ciphertext).
     *
     * @param keyId Master key identifier (e.g., AWS KMS key ARN)
     * @return EnvelopeDataKey containing plaintext and encrypted DEK
     * @throws UnsupportedOperationException if this provider does not support envelope encryption
     */
    default EnvelopeDataKey generateDataKey(String keyId) {
        throw new UnsupportedOperationException("Envelope encryption not supported by this provider");
    }

    /**
     * Decrypt an encrypted DEK using the master key.
     *
     * @param encryptedDataKey The encrypted DEK bytes
     * @return Plaintext DEK bytes
     * @throws UnsupportedOperationException if this provider does not support envelope encryption
     */
    default byte[] decryptDataKey(byte[] encryptedDataKey) {
        throw new UnsupportedOperationException("Envelope encryption not supported by this provider");
    }

    /**
     * Whether this provider supports envelope encryption.
     *
     * @return true if {@link #generateDataKey(String)} and {@link #decryptDataKey(byte[])} are implemented
     */
    default boolean supportsEnvelopeEncryption() {
        return false;
    }
}
