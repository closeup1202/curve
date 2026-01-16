package com.example.orderservice.domain;

import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.PiiType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 고객 정보 (PII 데이터 포함)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    /**
     * 고객 ID
     */
    private String customerId;

    /**
     * 고객 이름 (마스킹 처리)
     */
    @PiiField(type = PiiType.NAME, strategy = PiiStrategy.MASK)
    private String name;

    /**
     * 이메일 (마스킹 처리)
     */
    @PiiField(type = PiiType.EMAIL, strategy = PiiStrategy.MASK)
    private String email;

    /**
     * 전화번호 (암호화 처리)
     */
    @PiiField(type = PiiType.PHONE, strategy = PiiStrategy.ENCRYPT)
    private String phone;

    /**
     * 배송 주소 (마스킹 처리)
     */
    @PiiField(strategy = PiiStrategy.MASK)
    private String address;
}
