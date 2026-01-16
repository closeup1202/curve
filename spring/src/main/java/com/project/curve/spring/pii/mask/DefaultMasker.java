package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.springframework.stereotype.Component;

@Component
public class DefaultMasker implements PiiMasker {

    @Override
    public String mask(String value, MaskingLevel level) {
        if (value == null || value.isEmpty()) return value;

        int length = value.length();

        return switch (level) {
            case WEAK -> {
                // 앞 절반 표시: "abcdef" → "abc***"
                int showCount = Math.max(1, length / 2);
                yield value.substring(0, showCount) + "*".repeat(length - showCount);
            }
            case NORMAL -> {
                // 앞 2자 표시: "abcdef" → "ab****"
                int showCount = Math.min(2, length);
                yield value.substring(0, showCount) + "*".repeat(length - showCount);
            }
            case STRONG -> "*".repeat(length); // 전체 마스킹: "abcdef" → "******"
        };
    }

    @Override
    public boolean supports(PiiType type) {
        return type == PiiType.CUSTOM;
    }
}
