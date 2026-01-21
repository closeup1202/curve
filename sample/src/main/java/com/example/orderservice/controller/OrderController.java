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
 * 주문 REST API 컨트롤러
 * - Curve를 통해 모든 주문 이벤트가 자동으로 Kafka에 발행됨
 * - PII 데이터는 자동으로 마스킹/암호화됨
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

        // Customer 객체 생성 (PII 필드 포함)
        Customer customer = Customer.builder()
                .customerId(request.getCustomerId())
                .name(request.getCustomerName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .build();

        // 주문 생성 (자동으로 ORDER_CREATED 이벤트 발행)
        OrderCreatedPayload payload = orderService.createOrder(
                customer,
                request.getProductName(),
                request.getQuantity(),
                request.getTotalAmount()
        );

        // 응답 생성
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

        // 주문 취소 (자동으로 ORDER_CANCELLED 이벤트 발행)
        OrderCancelledPayload payload = orderService.cancelOrder(orderId, request.getReason());

        // 업데이트된 주문 조회
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

        // 주문 상태 업데이트 (자동으로 ORDER_STATUS_CHANGED 이벤트 발행)
        orderService.updateOrderStatus(orderId, newStatus);

        // 업데이트된 주문 조회
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
