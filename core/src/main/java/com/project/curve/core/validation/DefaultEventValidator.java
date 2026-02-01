package com.project.curve.core.validation;

import com.project.curve.core.envelope.EventEnvelope;

/**
 * 기본 이벤트 검증기.
 * <p>
 * 필수 필드의 존재 여부와 시간 순서만 검증합니다.
 */
public class DefaultEventValidator implements EventValidator {

    @Override
    public void validate(EventEnvelope<?> event) {
        EventValidator.validateDefault(event);
    }
}
