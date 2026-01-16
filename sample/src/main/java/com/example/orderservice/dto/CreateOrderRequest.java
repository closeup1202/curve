package com.example.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주문 생성 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    /**
     * 고객 ID
     */
    private String customerId;

    /**
     * 고객 이름
     */
    private String customerName;

    /**
     * 이메일
     */
    private String email;

    /**
     * 전화번호
     */
    private String phone;

    /**
     * 배송 주소
     */
    private String address;

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
}
