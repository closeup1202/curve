package com.project.curve.core.exception;

/**
 * 이벤트 직렬화 실패 예외
 * <p>
 * EventEnvelope을 특정 형식(JSON, Avro 등)으로 직렬화할 때 발생하는 예외
 * 주로 EventProducer 구현체에서 발생
 */
public class EventSerializationException extends RuntimeException {

    public EventSerializationException(String message) {
        super(message);
    }

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
