package com.example.orderservice.event;

import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order cancelled event payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PayloadSchema(name = "OrderCancelled", version = 1)
public class OrderCancelledPayload implements DomainEventPayload {

    /**
     * Order ID
     */
    private String orderId;

    /**
     * Customer ID
     */
    private String customerId;

    /**
     * Cancellation reason
     */
    private String reason;

    @Override
    public EventType getEventType() {
        return () -> "ORDER_CANCELLED";
    }
}
