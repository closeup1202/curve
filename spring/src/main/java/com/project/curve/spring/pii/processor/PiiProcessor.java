package com.project.curve.spring.pii.processor;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.strategy.PiiStrategy;

/**
 * PII 데이터 처리 인터페이스.
 * 전략별로 다른 구현체가 처리를 담당한다.
 */
public interface PiiProcessor {

    /**
     * PII 값을 처리한다.
     *
     * @param value 원본 값
     * @param piiField PiiField 어노테이션 정보
     * @return 처리된 값
     */
    String process(String value, PiiField piiField);

    /**
     * 이 프로세서가 지원하는 전략을 반환한다.
     */
    PiiStrategy supportedStrategy();
}
