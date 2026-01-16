package com.example.orderservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 주문 도메인 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    /**
     * 주문 ID
     */
    private String orderId;

    /**
     * 고객 정보
     */
    private Customer customer;

    /**
     * 상품명
     */
    private String productName;

    /**
     * 수량
     */
    private int quantity;

    /**
     * 총 금액
     */
    private BigDecimal totalAmount;

    /**
     * 주문 상태
     */
    private OrderStatus status;

    /**
     * 주문 생성 시간
     */
    private Instant createdAt;

    /**
     * 주문 업데이트 시간
     */
    private Instant updatedAt;
}
