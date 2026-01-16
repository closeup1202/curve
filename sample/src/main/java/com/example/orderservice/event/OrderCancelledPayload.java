package com.example.orderservice.event;

import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 주문 취소 이벤트 페이로드
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PayloadSchema(name = "OrderCancelled", version = 1)
public class OrderCancelledPayload implements DomainEventPayload {

    /**
     * 주문 ID
     */
    private String orderId;

    /**
     * 고객 ID
     */
    private String customerId;

    /**
     * 취소 사유
     */
    private String reason;

    @Override
    public EventType getEventType() {
        return () -> "ORDER_CANCELLED";
    }
}
