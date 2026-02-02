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
 * Payment failure event payload.
 *
 * <p>Critical for:
 * <ul>
 *   <li>Order cancellation</li>
 *   <li>Customer notification</li>
 *   <li>Retry logic and monitoring</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PayloadSchema(name = "PaymentFailure", version = 1)
public class PaymentFailurePayload implements DomainEventPayload {

    private String paymentId;
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String failureReason;
    private String errorCode;
    private int retryCount;
    private Instant failedAt;

    @Override
    public EventType getEventType() {
        return () -> "PAYMENT_FAILED";
    }
}
