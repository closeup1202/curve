package com.example.orderservice.domain;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.PiiType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer information (includes PII data)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    /**
     * Customer ID
     */
    private String customerId;

    /**
     * Customer name (masked)
     */
    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
    private String name;

    /**
     * Email (masked)
     */
    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;

    /**
     * Phone number (encrypted)
     */
    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;

    /**
     * Shipping address (masked)
     */
    @PiiField(strategy = PiiStrategy.MASK)
    private String address;
}
