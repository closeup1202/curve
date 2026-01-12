package com.project.curve.core.port;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;

public interface EventProducer {
    // 기본 발행 (Severity: INFO)
    <T extends DomainEventPayload> void publish(T payload);

    // 심각도를 지정한 발행
    <T extends DomainEventPayload> void publish(T payload, EventSeverity severity);
}
