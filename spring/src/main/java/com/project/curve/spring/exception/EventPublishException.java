package com.project.curve.spring.exception;

/**
 * @PublishEvent 어노테이션을 통한 이벤트 발행 실패 시 발생하는 예외
 * <p>
 * failOnError=true로 설정된 경우에만 발생
 */
public class EventPublishException extends RuntimeException {

    public EventPublishException(String message) {
        super(message);
    }

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
