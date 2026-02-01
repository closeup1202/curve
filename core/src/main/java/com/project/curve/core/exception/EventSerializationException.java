package com.project.curve.core.exception;

/**
 * 이벤트 직렬화 실패 시 발생하는 예외.
 * <p>
 * EventEnvelope를 특정 포맷(JSON, Avro 등)으로 직렬화할 때 발생합니다.
 * 주로 EventProducer 구현체에서 발생시킵니다.
 */
public class EventSerializationException extends RuntimeException {

    public EventSerializationException(String message) {
        super(message);
    }

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
