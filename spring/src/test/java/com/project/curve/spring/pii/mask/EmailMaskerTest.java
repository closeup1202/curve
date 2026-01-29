package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailMasker 테스트")
class EmailMaskerTest {

    private EmailMasker masker;

    @BeforeEach
    void setUp() {
        masker = new EmailMasker();
    }

    @Test
    @DisplayName("WEAK 레벨 - 로컬 앞 3자 표시")
    void maskWeakLevel() {
        // when
        String result = masker.mask("john.doe@gmail.com", MaskingLevel.WEAK);

        // then
        assertEquals("joh*****@gmail.com", result);
    }

    @Test
    @DisplayName("NORMAL 레벨 - 로컬 앞 2자 + 도메인 앞 2자 표시")
    void maskNormalLevel() {
        // when
        String result = masker.mask("john.doe@gmail.com", MaskingLevel.NORMAL);

        // then
        assertEquals("jo******@gm***.com", result);
    }

    @Test
    @DisplayName("STRONG 레벨 - 로컬 전체 + 도메인 전체 마스킹")
    void maskStrongLevel() {
        // when
        String result = masker.mask("john.doe@gmail.com", MaskingLevel.STRONG);

        // then
        assertEquals("********@*****.com", result);
    }

    @Test
    @DisplayName("짧은 로컬 부분 - WEAK")
    void maskShortLocalWeak() {
        // when
        String result = masker.mask("ab@test.com", MaskingLevel.WEAK);

        // then
        assertEquals("ab@test.com", result); // 3자 미만이면 마스킹하지 않음
    }

    @Test
    @DisplayName("@ 기호가 없는 경우")
    void maskWithoutAt() {
        // when
        String result = masker.mask("notanemail", MaskingLevel.NORMAL);

        // then
        assertEquals("**********", result);
    }

    @Test
    @DisplayName("@ 기호가 맨 앞에 있는 경우")
    void maskWithAtAtStart() {
        // when
        String result = masker.mask("@gmail.com", MaskingLevel.NORMAL);

        // then
        assertEquals("**********", result);
    }

    @Test
    @DisplayName("도메인에 점이 없는 경우")
    void maskWithoutDotInDomain() {
        // when
        String result = masker.mask("john@localhost", MaskingLevel.NORMAL);

        // then
        assertEquals("jo**@*********", result);
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
        String result = masker.mask("test@test.com", null);

        // then
        assertEquals("te**@te**.com", result); // NORMAL 레벨 동작
    }

    @Test
    @DisplayName("supports 메서드 - EMAIL 타입만 지원")
    void supportsEmailType() {
        // then
        assertTrue(masker.supports(PiiType.EMAIL));
        assertFalse(masker.supports(PiiType.NAME));
        assertFalse(masker.supports(PiiType.PHONE));
        assertFalse(masker.supports(PiiType.CUSTOM));
    }

    @Test
    @DisplayName("복잡한 이메일 - WEAK")
    void maskComplexEmailWeak() {
        // when
        String result = masker.mask("user.name+tag@example.co.uk", MaskingLevel.WEAK);

        // then
        assertTrue(result.startsWith("use"));
        assertTrue(result.contains("@"));
        assertTrue(result.contains("example.co.uk"));
    }

    @Test
    @DisplayName("복잡한 이메일 - STRONG")
    void maskComplexEmailStrong() {
        // when
        String result = masker.mask("user.name@example.com", MaskingLevel.STRONG);

        // then
        assertTrue(result.contains("@"));
        assertTrue(result.endsWith(".com"));
        assertTrue(result.contains("*"));
    }
}
