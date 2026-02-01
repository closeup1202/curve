package com.example.orderservice.dto;

import com.example.orderservice.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Order response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    /**
     * Order ID
     */
    private String orderId;

    /**
     * Customer ID
     */
    private String customerId;

    /**
     * Customer name
     */
    private String customerName;

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

    /**
     * Order creation time
     */
    private Instant createdAt;

    /**
     * Order update time
     */
    private Instant updatedAt;
}
