package com.example.orderservice.domain;

/**
 * Order status
 */
public enum OrderStatus {
    /**
     * Order pending
     */
    PENDING,

    /**
     * Payment completed
     */
    PAID,

    /**
     * Shipping in progress
     */
    SHIPPED,

    /**
     * Delivery completed
     */
    DELIVERED,

    /**
     * Order cancelled
     */
    CANCELLED
}
