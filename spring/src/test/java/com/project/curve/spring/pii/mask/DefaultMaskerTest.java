package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultMasker 테스트")
class DefaultMaskerTest {

    private DefaultMasker masker;

    @BeforeEach
    void setUp() {
        masker = new DefaultMasker();
    }

    @Test
    @DisplayName("WEAK 레벨 마스킹 - 앞 절반 표시")
    void maskWeakLevel() {
        // when
        String result = masker.mask("abcdef", MaskingLevel.WEAK);

        // then
        assertEquals("abc***", result);
    }

    @Test
    @DisplayName("NORMAL 레벨 마스킹 - 앞 2자 표시")
    void maskNormalLevel() {
        // when
        String result = masker.mask("abcdef", MaskingLevel.NORMAL);

        // then
        assertEquals("ab****", result);
    }

    @Test
    @DisplayName("STRONG 레벨 마스킹 - 전체 마스킹")
    void maskStrongLevel() {
        // when
        String result = masker.mask("abcdef", MaskingLevel.STRONG);

        // then
        assertEquals("******", result);
    }

    @Test
    @DisplayName("null 값 처리")
    void maskNullValue() {
        // when
        String result = masker.mask(null, MaskingLevel.NORMAL);

        // then
        assertNull(result);
    }

    @Test
    @DisplayName("빈 문자열 처리")
    void maskEmptyString() {
        // when
        String result = masker.mask("", MaskingLevel.NORMAL);

        // then
        assertEquals("", result);
    }

    @Test
    @DisplayName("null 레벨 처리 - 기본값 NORMAL")
    void maskWithNullLevel() {
        // when
        String result = masker.mask("abcdef", null);

        // then
        assertEquals("ab****", result); // NORMAL 레벨 동작
    }

    @Test
    @DisplayName("한 글자 마스킹 - WEAK")
    void maskSingleCharWeak() {
        // when
        String result = masker.mask("a", MaskingLevel.WEAK);

        // then
        assertEquals("a", result); // 최소 1자 표시
    }

    @Test
    @DisplayName("한 글자 마스킹 - NORMAL")
    void maskSingleCharNormal() {
        // when
        String result = masker.mask("a", MaskingLevel.NORMAL);

        // then
        assertEquals("a", result);
    }

    @Test
    @DisplayName("한 글자 마스킹 - STRONG")
    void maskSingleCharStrong() {
        // when
        String result = masker.mask("a", MaskingLevel.STRONG);

        // then
        assertEquals("*", result);
    }

    @Test
    @DisplayName("긴 문자열 마스킹 - WEAK")
    void maskLongStringWeak() {
        // when
        String result = masker.mask("abcdefghijklmnop", MaskingLevel.WEAK);

        // then
        assertEquals("abcdefgh********", result);
    }

    @Test
    @DisplayName("한글 마스킹")
    void maskKorean() {
        // when
        String weakResult = masker.mask("홍길동", MaskingLevel.WEAK);
        String normalResult = masker.mask("홍길동", MaskingLevel.NORMAL);
        String strongResult = masker.mask("홍길동", MaskingLevel.STRONG);

        // then
        assertEquals("홍**", weakResult); // Math.max(1, 3/2) = 1 -> "홍" + "**"
        assertEquals("홍길*", normalResult); // Math.min(2, 3) = 2 -> "홍길" + "*"
        assertEquals("***", strongResult);
    }

    @Test
    @DisplayName("supports 메서드 - CUSTOM 타입만 지원")
    void supportsCustomType() {
        // then
        assertTrue(masker.supports(PiiType.CUSTOM));
        assertFalse(masker.supports(PiiType.NAME));
        assertFalse(masker.supports(PiiType.EMAIL));
        assertFalse(masker.supports(PiiType.PHONE));
        assertFalse(masker.supports(PiiType.ADDRESS));
    }

    @Test
    @DisplayName("PiiMasker 인터페이스 구현")
    void implementsPiiMasker() {
        // then
        assertTrue(masker instanceof PiiMasker);
    }
}
