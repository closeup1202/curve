package com.project.curve.spring.pii.crypto;

import com.project.curve.spring.exception.PiiCryptoException;
import lombok.Getter;

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
 * <p>
 * AES-256-GCM 암호화와 SHA-256 해싱을 지원한다.
 * <p>
 * <b>보안 주의사항:</b>
 * <ul>
 *   <li>암호화 기능을 사용하려면 반드시 {@code curve.pii.crypto.default-key}를 설정해야 합니다.</li>
 *   <li>키가 설정되지 않은 상태에서 {@link #encrypt} 호출 시 예외가 발생합니다.</li>
 *   <li>해싱 기능은 키 없이도 사용 가능하지만, salt 설정을 권장합니다.</li>
 * </ul>
 */
public class DefaultPiiCryptoProvider implements PiiCryptoProvider {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey defaultKey;
    private final Map<String, SecretKey> keyStore;
    private final String salt;
    private final SecureRandom secureRandom;
    @Getter
    private final boolean encryptionEnabled;

    /**
     * DefaultPiiCryptoProvider를 생성한다.
     *
     * @param defaultKeyBase64 Base64로 인코딩된 AES-256 암호화 키 (null 가능, 암호화 비활성화)
     * @param salt             해싱에 사용할 솔트 (null 가능)
     */
    public DefaultPiiCryptoProvider(String defaultKeyBase64, String salt) {
        this.encryptionEnabled = defaultKeyBase64 != null && !defaultKeyBase64.isBlank();
        this.defaultKey = encryptionEnabled ? createKey(defaultKeyBase64) : null;
        this.keyStore = new ConcurrentHashMap<>();
        this.salt = salt != null ? salt : "";
        this.secureRandom = new SecureRandom();
    }

    /**
     * 추가 키를 등록한다.
     *
     * @param alias     키 별칭
     * @param keyBase64 Base64로 인코딩된 AES-256 키
     * @throws IllegalArgumentException 키가 null이거나 비어있는 경우
     */
    public void registerKey(String alias, String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalArgumentException("암호화 키는 null이거나 비어있을 수 없습니다. alias: " + alias);
        }
        keyStore.put(alias, createKey(keyBase64));
    }

    private SecretKey createKey(String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "PII 암호화 키가 설정되지 않았습니다. " +
                    "curve.pii.crypto.default-key 설정이 필요합니다. " +
                    "환경변수 PII_ENCRYPTION_KEY 사용을 권장합니다."
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("잘못된 Base64 형식의 암호화 키입니다.", e);
        }

        // 키 길이를 32바이트(256비트)로 맞춤
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        } else if (keyBytes.length > 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 값을 AES-256-GCM으로 암호화한다.
     *
     * @param value    암호화할 원본 값
     * @param keyAlias 사용할 키 별칭 (null이면 기본 키 사용)
     * @return Base64로 인코딩된 암호문 (IV 포함)
     * @throws PiiCryptoException 암호화 키가 설정되지 않았거나 암호화 실패 시
     */
    @Override
    public String encrypt(String value, String keyAlias) {
        if (value == null) return null;

        if (!encryptionEnabled) {
            throw new PiiCryptoException(
                    "PII 암호화가 비활성화되어 있습니다. " +
                    "curve.pii.crypto.default-key를 설정하세요. " +
                    "환경변수: PII_ENCRYPTION_KEY"
            );
        }

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
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("암호화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * AES-256-GCM으로 암호화된 값을 복호화한다.
     *
     * @param encryptedValue Base64로 인코딩된 암호문 (IV 포함)
     * @param keyAlias       사용할 키 별칭 (null이면 기본 키 사용)
     * @return 복호화된 원본 값
     * @throws PiiCryptoException 암호화 키가 설정되지 않았거나 복호화 실패 시
     */
    @Override
    public String decrypt(String encryptedValue, String keyAlias) {
        if (encryptedValue == null) return null;

        if (!encryptionEnabled) {
            throw new PiiCryptoException(
                    "PII 암호화가 비활성화되어 있습니다. " +
                    "curve.pii.crypto.default-key를 설정하세요."
            );
        }

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
        } catch (PiiCryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new PiiCryptoException("복호화 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 값을 SHA-256으로 해싱한다.
     * <p>
     * 해싱은 암호화 키 없이도 사용할 수 있으나, salt 설정을 권장한다.
     *
     * @param value 해싱할 원본 값
     * @return Base64로 인코딩된 해시 값
     * @throws PiiCryptoException 해싱 실패 시
     */
    @Override
    public String hash(String value) {
        if (value == null) return null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedValue = salt + value;
            byte[] hashBytes = digest.digest(saltedValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new PiiCryptoException("해싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 키 별칭으로 사용할 키를 찾는다.
     *
     * @param keyAlias 키 별칭 (null이거나 빈 문자열이면 기본 키 사용)
     * @return 해당 키 또는 기본 키
     */
    private SecretKey resolveKey(String keyAlias) {
        if (keyAlias == null || keyAlias.isEmpty()) {
            return defaultKey;
        }
        return keyStore.getOrDefault(keyAlias, defaultKey);
    }
}
