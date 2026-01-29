package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NameMasker 테스트")
class NameMaskerTest {

    private NameMasker masker;

    @BeforeEach
    void setUp() {
        masker = new NameMasker();
    }

    @Test
    @DisplayName("WEAK 레벨 - 첫 글자만 표시")
    void maskWeakLevel() {
        // when
        String result = masker.mask("홍길동", MaskingLevel.WEAK);

        // then
        assertEquals("홍**", result);
    }

    @Test
    @DisplayName("NORMAL 레벨 - 첫 글자와 마지막 글자 표시")
    void maskNormalLevel() {
        // when
        String result = masker.mask("홍길동", MaskingLevel.NORMAL);

        // then
        assertEquals("홍*동", result);
    }

    @Test
    @DisplayName("STRONG 레벨 - 전체 마스킹")
    void maskStrongLevel() {
        // when
        String result = masker.mask("홍길동", MaskingLevel.STRONG);

        // then
        assertEquals("***", result);
    }

    @Test
    @DisplayName("한 글자 이름 - WEAK")
    void maskSingleCharWeak() {
        // when
        String result = masker.mask("김", MaskingLevel.WEAK);

        // then
        assertEquals("*", result);
    }

    @Test
    @DisplayName("두 글자 이름 - NORMAL")
    void maskTwoCharsNormal() {
        // when
        String result = masker.mask("홍길", MaskingLevel.NORMAL);

        // then
        assertEquals("홍*", result);
    }

    @Test
    @DisplayName("긴 이름 - NORMAL")
    void maskLongNameNormal() {
        // when
        String result = masker.mask("홍길동길동", MaskingLevel.NORMAL);

        // then
        assertEquals("홍***동", result);
    }

    @Test
    @DisplayName("영문 이름 - WEAK")
    void maskEnglishNameWeak() {
        // when
        String result = masker.mask("John", MaskingLevel.WEAK);

        // then
        assertEquals("J***", result);
    }

    @Test
    @DisplayName("영문 이름 - NORMAL")
    void maskEnglishNameNormal() {
        // when
        String result = masker.mask("John", MaskingLevel.NORMAL);

        // then
        assertEquals("J**n", result);
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
    @DisplayName("null 레벨 처리")
    void maskWithNullLevel() {
        // when
        String result = masker.mask("홍길동", null);

        // then
        assertEquals("홍*동", result); // NORMAL 레벨 동작
    }

    @Test
    @DisplayName("supports 메서드 - NAME 타입만 지원")
    void supportsNameType() {
        // then
        assertTrue(masker.supports(PiiType.NAME));
        assertFalse(masker.supports(PiiType.EMAIL));
        assertFalse(masker.supports(PiiType.PHONE));
        assertFalse(masker.supports(PiiType.CUSTOM));
    }
}
