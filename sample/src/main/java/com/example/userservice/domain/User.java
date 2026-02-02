package com.example.userservice.domain;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.PiiType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User domain model with comprehensive PII protection.
 *
 * <p>Demonstrates various PII protection strategies:
 * <ul>
 *   <li>NAME: Masked for display (e.g., "John Doe" → "J*** ***")</li>
 *   <li>EMAIL: Encrypted for reversible protection</li>
 *   <li>PHONE: Encrypted for reversible protection</li>
 *   <li>Password hash: Hashed for irreversible protection</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String userId;

    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
    private String name;

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.ENCRYPT)
    private String email;

    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;

    @PiiField(strategy = PiiStrategy.HASH)
    private String passwordHash;

    private String role;
    private boolean active;
    private Instant createdAt;
    private Instant lastLoginAt;
}
