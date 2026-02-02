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
 * User login event payload.
 *
 * <p>Demonstrates security audit logging:
 * <ul>
 *   <li>Email: HASH for security audit (can match but not reverse)</li>
 *   <li>IP address: Captured for security monitoring</li>
 *   <li>Success/failure tracking for authentication analysis</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PayloadSchema(name = "UserLogin", version = 1)
public class UserLoginPayload implements DomainEventPayload {

    private String userId;

    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.HASH)
    private String email;

    private String ipAddress;
    private String userAgent;
    private boolean success;
    private String failureReason;
    private Instant loginAt;

    @Override
    public EventType getEventType() {
        return () -> "USER_LOGIN";
    }
}
