package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.dto.ProcessPaymentRequest;
import com.example.paymentservice.event.PaymentSuccessPayload;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for payment operations.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>Critical payment processing with Outbox pattern</li>
 *   <li>Retry logic for transient failures</li>
 *   <li>Error handling with event publishing</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Process a payment.
     *
     * @param request payment request
     * @return payment response with result
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody ProcessPaymentRequest request) {
        try {
            PaymentSuccessPayload result = paymentService.processPayment(
                    request.getOrderId(),
                    request.getCustomerId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getPaymentMethod()
            );

            return ResponseEntity.ok(PaymentResponse.builder()
                    .paymentId(result.getPaymentId())
                    .orderId(result.getOrderId())
                    .transactionId(result.getTransactionId())
                    .status("SUCCESS")
                    .message("Payment processed successfully")
                    .build());
        } catch (PaymentService.PaymentGatewayException e) {
            return ResponseEntity.status(502).body(PaymentResponse.builder()
                    .orderId(request.getOrderId())
                    .status("FAILED")
                    .message("Payment gateway error: " + e.getMessage())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(PaymentResponse.builder()
                    .orderId(request.getOrderId())
                    .status("ERROR")
                    .message("Unexpected error: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Process a payment with automatic retry.
     *
     * @param request payment request
     * @param maxRetries maximum retry attempts (default: 3)
     * @return payment response with result
     */
    @PostMapping("/with-retry")
    public ResponseEntity<PaymentResponse> processPaymentWithRetry(
            @RequestBody ProcessPaymentRequest request,
            @RequestParam(defaultValue = "3") int maxRetries
    ) {
        try {
            PaymentSuccessPayload result = paymentService.processPaymentWithRetry(
                    request.getOrderId(),
                    request.getCustomerId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getPaymentMethod(),
                    maxRetries
            );

            return ResponseEntity.ok(PaymentResponse.builder()
                    .paymentId(result.getPaymentId())
                    .orderId(result.getOrderId())
                    .transactionId(result.getTransactionId())
                    .status("SUCCESS")
                    .message("Payment processed successfully")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(PaymentResponse.builder()
                    .orderId(request.getOrderId())
                    .status("FAILED")
                    .message(e.getMessage())
                    .build());
        }
    }
}
