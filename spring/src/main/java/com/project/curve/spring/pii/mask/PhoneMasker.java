package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.springframework.stereotype.Component;

@Component
public class PhoneMasker implements PiiMasker {

    @Override
    public String mask(String value, MaskingLevel level) {
        if (value == null || value.isEmpty()) return value;
        if (level == null) level = MaskingLevel.NORMAL;

        // 숫자만 추출
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return "*".repeat(value.length());

        return switch (level) {
            case WEAK -> // 뒤 4자리만 마스킹: "010-1234-5678" → "010-1234-****"
                    maskDigits(value, digits);
            case NORMAL -> // 중간 4자리 마스킹: "010-1234-5678" → "010-****-5678"
                    value.replaceAll("(\\d{3})-?(\\d{3,4})-?(\\d{4})", "$1-****-$3");
            case STRONG -> // 뒤 8자리 마스킹: "010-1234-5678" → "010-****-****"
                    value.replaceAll("(\\d{3})-?(\\d{3,4})-?(\\d{4})", "$1-****-****");
        };
    }

    private String maskDigits(String original, String digits) {
        StringBuilder result = new StringBuilder();
        int digitIndex = 0;
        int totalDigits = digits.length();
        int maskFrom = totalDigits - 4;

        for (char c : original.toCharArray()) {
            if (Character.isDigit(c)) {
                result.append(digitIndex >= maskFrom ? '*' : c);
                digitIndex++;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    @Override
    public boolean supports(PiiType type) {
        return type == PiiType.PHONE;
    }
}
