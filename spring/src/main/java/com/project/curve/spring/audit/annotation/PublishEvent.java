package com.project.curve.spring.audit.annotation;

import com.project.curve.core.type.EventSeverity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic event publishing on method execution.
 * <p>
 * This annotation enables declarative event publishing using Spring AOP. Simply add this
 * annotation to any Spring-managed bean method, and an event will be automatically published
 * when the method executes.
 * </p>
 *
 * <h3>Basic Usage:</h3>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @PublishEvent(eventType = "ORDER_CREATED")
 *     public Order createOrder(CreateOrderRequest request) {
 *         return orderRepository.save(new Order(request));
 *     }
 * }
 * }</pre>
 *
 * <h3>With SpEL for Payload Extraction:</h3>
 * <pre>{@code
 * @PublishEvent(
 *     eventType = "USER_UPDATED",
 *     payload = "#args[0].toEventDto()"
 * )
 * public User updateUser(UserUpdateRequest request) {
 *     return userRepository.save(request.toEntity());
 * }
 * }</pre>
 *
 * <h3>With Transactional Outbox:</h3>
 * <pre>{@code
 * @PublishEvent(
 *     eventType = "ORDER_CREATED",
 *     outbox = true,
 *     aggregateType = "Order",
 *     aggregateId = "#result.orderId"
 * )
 * @Transactional
 * public Order createOrder(CreateOrderRequest request) {
 *     return orderRepository.save(new Order(request));
 * }
 * }</pre>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Automatic event envelope creation with rich metadata</li>
 *   <li>SpEL support for flexible payload extraction</li>
 *   <li>Multiple execution phases (BEFORE, AFTER_RETURNING, AFTER)</li>
 *   <li>Transactional Outbox Pattern support for data consistency</li>
 *   <li>PII field masking/encryption support</li>
 *   <li>Configurable error handling</li>
 * </ul>
 *
 * @see com.project.curve.core.port.EventProducer
 * @see com.project.curve.spring.audit.aop.PublishEventAspect
 * @since 0.0.1
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
     * <p>
     * payload() 속성이 설정되어 있으면 이 값은 무시됩니다.
     */
    int payloadIndex() default -1;

    /**
     * 이벤트 페이로드 추출을 위한 SpEL 표현식.
     * <p>
     * 설정 시 payloadIndex보다 우선순위가 높습니다.
     *
     * <h3>사용 가능한 변수</h3>
     * <ul>
     *   <li>#result - 메서드 반환값 (AFTER_RETURNING 시)</li>
     *   <li>#args - 메서드 파라미터 배열</li>
     *   <li>#p0, #p1, ... - 각 파라미터</li>
     *   <li>파라미터 이름 - 컴파일 시 -parameters 옵션이 켜져있을 경우 사용 가능</li>
     * </ul>
     *
     * <h3>예시</h3>
     * <pre>
     * // 요청 객체의 특정 필드만 추출
     * payload = "#args[0].toEventDto()"
     *
     * // 반환값과 파라미터 조합
     * payload = "new com.example.Event(#result.id, #args[0].name)"
     * </pre>
     */
    String payload() default "";

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
     */
    boolean outbox() default false;

    /**
     * 집합체(Aggregate) 타입.
     * outbox=true일 때 필수 항목입니다.
     */
    String aggregateType() default "";

    /**
     * 집합체(Aggregate) ID 추출 SpEL 표현식.
     * outbox=true일 때 필수 항목입니다.
     */
    String aggregateId() default "";

    /**
     * 이벤트 발행 시점
     */
    enum Phase {
        BEFORE,
        AFTER_RETURNING,
        AFTER
    }
}
