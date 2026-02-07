package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PhoneMasker implements PiiMasker {

    // Pre-compiled regex patterns for performance
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("[^0-9]");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\d{3})-?(\\d{3,4})-?(\\d{4})");

    @Override
    public String mask(String value, MaskingLevel level) {
        if (value == null || value.isEmpty()) return value;
        if (level == null) level = MaskingLevel.NORMAL;

        // Extract digits only
        String digits = NON_DIGIT_PATTERN.matcher(value).replaceAll("");
        if (digits.length() < 4) return "*".repeat(value.length());

        return switch (level) {
            case WEAK -> // Mask last 4 digits only: "010-1234-5678" → "010-1234-****"
                    maskDigits(value, digits);
            case NORMAL -> // Mask middle 4 digits: "010-1234-5678" → "010-****-5678"
                    PHONE_PATTERN.matcher(value).replaceAll("$1-****-$3");
            case STRONG -> // Mask last 8 digits: "010-1234-5678" → "010-****-****"
                    PHONE_PATTERN.matcher(value).replaceAll("$1-****-****");
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
