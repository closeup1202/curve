package com.example.orderservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Order domain model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    /**
     * Order ID
     */
    private String orderId;

    /**
     * Customer information
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

    /**
     * Order creation time
     */
    private Instant createdAt;

    /**
     * Order update time
     */
    private Instant updatedAt;
}
