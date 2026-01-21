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
 * 주문 서비스
 * - @PublishEvent 어노테이션으로 자동 이벤트 발행
 * - PII 데이터는 자동으로 마스킹/암호화
 */
@Slf4j
@Service
public class OrderService {

    // 간단한 인메모리 저장소 (실제로는 DB 사용)
    private final Map<String, Order> orderStorage = new ConcurrentHashMap<>();

    /**
     * 주문 생성
     * - AFTER_RETURNING: 메서드 정상 완료 후 이벤트 발행
     * - payloadIndex = -1: 반환값(Order)을 페이로드로 사용
     *
     * @param customer 고객 정보
     * @param productName 상품명
     * @param quantity 수량
     * @param totalAmount 총 금액
     * @return 생성된 주문
     */
    @PublishEvent(
            eventType = "ORDER_CREATED",
            severity = EventSeverity.INFO,
            phase = PublishEvent.Phase.AFTER_RETURNING,
            payloadIndex = -1,  // 반환값 사용
            failOnError = false  // 이벤트 발행 실패해도 비즈니스 로직은 계속 진행
    )
    public OrderCreatedPayload createOrder(
            Customer customer,
            String productName,
            int quantity,
            BigDecimal totalAmount
    ) {
        log.info("Creating order: customer={}, product={}, quantity={}, amount={}",
                customer.getCustomerId(), productName, quantity, totalAmount);

        // 주문 생성
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

        // 저장
        orderStorage.put(orderId, order);

        log.info("Order created successfully: orderId={}", orderId);

        // 이벤트 페이로드 반환 (자동으로 Kafka에 발행됨)
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
     * 주문 취소
     * - AFTER: 메서드 완료 후 항상 이벤트 발행 (예외 발생해도)
     * - payloadIndex = 1: 두 번째 파라미터(reason)를 페이로드에 포함
     *
     * @param orderId 주문 ID
     * @param reason 취소 사유
     * @return 취소된 주문
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

        // 주문 취소 처리
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());

        log.info("Order cancelled successfully: orderId={}", orderId);

        // 이벤트 페이로드 반환
        return OrderCancelledPayload.builder()
                .orderId(orderId)
                .customerId(order.getCustomer().getCustomerId())
                .reason(reason)
                .build();
    }

    /**
     * 주문 조회
     *
     * @param orderId 주문 ID
     * @return 주문 정보
     */
    public Order getOrder(String orderId) {
        Order order = orderStorage.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        return order;
    }

    /**
     * 주문 상태 업데이트
     * - BEFORE: 메서드 실행 전 이벤트 발행
     *
     * @param orderId 주문 ID
     * @param newStatus 새로운 상태
     */
    @PublishEvent(
            eventType = "ORDER_STATUS_CHANGED",
            severity = EventSeverity.INFO,
            phase = PublishEvent.Phase.BEFORE,
            payloadIndex = 0,  // 첫 번째 파라미터(orderId) 사용
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
}
