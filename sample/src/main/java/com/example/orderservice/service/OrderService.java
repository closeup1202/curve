package com.example.orderservice.service;

import com.example.orderservice.domain.Customer;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderStatus;
import com.example.orderservice.event.OrderCancelledPayload;
import com.example.orderservice.event.OrderCreatedPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.spring.audit.annotation.PublishEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Order Service
 * - Automatic event publishing with @PublishEvent annotation
 * - PII data is automatically masked/encrypted
 */
@Slf4j
@Service
public class OrderService {

    // Simple in-memory storage (actual implementation would use a database)
    private final Map<String, Order> orderStorage = new ConcurrentHashMap<>();

    /**
     * Create order
     * - AFTER_RETURNING: Publishes event after method completes successfully
     * - payloadIndex = -1: Uses return value (Order) as payload
     *
     * @param customer Customer information
     * @param productName Product name
     * @param quantity Quantity
     * @param totalAmount Total amount
     * @return Created order
     */
    @PublishEvent(
            eventType = "ORDER_CREATED",
            severity = EventSeverity.INFO,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,  // Use return value
            failOnError = false  // Business logic continues even if event publishing fails
    )
    public OrderCreatedPayload createOrder(
            Customer customer,
            String productName,
            int quantity,
            BigDecimal totalAmount
    ) {
        log.info("Creating order: customer={}, product={}, quantity={}, amount={}",
                customer.getCustomerId(), productName, quantity, totalAmount);

        // Create order
        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Order order = Order.builder()
                .orderId(orderId)
                .customer(customer)
                .productName(productName)
                .quantity(quantity)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Save
        orderStorage.put(orderId, order);

        log.info("Order created successfully: orderId={}", orderId);

        // Return event payload (automatically published to Kafka)
        return OrderCreatedPayload.builder()
                .orderId(orderId)
                .customer(customer)
                .productName(productName)
                .quantity(quantity)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();
    }

    /**
     * Cancel order
     * - AFTER: Always publishes event after method completes (even if exception occurs)
     * - payloadIndex = 1: Includes second parameter (reason) in payload
     *
     * @param orderId Order ID
     * @param reason Cancellation reason
     * @return Cancelled order
     */
    @PublishEvent(
            eventType = "ORDER_CANCELLED",
            severity = EventSeverity.WARN,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,
            failOnError = false
    )
    public OrderCancelledPayload cancelOrder(String orderId, String reason) {
        log.info("Cancelling order: orderId={}, reason={}", orderId, reason);

        Order order = orderStorage.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order already cancelled: " + orderId);
        }

        // Process order cancellation
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());

        log.info("Order cancelled successfully: orderId={}", orderId);

        // Return event payload
        return OrderCancelledPayload.builder()
                .orderId(orderId)
                .customerId(order.getCustomer().getCustomerId())
                .reason(reason)
                .build();
    }

    /**
     * Retrieve order
     *
     * @param orderId Order ID
     * @return Order information
     */
    public Order getOrder(String orderId) {
        Order order = orderStorage.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        return order;
    }

    /**
     * Update order status
     * - BEFORE: Publishes event before method execution
     *
     * @param orderId Order ID
     * @param newStatus New status
     */
    @PublishEvent(
            eventType = "ORDER_STATUS_CHANGED",
            severity = EventSeverity.INFO,
            phase = PublishEvent.Phase.BEFORE,
            payloadIndex = 0,  // Use first parameter (orderId)
            failOnError = false
    )
    public void updateOrderStatus(String orderId, OrderStatus newStatus) {
        log.info("Updating order status: orderId={}, newStatus={}", orderId, newStatus);

        Order order = orderStorage.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());

        log.info("Order status updated: orderId={}, {} -> {}", orderId, oldStatus, newStatus);
    }

    /**
     * Create order (using Transactional Outbox Pattern).
     * <p>
     * Set outbox=true to ensure atomicity between DB transaction and event publishing.
     * <p>
     * - DB save and Outbox event save are performed in the same transaction.
     * - A separate scheduler publishes PENDING events from the Outbox table to Kafka.
     * - No data loss even if Kafka is down.
     *
     * @param customer Customer information
     * @param productName Product name
     * @param quantity Quantity
     * @param totalAmount Total amount
     * @return Created order payload
     */
    @PublishEvent(
            eventType = "ORDER_CREATED_WITH_OUTBOX",
            severity = EventSeverity.INFO,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,  // Use return value
            failOnError = false,
            // Transactional Outbox Pattern configuration
            outbox = true,
            aggregateType = "Order",
            aggregateId = "#result.orderId"  // SpEL: orderId field of return value
    )
    public OrderCreatedPayload createOrderWithOutbox(
            Customer customer,
            String productName,
            int quantity,
            BigDecimal totalAmount
    ) {
        log.info("[OUTBOX] Creating order: customer={}, product={}, quantity={}, amount={}",
                customer.getCustomerId(), productName, quantity, totalAmount);

        // Create order
        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Order order = Order.builder()
                .orderId(orderId)
                .customer(customer)
                .productName(productName)
                .quantity(quantity)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Save
        orderStorage.put(orderId, order);

        log.info("[OUTBOX] Order created successfully: orderId={}", orderId);
        log.info("[OUTBOX] Event will be saved to outbox table in the same transaction");
        log.info("[OUTBOX] OutboxEventPublisher will publish it to Kafka asynchronously");

        // Return event payload
        return OrderCreatedPayload.builder()
                .orderId(orderId)
                .customer(customer)
                .productName(productName)
                .quantity(quantity)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .build();
    }

    /**
     * Update customer information (for SpEL testing)
     * <p>
     * Uses SpEL to extract only specific fields from parameters as event payload.
     *
     * @param customerId Customer ID
     * @param newEmail New email
     */
    @PublishEvent(
            eventType = "CUSTOMER_EMAIL_UPDATED",
            payload = "#newEmail" // SpEL: Access by parameter name
    )
    public void updateCustomerEmail(String customerId, String newEmail) {
        log.info("Updating customer email: customerId={}, newEmail={}", customerId, newEmail);
        // Actual logic omitted
    }
}
