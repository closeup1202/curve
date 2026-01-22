package com.project.curve.spring.audit.annotation;

import com.project.curve.core.type.EventSeverity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 실행 시 자동으로 이벤트를 발행하는 어노테이션
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublishEvent {

    /**
     * 이벤트 타입 이름
     * 기본값: 메서드 이름을 기반으로 생성
     */
    String eventType() default "";

    /**
     * 이벤트 심각도
     */
    EventSeverity severity() default EventSeverity.INFO;

    /**
     * 이벤트 페이로드로 사용할 파라미터 인덱스
     * -1: 반환값 사용 (기본값)
     * 0 이상: 해당 인덱스의 파라미터 사용
     */
    int payloadIndex() default -1;

    /**
     * 이벤트 발행 시점
     */
    Phase phase() default Phase.AFTER_RETURNING;

    /**
     * 이벤트 발행 실패 시 예외 전파 여부
     * <p>
     * true: 이벤트 발행 실패 시 예외를 던져 비즈니스 로직도 실패
     * false: 이벤트 발행 실패해도 비즈니스 로직은 정상 진행 (기본값)
     */
    boolean failOnError() default false;

    /**
     * Transactional Outbox Pattern 사용 여부.
     * <p>
     * true: DB 트랜잭션 내에 Outbox 테이블에 이벤트를 먼저 저장하고,
     * 별도 스케줄러가 Kafka로 발행하여 원자성 보장
     * <p>
     * false: 즉시 Kafka로 발행 (기본값)
     *
     * <h3>사용 예시</h3>
     * <pre>
     * @Transactional
     * @PublishEvent(
     *     eventType = "ORDER_CREATED",
     *     outbox = true,
     *     aggregateType = "Order",
     *     aggregateId = "#result.orderId"
     * )
     * public Order createOrder(OrderRequest req) {
     *     return orderRepo.save(new Order(req));
     * }
     * </pre>
     */
    boolean outbox() default false;

    /**
     * 집합체(Aggregate) 타입.
     * <p>
     * Outbox 패턴에서 이벤트 그룹화 및 순서 보장에 사용됩니다.
     * <p>
     * 예: "Order", "User", "Payment"
     * <p>
     * outbox=true일 때 필수 항목입니다.
     */
    String aggregateType() default "";

    /**
     * 집합체(Aggregate) ID 추출 SpEL 표현식.
     * <p>
     * 메서드 파라미터나 반환값에서 aggregateId를 추출합니다.
     *
     * <h3>사용 가능한 변수</h3>
     * <ul>
     *   <li>#result - 메서드 반환값 (AFTER_RETURNING 시)</li>
     *   <li>#args[0], #args[1] - 메서드 파라미터</li>
     *   <li>#orderId - 파라미터 이름으로 직접 접근</li>
     * </ul>
     *
     * <h3>예시</h3>
     * <pre>
     * // 반환값에서 추출
     * aggregateId = "#result.orderId"
     *
     * // 파라미터에서 추출
     * aggregateId = "#orderId"
     * aggregateId = "#args[0]"
     *
     * // 중첩 속성
     * aggregateId = "#result.order.orderId"
     * </pre>
     *
     * outbox=true일 때 필수 항목입니다.
     */
    String aggregateId() default "";

    /**
     * 이벤트 발행 시점
     */
    enum Phase {
        // 메서드 실행 전
        BEFORE,

        // 메서드 정상 반환 후
        AFTER_RETURNING,

        // 메서드 실행 후 (예외 발생 여부 무관)
        AFTER
    }
}
