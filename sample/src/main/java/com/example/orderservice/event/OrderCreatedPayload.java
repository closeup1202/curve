package com.example.orderservice.event;

import com.example.orderservice.domain.Customer;
import com.example.orderservice.domain.OrderStatus;
import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Order created event payload
 * - PII data (Customer) is automatically masked/encrypted
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PayloadSchema(name = "OrderCreated", version = 1)
public class OrderCreatedPayload implements DomainEventPayload {

    /**
     * Order ID
     */
    private String orderId;

    /**
     * Customer information (includes PII fields)
     */
    private Customer customer;

    /**
     * Product name
     */
    private String productName;

    /**
     * Quantity
     */
    private int quantity;

    /**
     * Total amount
     */
    private BigDecimal totalAmount;

    /**
     * Order status
     */
    private OrderStatus status;

    @Override
    public EventType getEventType() {
        return () -> "ORDER_CREATED";
    }
}
