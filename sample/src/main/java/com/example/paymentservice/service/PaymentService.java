package com.example.paymentservice.service;

import com.example.paymentservice.domain.Payment;
import com.example.paymentservice.domain.PaymentStatus;
import com.example.paymentservice.event.PaymentFailurePayload;
import com.example.paymentservice.event.PaymentSuccessPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.audit.annotation.PublishEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Payment Service demonstrating advanced Curve features for critical operations.
 *
 * <p>Key demonstrations:
 * <ul>
 *   <li>Synchronous mode for critical financial events</li>
 *   <li>failOnError=true for payment success (MUST be recorded)</li>
 *   <li>Transactional Outbox for guaranteed delivery</li>
 *   <li>Error handling with automatic retry</li>
 *   <li>Different event severities (INFO for success, ERROR for failure)</li>
 * </ul>
 */
@Slf4j
@Service
public class PaymentService {

    private final Map<String, Payment> paymentStorage = new ConcurrentHashMap<>();

    /**
     * Process payment - CRITICAL OPERATION.
     *
     * <p><b>Key configurations:</b>
     * <ul>
     *   <li>failOnError=true: Payment success MUST be recorded for financial compliance</li>
     *   <li>outbox=true: Guaranteed delivery even if Kafka is temporarily unavailable</li>
     *   <li>CRITICAL severity: Highest priority for monitoring and alerting</li>
     * </ul>
     *
     * <p>If event publishing fails, the payment is rolled back to ensure
     * financial audit trail integrity.
     *
     * @param orderId associated order ID
     * @param customerId customer ID
     * @param amount payment amount
     * @param currency currency code (e.g., "USD", "KRW")
     * @param paymentMethod payment method (e.g., "CREDIT_CARD", "BANK_TRANSFER")
     * @return payment success payload
     */
    @PublishEvent(
            eventType = "PAYMENT_SUCCESS",
            severity = EventSeverity.CRITICAL,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,
            failOnError = true,  // Critical: payment MUST be recorded
            outbox = true,       // Guaranteed delivery
            aggregateType = "Payment",
            aggregateId = "#result.paymentId"
    )
    public PaymentSuccessPayload processPayment(
            String orderId,
            String customerId,
            BigDecimal amount,
            String currency,
            String paymentMethod
    ) {
        log.info("Processing payment: orderId={}, amount={} {}", orderId, amount, currency);

        String paymentId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // Simulate payment gateway call
        String transactionId = simulatePaymentGateway(paymentMethod, amount);

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(paymentMethod)
                .transactionId(transactionId)
                .createdAt(now)
                .processedAt(now)
                .build();

        paymentStorage.put(paymentId, payment);
        log.info("Payment processed successfully: paymentId={}, transactionId={}", paymentId, transactionId);

        return PaymentSuccessPayload.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .transactionId(transactionId)
                .processedAt(now)
                .build();
    }

    /**
     * Record payment failure.
     *
     * <p><b>Key configurations:</b>
     * <ul>
     *   <li>failOnError=false: Recording failure shouldn't prevent error handling</li>
     *   <li>ERROR severity: Triggers alerts for failed payments</li>
     * </ul>
     *
     * @param orderId associated order ID
     * @param customerId customer ID
     * @param amount payment amount
     * @param currency currency code
     * @param paymentMethod payment method
     * @param failureReason human-readable failure reason
     * @param errorCode technical error code
     * @param retryCount current retry attempt count
     * @return payment failure payload
     */
    @PublishEvent(
            eventType = "PAYMENT_FAILED",
            severity = EventSeverity.ERROR,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,
            failOnError = false  // Recording failure shouldn't fail the operation
    )
    public PaymentFailurePayload recordPaymentFailure(
            String orderId,
            String customerId,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String failureReason,
            String errorCode,
            int retryCount
    ) {
        log.error("Payment failed: orderId={}, reason={}, errorCode={}, retryCount={}",
                orderId, failureReason, errorCode, retryCount);

        String paymentId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.FAILED)
                .paymentMethod(paymentMethod)
                .failureReason(failureReason)
                .createdAt(now)
                .build();

        paymentStorage.put(paymentId, payment);

        return PaymentFailurePayload.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .paymentMethod(paymentMethod)
                .failureReason(failureReason)
                .errorCode(errorCode)
                .retryCount(retryCount)
                .failedAt(now)
                .build();
    }

    /**
     * Process payment with automatic retry on failure.
     *
     * <p>Demonstrates error handling pattern with event publishing:
     * <ul>
     *   <li>Exponential backoff between retries</li>
     *   <li>Intermediate failure events for monitoring</li>
     *   <li>Final success or max-retries-exceeded event</li>
     * </ul>
     *
     * @param orderId associated order ID
     * @param customerId customer ID
     * @param amount payment amount
     * @param currency currency code
     * @param paymentMethod payment method
     * @param maxRetries maximum retry attempts
     * @return payment success payload
     * @throws RuntimeException if all retries exhausted
     */
    public PaymentSuccessPayload processPaymentWithRetry(
            String orderId,
            String customerId,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            int maxRetries
    ) {
        int retryCount = 0;
        PaymentGatewayException lastException = null;

        while (retryCount <= maxRetries) {
            try {
                return processPayment(orderId, customerId, amount, currency, paymentMethod);
            } catch (PaymentGatewayException e) {
                lastException = e;
                retryCount++;

                if (retryCount <= maxRetries) {
                    log.warn("Payment attempt {} failed, retrying... orderId={}, error={}",
                            retryCount, orderId, e.getMessage());

                    // Record intermediate failure for analytics
                    recordPaymentFailure(orderId, customerId, amount, currency, paymentMethod,
                            e.getMessage(), e.getErrorCode(), retryCount);

                    // Exponential backoff
                    try {
                        Thread.sleep((long) Math.pow(2, retryCount) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Payment interrupted", ie);
                    }
                }
            }
        }

        // All retries exhausted
        String finalReason = "MAX_RETRIES_EXCEEDED: " +
                (lastException != null ? lastException.getMessage() : "Unknown error");
        recordPaymentFailure(orderId, customerId, amount, currency, paymentMethod,
                finalReason, "RETRY_EXHAUSTED", maxRetries);

        throw new RuntimeException("Payment failed after " + maxRetries + " retries", lastException);
    }

    /**
     * Find payment by ID.
     *
     * @param paymentId payment ID
     * @return payment or null if not found
     */
    public Payment findById(String paymentId) {
        return paymentStorage.get(paymentId);
    }

    private String simulatePaymentGateway(String paymentMethod, BigDecimal amount) {
        // Simulate occasional failures for testing (10% failure rate)
        if (Math.random() < 0.1) {
            throw new PaymentGatewayException("GATEWAY_TIMEOUT", "Payment gateway timeout");
        }

        // Generate transaction ID
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Custom exception for payment gateway errors.
     */
    @Getter
    public static class PaymentGatewayException extends RuntimeException {
        private final String errorCode;

        public PaymentGatewayException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

    }
}
