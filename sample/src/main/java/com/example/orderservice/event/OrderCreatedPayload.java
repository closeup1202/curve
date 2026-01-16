package com.example.orderservice.event;

import com.example.orderservice.domain.Customer;
import com.example.orderservice.domain.OrderStatus;
import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주문 생성 이벤트 페이로드
 * - PII 데이터(Customer)는 자동으로 마스킹/암호화됨
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@PayloadSchema(name = "OrderCreated", version = 1)
public class OrderCreatedPayload implements DomainEventPayload {

    /**
     * 주문 ID
     */
    private String orderId;

    /**
     * 고객 정보 (PII 필드 포함)
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

    @Override
    public EventType getEventType() {
        return () -> "ORDER_CREATED";
    }
}
