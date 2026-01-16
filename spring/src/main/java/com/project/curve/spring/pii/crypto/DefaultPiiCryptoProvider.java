package com.project.curve.spring.pii.crypto;

import com.project.curve.spring.exception.PiiCryptoException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기본 PII 암호화 제공자.
 * AES-256-GCM 암호화와 SHA-256 해싱을 지원한다.
 */
public class DefaultPiiCryptoProvider implements PiiCryptoProvider {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey defaultKey;
    private final Map<String, SecretKey> keyStore;
    private final String salt;
    private final SecureRandom secureRandom;

    public DefaultPiiCryptoProvider(String defaultKeyBase64, String salt) {
        this.defaultKey = createKey(defaultKeyBase64);
        this.keyStore = new ConcurrentHashMap<>();
        this.salt = salt != null ? salt : "";
        this.secureRandom = new SecureRandom();
    }

    /**
     * 추가 키를 등록한다.
     */
    public void registerKey(String alias, String keyBase64) {
        keyStore.put(alias, createKey(keyBase64));
    }

    private SecretKey createKey(String keyBase64) {
        if (keyBase64 == null || keyBase64.isEmpty()) {
            // 키가 없으면 더미 키 생성 (실제 환경에서는 반드시 설정 필요)
            byte[] dummyKey = new byte[32];
            Arrays.fill(dummyKey, (byte) 0x00);
            return new SecretKeySpec(dummyKey, "AES");
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        // 키 길이를 32바이트(256비트)로 맞춤
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        } else if (keyBytes.length > 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String value, String keyAlias) {
        if (value == null) return null;

        try {
            SecretKey key = resolveKey(keyAlias);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);

            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            // IV + 암호문을 합쳐서 반환
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new PiiCryptoException("암호화 실패", e);
        }
    }

    @Override
    public String decrypt(String encryptedValue, String keyAlias) {
        if (encryptedValue == null) return null;

        try {
            SecretKey key = resolveKey(keyAlias);
            byte[] combined = Base64.getDecoder().decode(encryptedValue);

            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encryptedBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new PiiCryptoException("복호화 실패", e);
        }
    }

    @Override
    public String hash(String value) {
        if (value == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedValue = salt + value;
            byte[] hashBytes = digest.digest(saltedValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new PiiCryptoException("해싱 실패", e);
        }
    }

    private SecretKey resolveKey(String keyAlias) {
        if (keyAlias == null || keyAlias.isEmpty()) {
            return defaultKey;
        }
        return keyStore.getOrDefault(keyAlias, defaultKey);
    }
}
