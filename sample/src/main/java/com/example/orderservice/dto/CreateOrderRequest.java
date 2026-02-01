package com.example.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Create order request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    /**
     * Customer ID
     */
    private String customerId;

    /**
     * Customer name
     */
    private String customerName;

    /**
     * Email
     */
    private String email;

    /**
     * Phone number
     */
    private String phone;

    /**
     * Shipping address
     */
    private String address;

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
}
