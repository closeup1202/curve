package com.project.curve.spring.pii.mask;

import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.springframework.stereotype.Component;

@Component
public class NameMasker implements PiiMasker {

    @Override
    public String mask(String value, MaskingLevel level) {
        if (value == null || value.isEmpty()) return value;
        if (level == null) level = MaskingLevel.NORMAL;

        int length = value.length();

        return switch (level) {
            case WEAK -> {
                // 첫 글자만 표시: "홍길동" → "홍**"
                if (length == 1) yield "*";
                yield value.charAt(0) + "*".repeat(length - 1);
            }
            case NORMAL -> {
                // 첫 글자와 마지막 글자 표시: "홍길동" → "홍*동"
                if (length <= 2) yield value.charAt(0) + "*";
                yield value.charAt(0) + "*".repeat(length - 2) + value.charAt(length - 1);
            }
            case STRONG -> "*".repeat(length); // 전체 마스킹: "홍길동" → "***"
        };
    }

    @Override
    public boolean supports(PiiType type) {
        return type == PiiType.NAME;
    }
}
