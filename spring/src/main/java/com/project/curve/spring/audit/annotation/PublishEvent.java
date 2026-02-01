package com.project.curve.spring.audit.annotation;

import com.project.curve.core.type.EventSeverity;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 메서드 실행 시 자동 이벤트 발행을 위한 어노테이션.
 * <p>
 * Spring AOP를 사용하여 선언적으로 이벤트를 발행할 수 있습니다.
 * Spring 빈 메서드에 이 어노테이션을 추가하면 메서드 실행 시 자동으로 이벤트가 발행됩니다.
 * </p>
 *
 * <h3>기본 사용법:</h3>
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
 * <h3>SpEL을 사용한 페이로드 추출:</h3>
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
 * <h3>Transactional Outbox 사용:</h3>
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
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>풍부한 메타데이터를 포함한 이벤트 엔벨로프 자동 생성</li>
 *   <li>SpEL 지원을 통한 유연한 페이로드 추출</li>
 *   <li>다양한 실행 시점 지원 (BEFORE, AFTER_RETURNING, AFTER)</li>
 *   <li>데이터 일관성을 위한 Transactional Outbox Pattern 지원</li>
 *   <li>PII 필드 마스킹/암호화 지원</li>
 *   <li>설정 가능한 에러 처리</li>
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
     * 이벤트 타입 이름.
     * <p>
     * 기본값: 메서드 이름을 기반으로 생성 (ClassName.methodName)
     *
     * @return 이벤트 타입
     */
    String eventType() default "";

    /**
     * 이벤트 중요도 레벨.
     *
     * @return 이벤트 중요도 (기본값: INFO)
     */
    EventSeverity severity() default EventSeverity.INFO;

    /**
     * 이벤트 페이로드로 사용할 파라미터 인덱스.
     * <ul>
     *   <li>-1: 반환값 사용 (기본값)</li>
     *   <li>0 이상: 해당 인덱스의 파라미터 사용</li>
     * </ul>
     * <p>
     * {@link #payload()} 속성이 설정된 경우 이 값은 무시됩니다.
     *
     * @return 페이로드 파라미터 인덱스
     */
    int payloadIndex() default -1;

    /**
     * 이벤트 페이로드 추출을 위한 SpEL 표현식.
     * <p>
     * 설정 시 {@link #payloadIndex()}보다 우선순위를 가집니다.
     *
     * <h3>사용 가능한 변수</h3>
     * <ul>
     *   <li>#result - 메서드 반환값 (AFTER_RETURNING 시점용)</li>
     *   <li>#args - 메서드 파라미터 배열</li>
     *   <li>#p0, #p1, ... - 개별 파라미터</li>
     *   <li>파라미터 이름 - -parameters 옵션으로 컴파일 시 사용 가능</li>
     * </ul>
     *
     * <h3>예시</h3>
     * <pre>
     * // 요청 객체에서 특정 필드 추출
     * payload = "#args[0].toEventDto()"
     *
     * // 반환값과 파라미터 조합
     * payload = "new com.example.Event(#result.id, #args[0].name)"
     * </pre>
     *
     * @return SpEL 표현식
     */
    String payload() default "";

    /**
     * 이벤트 발행 시점.
     *
     * @return 이벤트 발행 시점 (기본값: AFTER_RETURNING)
     */
    Phase phase() default Phase.AFTER_RETURNING;

    /**
     * 이벤트 발행 실패 시 예외 전파 여부.
     * <ul>
     *   <li>true: 이벤트 발행 실패 시 예외를 던져 비즈니스 로직 실패 처리</li>
     *   <li>false: 에러 로그만 남기고 비즈니스 로직 계속 실행 (기본값)</li>
     * </ul>
     *
     * @return 에러 시 실패 처리 여부
     */
    boolean failOnError() default false;

    /**
     * Transactional Outbox Pattern 사용 여부.
     * <p>
     * 활성화 시 비즈니스 로직과 동일한 트랜잭션 내에서 이벤트를 DB에 저장하여
     * 원자성과 일관성을 보장합니다.
     *
     * @return Outbox 패턴 사용 여부
     */
    boolean outbox() default false;

    /**
     * Outbox 패턴용 애그리거트 타입.
     * <p>
     * {@code outbox=true}일 때 필수입니다.
     *
     * @return 애그리거트 타입
     */
    String aggregateType() default "";

    /**
     * 애그리거트 ID 추출을 위한 SpEL 표현식.
     * <p>
     * {@code outbox=true}일 때 필수입니다.
     *
     * @return 애그리거트 ID용 SpEL 표현식
     */
    String aggregateId() default "";

    /**
     * 이벤트 발행 시점 열거형.
     */
    enum Phase {
        /**
         * 메서드 실행 전 이벤트 발행.
         */
        BEFORE,

        /**
         * 메서드가 성공적으로 반환된 후 이벤트 발행.
         */
        AFTER_RETURNING,

        /**
         * 메서드 실행 후 (성공/실패 여부 무관) 이벤트 발행.
         */
        AFTER
    }
}
