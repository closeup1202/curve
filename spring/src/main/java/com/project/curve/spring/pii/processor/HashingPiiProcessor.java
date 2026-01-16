package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.crypto.PiiCryptoProvider;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * HASH 전략 프로세서.
 * SHA-256 해시를 수행하며, 복호화가 불가능하다.
 */
@Component
@RequiredArgsConstructor
public class HashingPiiProcessor implements PiiProcessor {

    private final PiiCryptoProvider cryptoProvider;

    @Override
    public String process(String value, PiiField piiField) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String hashed = cryptoProvider.hash(value);
        return "HASH(" + hashed + ")";
    }

    @Override
    public PiiStrategy supportedStrategy() {
        return PiiStrategy.HASH;
    }
}
