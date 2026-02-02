package com.example.userservice.controller;

import com.example.userservice.dto.LoginRequest;
import com.example.userservice.dto.RegisterUserRequest;
import com.example.userservice.dto.UserResponse;
import com.example.userservice.event.UserLoginPayload;
import com.example.userservice.event.UserRegisteredPayload;
import com.example.userservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user operations.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>User registration with PII protection</li>
 *   <li>Login with security audit logging</li>
 *   <li>IP address and user agent capture</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Register a new user.
     *
     * @param request registration request
     * @return user response with registration result
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterUserRequest request) {
        try {
            UserRegisteredPayload result = userService.registerUser(
                    request.getName(),
                    request.getEmail(),
                    request.getPhone(),
                    request.getPassword(),
                    "API"
            );

            return ResponseEntity.ok(UserResponse.builder()
                    .userId(result.getUserId())
                    .name(result.getName())
                    .email(result.getEmail())
                    .role(result.getRole())
                    .success(true)
                    .message("User registered successfully")
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(UserResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    /**
     * User login.
     *
     * @param request login request
     * @param httpRequest HTTP request for IP and user agent extraction
     * @return user response with login result
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        UserLoginPayload result = userService.login(
                request.getEmail(),
                request.getPassword(),
                ipAddress,
                userAgent
        );

        if (!result.isSuccess()) {
            return ResponseEntity.status(401).body(UserResponse.builder()
                    .success(false)
                    .message("Login failed: " + result.getFailureReason())
                    .build());
        }

        return ResponseEntity.ok(UserResponse.builder()
                .userId(result.getUserId())
                .email(result.getEmail())
                .success(true)
                .message("Login successful")
                .build());
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
