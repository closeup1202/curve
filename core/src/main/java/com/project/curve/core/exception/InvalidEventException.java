package com.project.curve.core.exception;

/**
 * 유효하지 않은 이벤트일 때 발생하는 예외.
 * <p>
 * 필수 필드가 누락되었거나, 비즈니스 규칙에 위배되는 이벤트 데이터가 포함된 경우 발생합니다.
 * 주로 {@link com.project.curve.core.validation.EventValidator}에서 발생시킵니다.
 */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }
}
