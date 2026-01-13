package com.project.curve.spring.payload;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import lombok.Getter;

/**
 * @Auditable 어노테이션을 통해 자동 생성되는 감사 이벤트 페이로드
 *
 * 메서드 실행 정보와 함께 실제 데이터를 포함합니다.
 */
@Getter
public class AuditEventPayload implements DomainEventPayload {

    private final String eventTypeName;
    private final String className;
    private final String methodName;
    private final Object data;

    public AuditEventPayload(String eventTypeName, String className, String methodName, Object data) {
        this.eventTypeName = eventTypeName;
        this.className = className;
        this.methodName = methodName;
        this.data = data;
    }

    @Override
    public EventType getEventType() {
        return new AuditEventType(eventTypeName);
    }

    /**
     * 감사 이벤트 타입
     */
    private record AuditEventType(String name) implements EventType {
        @Override
        public String name() {
            return name;
        }
    }
}
