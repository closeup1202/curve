package com.project.curve.core.validation;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.InvalidEventException;

/**
 * 이벤트 유효성 검증 인터페이스.
 * <p>
 * 구현체는 이벤트의 구조적 유효성, 비즈니스 규칙 등을 검증합니다.
 */
public interface EventValidator {

    /**
     * 이벤트 유효성 검증.
     *
     * @param event 검증할 이벤트
     * @throws InvalidEventException 유효하지 않은 경우
     */
    void validate(EventEnvelope<?> event);

    /**
     * 기본 검증 로직을 수행하는 정적 메서드 (하위 호환성 및 기본 구현용).
     */
    static void validateDefault(EventEnvelope<?> event) {
        if (event == null) {
            throw new InvalidEventException("event must not be null");
        }
        if (event.occurredAt().isAfter(event.publishedAt())) {
            throw new InvalidEventException("occurredAt must be <= publishedAt");
        }
    }
}
