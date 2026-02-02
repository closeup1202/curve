package com.example.paymentservice.event;

import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment success event payload.
 *
 * <p>Critical for:
 * <ul>
 *   <li>Order fulfillment</li>
 *   <li>Financial reconciliation</li>
 *   <li>Analytics and reporting</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PayloadSchema(name = "PaymentSuccess", version = 1)
public class PaymentSuccessPayload implements DomainEventPayload {

    private String paymentId;
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String transactionId;
    private Instant processedAt;

    @Override
    public EventType getEventType() {
        return () -> "PAYMENT_SUCCESS";
    }
}
