package com.example.userservice.event;

import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.PiiType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User registration event payload.
 *
 * <p>Demonstrates comprehensive PII protection:
 * <ul>
 *   <li>Email: ENCRYPT for reversible protection (may need for verification)</li>
 *   <li>Phone: ENCRYPT for reversible protection</li>
 *   <li>Name: MASK for display purposes</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PayloadSchema(name = "UserRegistered", version = 1)
public class UserRegisteredPayload implements DomainEventPayload {

    private String userId;

    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
    private String name;

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.ENCRYPT)
    private String email;

    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;

    private String role;
    private Instant registeredAt;
    private String registrationSource;

    @Override
    public EventType getEventType() {
        return () -> "USER_REGISTERED";
    }
}
