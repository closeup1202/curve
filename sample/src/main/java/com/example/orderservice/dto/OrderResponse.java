package com.example.orderservice.dto;

import com.example.orderservice.domain.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 주문 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    /**
     * 주문 ID
     */
    private String orderId;

    /**
     * 고객 ID
     */
    private String customerId;

    /**
     * 고객 이름
     */
    private String customerName;

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
