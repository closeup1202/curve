package com.project.curve.kafka.producer;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;

/**
 * 테스트용 이벤트 페이로드
 */
public record TestEventPayload(String orderId, String productName, int amount) implements DomainEventPayload {

    @Override
    public EventType getEventType() {
        return () -> "TEST_ORDER_CREATED";
    }
}
