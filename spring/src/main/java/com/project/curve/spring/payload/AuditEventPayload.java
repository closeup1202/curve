package com.project.curve.spring.payload;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import com.project.curve.spring.type.AuditEventType;

/**
 * @Auditable 어노테이션을 통해 자동 생성되는 감사 이벤트 페이로드
 */
public record AuditEventPayload(
        String eventTypeName,
        String className,
        String methodName,
        Object data) implements DomainEventPayload {

    @Override
    public EventType getEventType() {
        return new AuditEventType(eventTypeName);
    }
}
