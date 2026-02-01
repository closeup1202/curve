package com.project.curve.spring.pii.annotation;

import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * PII(개인식별정보) 필드를 표시하고 처리 전략을 지정하는 어노테이션.
 * Jackson 직렬화 시 자동으로 마스킹/암호화/해싱 처리됩니다.
 *
 * <pre>{@code
 * public record UserPayload(
 *     @PiiField(type = PiiType.EMAIL)
 *     String email,
 *
 *     @PiiField(type = PiiType.PHONE, level = MaskingLevel.STRONG)
 *     String phone,
 *
 *     @PiiField(strategy = PiiStrategy.ENCRYPT, encryptKey = "sensitive")
 *     String ssn,
 *
 *     @PiiField(strategy = PiiStrategy.EXCLUDE)
 *     String password
 * ) {}
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PiiField {

    /**
     * PII 데이터 유형 (마스킹 패턴 결정에 사용됨)
     */
    PiiType type() default PiiType.CUSTOM;

    /**
     * PII 처리 전략
     * - MASK: 일부 문자를 *로 치환
     * - ENCRYPT: AES 암호화 (복호화 가능)
     * - HASH: SHA-256 해시 (복호화 불가능)
     * - EXCLUDE: 직렬화에서 제외
     */
    PiiStrategy strategy() default PiiStrategy.MASK;

    /**
     * 마스킹 강도 (strategy=MASK일 때 사용)
     */
    MaskingLevel level() default MaskingLevel.NORMAL;

    /**
     * 암호화 키 별칭 (strategy=ENCRYPT일 때 사용)
     * 비어있으면 기본 키 사용
     */
    String encryptKey() default "";
}
