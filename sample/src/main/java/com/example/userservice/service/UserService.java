package com.example.userservice.service;

import com.example.userservice.domain.User;
import com.example.userservice.event.UserLoginPayload;
import com.example.userservice.event.UserRegisteredPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.audit.annotation.PublishEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User Service demonstrating advanced Curve features.
 *
 * <p>Key demonstrations:
 * <ul>
 *   <li>PII protection with ENCRYPT strategy for reversible protection</li>
 *   <li>failOnError=true for critical operations (registration must be audited)</li>
 *   <li>Different event severities (INFO for success, WARN for failures)</li>
 *   <li>Security audit logging for login attempts</li>
 * </ul>
 */
@Slf4j
@Service
public class UserService {

    private final Map<String, User> userStorage = new ConcurrentHashMap<>();

    /**
     * Register a new user.
     *
     * <p><b>CRITICAL:</b> failOnError=true ensures that user registration
     * is always recorded in the audit log. If event publishing fails,
     * the entire operation is rolled back to maintain audit trail integrity.
     *
     * @param name user's full name
     * @param email user's email address
     * @param phone user's phone number
     * @param password user's password (will be hashed)
     * @param registrationSource where the user registered from (e.g., "WEB", "API", "MOBILE")
     * @return registration event payload
     */
    @PublishEvent(
            eventType = "USER_REGISTERED",
            severity = EventSeverity.INFO,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,
            failOnError = true  // Critical: registration MUST be audited
    )
    public UserRegisteredPayload registerUser(
            String name,
            String email,
            String phone,
            String password,
            String registrationSource
    ) {
        log.info("Registering user: email={}, source={}", email, registrationSource);

        // Validate email uniqueness
        boolean emailExists = userStorage.values().stream()
                .anyMatch(u -> u.getEmail().equals(email));
        if (emailExists) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        String userId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        User user = User.builder()
                .userId(userId)
                .name(name)
                .email(email)
                .phone(phone)
                .passwordHash(hashPassword(password))
                .role("USER")
                .active(true)
                .createdAt(now)
                .build();

        userStorage.put(userId, user);
        log.info("User registered successfully: userId={}", userId);

        return UserRegisteredPayload.builder()
                .userId(userId)
                .name(name)
                .email(email)
                .phone(phone)
                .role("USER")
                .registeredAt(now)
                .registrationSource(registrationSource)
                .build();
    }

    /**
     * User login.
     *
     * <p>Security logging with:
     * <ul>
     *   <li>INFO severity for successful logins</li>
     *   <li>failOnError=false (login should work even if audit fails)</li>
     * </ul>
     *
     * @param email user's email
     * @param password user's password
     * @param ipAddress client IP address
     * @param userAgent client user agent
     * @return login event payload with success/failure status
     */
    @PublishEvent(
            eventType = "USER_LOGIN",
            severity = EventSeverity.INFO,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,
            failOnError = false  // Login should succeed even if audit fails
    )
    public UserLoginPayload login(
            String email,
            String password,
            String ipAddress,
            String userAgent
    ) {
        log.info("Login attempt: email={}, ip={}", email, ipAddress);

        Instant now = Instant.now();

        // Find user by email
        User user = userStorage.values().stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst()
                .orElse(null);

        if (user == null) {
            log.warn("Login failed: user not found, email={}, ip={}", email, ipAddress);
            return UserLoginPayload.builder()
                    .email(email)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(false)
                    .failureReason("USER_NOT_FOUND")
                    .loginAt(now)
                    .build();
        }

        // Verify password
        if (!verifyPassword(password, user.getPasswordHash())) {
            log.warn("Login failed: invalid password, userId={}, ip={}", user.getUserId(), ipAddress);
            return UserLoginPayload.builder()
                    .userId(user.getUserId())
                    .email(email)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .success(false)
                    .failureReason("INVALID_PASSWORD")
                    .loginAt(now)
                    .build();
        }

        // Update last login time
        user.setLastLoginAt(now);
        log.info("Login successful: userId={}", user.getUserId());

        return UserLoginPayload.builder()
                .userId(user.getUserId())
                .email(email)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(true)
                .loginAt(now)
                .build();
    }

    /**
     * Record failed login attempt with WARN severity.
     *
     * <p>Separate method for explicit failed login tracking,
     * useful for security monitoring and rate limiting.
     *
     * @param email attempted email
     * @param ipAddress client IP address
     * @param userAgent client user agent
     * @param reason failure reason
     * @return login failure payload
     */
    @PublishEvent(
            eventType = "USER_LOGIN_FAILED",
            severity = EventSeverity.WARN,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,
            failOnError = false
    )
    public UserLoginPayload recordFailedLogin(
            String email,
            String ipAddress,
            String userAgent,
            String reason
    ) {
        log.warn("Recording failed login: email={}, ip={}, reason={}", email, ipAddress, reason);

        return UserLoginPayload.builder()
                .email(email)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(false)
                .failureReason(reason)
                .loginAt(Instant.now())
                .build();
    }

    /**
     * Find user by ID.
     *
     * @param userId user ID
     * @return user or null if not found
     */
    public User findById(String userId) {
        return userStorage.get(userId);
    }

    private String hashPassword(String password) {
        // Simplified hash - use BCrypt in production
        return "hashed_" + password.hashCode();
    }

    private boolean verifyPassword(String password, String hash) {
        return hash.equals(hashPassword(password));
    }
}
