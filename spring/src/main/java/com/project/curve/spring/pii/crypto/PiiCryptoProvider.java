package com.project.curve.spring.pii.crypto;

/**
 * PII 암호화/해싱 제공자 인터페이스.
 */
public interface PiiCryptoProvider {

    /**
     * 값을 암호화한다.
     *
     * @param value 원본 값
     * @param keyAlias 키 별칭 (null이면 기본 키 사용)
     * @return 암호화된 값 (Base64 인코딩)
     */
    String encrypt(String value, String keyAlias);

    /**
     * 암호화된 값을 복호화한다.
     *
     * @param encryptedValue 암호화된 값 (Base64 인코딩)
     * @param keyAlias 키 별칭 (null이면 기본 키 사용)
     * @return 복호화된 원본 값
     */
    String decrypt(String encryptedValue, String keyAlias);

    /**
     * 값을 해시한다 (SHA-256).
     * 솔트가 설정되어 있으면 솔트를 적용한다.
     *
     * @param value 원본 값
     * @return 해시된 값 (Base64 인코딩)
     */
    String hash(String value);
}
