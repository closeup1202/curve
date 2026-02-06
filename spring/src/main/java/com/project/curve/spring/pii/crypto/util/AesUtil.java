package com.project.curve.spring.pii.crypto.util;

import com.project.curve.spring.exception.PiiCryptoException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Utility class for AES-256-GCM encryption and decryption.
 */
public final class AesUtil {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesUtil() {
        // Prevent instantiation
    }

    /**
     * Encrypts plaintext and returns Base64-encoded result (IV + ciphertext).
     */
    public static String encrypt(String plainText, String base64Key) {
        if (plainText == null) return null;
        return Base64.getEncoder().encodeToString(encryptToBytes(plainText, base64Key));
    }

    /**
     * Encrypts plaintext and returns raw bytes (IV + ciphertext).
     * Useful for envelope encryption where the caller packs additional data.
     */
    public static byte[] encryptToBytes(String plainText, String base64Key) {
        if (plainText == null) return null;
        if (base64Key == null || base64Key.isBlank()) {
            throw new PiiCryptoException("Encryption key cannot be null or blank.");
        }

        try {
            SecretKey key = createKey(base64Key);
            return encryptWithKey(plainText, key);
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Encrypts plaintext using a SecretKey directly (avoids Base64 roundtrip).
     */
    public static byte[] encryptWithKey(String plainText, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);

            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return combined;
        } catch (Exception e) {
            throw new PiiCryptoException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts Base64-encoded ciphertext (IV + ciphertext).
     */
    public static String decrypt(String encryptedText, String base64Key) {
        if (encryptedText == null) return null;
        if (base64Key == null || base64Key.isBlank()) {
            throw new PiiCryptoException("Decryption key cannot be null or blank.");
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);
            SecretKey key = createKey(base64Key);
            return decryptWithKey(combined, key);
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts raw bytes (IV + ciphertext) using a Base64-encoded key.
     * Useful for envelope encryption where the caller unpacks additional data.
     */
    public static String decryptFromBytes(byte[] combined, String base64Key) {
        if (combined == null) return null;
        if (base64Key == null || base64Key.isBlank()) {
            throw new PiiCryptoException("Decryption key cannot be null or blank.");
        }

        try {
            SecretKey key = createKey(base64Key);
            return decryptWithKey(combined, key);
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts raw bytes (IV + ciphertext) using a SecretKey directly.
     */
    public static String decryptWithKey(byte[] combined, SecretKey key) {
        try {
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encryptedBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PiiCryptoException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a SecretKey from a Base64-encoded key string.
     * If the key is shorter than 32 bytes, it will be zero-padded.
     */
    public static SecretKey createKey(String keyBase64) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 format for key.", e);
        }

        byte[] paddedKey;
        if (keyBytes.length < 32) {
            // Pad with zeros to reach 32 bytes
            paddedKey = Arrays.copyOf(keyBytes, 32);
        } else if (keyBytes.length > 32) {
            Arrays.fill(keyBytes, (byte) 0);
            throw new IllegalArgumentException(
                    "AES-256 requires at most 32 bytes key, but got " + keyBytes.length + " bytes. " +
                            "Please provide a Base64-encoded key with 32 bytes or less."
            );
        } else {
            paddedKey = keyBytes;
        }

        try {
            return new SecretKeySpec(paddedKey, "AES");
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
            if (paddedKey != keyBytes) {
                Arrays.fill(paddedKey, (byte) 0);
            }
        }
    }
}
