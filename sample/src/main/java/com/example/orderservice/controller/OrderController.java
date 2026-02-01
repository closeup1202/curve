package com.example.orderservice.controller;

import com.example.orderservice.domain.Customer;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderStatus;
import com.example.orderservice.dto.CancelOrderRequest;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.event.OrderCancelledPayload;
import com.example.orderservice.event.OrderCreatedPayload;
import com.example.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Order REST API Controller
 * - All order events are automatically published to Kafka through Curve
 * - PII data is automatically masked/encrypted
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        log.info("Received order creation request: customerId={}, product={}",
                request.getCustomerId(), request.getProductName());

        // Create Customer object (includes PII fields)
        Customer customer = Customer.builder()
                .customerId(request.getCustomerId())
                .name(request.getCustomerName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .build();

        // Create order (automatically publishes ORDER_CREATED event)
        OrderCreatedPayload payload = orderService.createOrder(
                customer,
                request.getProductName(),
                request.getQuantity(),
                request.getTotalAmount()
        );

        // Create response
        OrderResponse response = OrderResponse.builder()
                .orderId(payload.getOrderId())
                .customerId(customer.getCustomerId())
                .customerName(customer.getName())
                .productName(payload.getProductName())
                .quantity(payload.getQuantity())
                .totalAmount(payload.getTotalAmount())
                .status(payload.getStatus())
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        log.info("Getting order: orderId={}", orderId);

        Order order = orderService.getOrder(orderId);

        OrderResponse response = OrderResponse.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomer().getCustomerId())
                .customerName(order.getCustomer().getName())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable String orderId,
            @RequestBody CancelOrderRequest request
    ) {
        log.info("Cancelling order: orderId={}, reason={}", orderId, request.getReason());

        // Cancel order (automatically publishes ORDER_CANCELLED event)
        OrderCancelledPayload payload = orderService.cancelOrder(orderId, request.getReason());

        // Retrieve updated order
        Order order = orderService.getOrder(orderId);

        OrderResponse response = OrderResponse.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomer().getCustomerId())
                .customerName(order.getCustomer().getName())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable String orderId,
            @RequestParam OrderStatus newStatus
    ) {
        log.info("Updating order status: orderId={}, newStatus={}", orderId, newStatus);

        // Update order status (automatically publishes ORDER_STATUS_CHANGED event)
        orderService.updateOrderStatus(orderId, newStatus);

        // Retrieve updated order
        Order order = orderService.getOrder(orderId);

        OrderResponse response = OrderResponse.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomer().getCustomerId())
                .customerName(order.getCustomer().getName())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();

        return ResponseEntity.ok(response);
    }
}
