package com.example.orderservice.domain;

/**
 * 주문 상태
 */
public enum OrderStatus {
    /**
     * 주문 대기 중
     */
    PENDING,

    /**
     * 결제 완료
     */
    PAID,

    /**
     * 배송 중
     */
    SHIPPED,

    /**
     * 배송 완료
     */
    DELIVERED,

    /**
     * 주문 취소
     */
    CANCELLED
}
