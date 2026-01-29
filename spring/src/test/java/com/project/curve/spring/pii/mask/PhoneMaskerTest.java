package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PhoneMasker 테스트")
class PhoneMaskerTest {

    private PhoneMasker masker;

    @BeforeEach
    void setUp() {
        masker = new PhoneMasker();
    }

    @Test
    @DisplayName("WEAK 레벨 - 뒤 4자리만 마스킹")
    void maskWeakLevel() {
        // when
        String result = masker.mask("010-1234-5678", MaskingLevel.WEAK);

        // then
        assertEquals("010-1234-****", result);
    }

    @Test
    @DisplayName("NORMAL 레벨 - 중간 4자리 마스킹")
    void maskNormalLevel() {
        // when
        String result = masker.mask("010-1234-5678", MaskingLevel.NORMAL);

        // then
        assertEquals("010-****-5678", result);
    }

    @Test
    @DisplayName("STRONG 레벨 - 뒤 8자리 마스킹")
    void maskStrongLevel() {
        // when
        String result = masker.mask("010-1234-5678", MaskingLevel.STRONG);

        // then
        assertEquals("010-****-****", result);
    }

    @Test
    @DisplayName("하이픈 없는 전화번호 - WEAK")
    void maskWithoutHyphenWeak() {
        // when
        String result = masker.mask("01012345678", MaskingLevel.WEAK);

        // then
        assertEquals("0101234****", result);
    }

    @Test
    @DisplayName("하이픈 없는 전화번호 - NORMAL")
    void maskWithoutHyphenNormal() {
        // when
        String result = masker.mask("01012345678", MaskingLevel.NORMAL);

        // then
        assertEquals("010-****-5678", result);
    }

    @Test
    @DisplayName("짧은 전화번호 (4자리 미만)")
    void maskShortPhone() {
        // when
        String result = masker.mask("123", MaskingLevel.NORMAL);

        // then
        assertEquals("***", result);
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
        String result = masker.mask("010-1234-5678", null);

        // then
        assertEquals("010-****-5678", result); // NORMAL 레벨 동작
    }

    @Test
    @DisplayName("supports 메서드 - PHONE 타입만 지원")
    void supportsPhoneType() {
        // then
        assertTrue(masker.supports(PiiType.PHONE));
        assertFalse(masker.supports(PiiType.NAME));
        assertFalse(masker.supports(PiiType.EMAIL));
        assertFalse(masker.supports(PiiType.CUSTOM));
    }

    @Test
    @DisplayName("괄호 포함 전화번호 - WEAK")
    void maskWithParenthesisWeak() {
        // when
        String result = masker.mask("(010)1234-5678", MaskingLevel.WEAK);

        // then
        assertTrue(result.contains("****"));
        assertTrue(result.contains("010"));
    }

    @Test
    @DisplayName("공백 포함 전화번호 - WEAK")
    void maskWithSpaceWeak() {
        // when
        String result = masker.mask("010 1234 5678", MaskingLevel.WEAK);

        // then
        assertTrue(result.contains("****"));
    }

}
