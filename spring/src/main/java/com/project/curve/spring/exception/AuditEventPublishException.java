package com.project.curve.spring.exception;

/**
 * @Auditable 어노테이션을 통한 이벤트 발행 실패 시 발생하는 예외
 * <p>
 * failOnError=true로 설정된 경우에만 발생
 */
public class AuditEventPublishException extends RuntimeException {

    public AuditEventPublishException(String message) {
        super(message);
    }

    public AuditEventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
