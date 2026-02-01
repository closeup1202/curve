package com.project.curve.core.port;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;

/**
 * 도메인 이벤트 발행을 위한 주요 포트(Port).
 * <p>
 * 이 인터페이스는 Curve 라이브러리에서 도메인 이벤트를 발행하기 위한 계약을 정의합니다.
 * 헥사고날 아키텍처 패턴을 따르며, 다양한 어댑터(Kafka, RabbitMQ 등)에 의해 구현될 수 있는 포트 역할을 합니다.
 * </p>
 *
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     private final EventProducer eventProducer;
 *
 *     public Order createOrder(OrderRequest request) {
 *         Order order = orderRepository.save(new Order(request));
 *         eventProducer.publish(new OrderCreatedPayload(order));
 *         return order;
 *     }
 * }
 * }</pre>
 *
 * @see com.project.curve.core.payload.DomainEventPayload
 * @see com.project.curve.core.type.EventSeverity
 * @since 0.0.1
 */
public interface EventProducer {

    /**
     * 기본 중요도(INFO)로 도메인 이벤트를 발행합니다.
     * <p>
     * 이 메서드는 페이로드를 컨텍스트 메타데이터(actor, trace, source 등)와 함께
     * {@link com.project.curve.core.envelope.EventEnvelope}로 감싸서 설정된 메시지 브로커로 발행합니다.
     * </p>
     *
     * @param <T> 이벤트 페이로드 타입
     * @param payload 발행할 도메인 이벤트 페이로드
     * @throws com.project.curve.core.exception.InvalidEventException 페이로드가 유효하지 않은 경우
     * @throws com.project.curve.core.exception.EventSerializationException 직렬화 실패 시
     */
    <T extends DomainEventPayload> void publish(T payload);

    /**
     * 지정된 중요도 레벨로 도메인 이벤트를 발행합니다.
     * <p>
     * 이벤트 중요도를 명시적으로 설정해야 할 때 이 메서드를 사용하세요:
     * </p>
     * <ul>
     *   <li>{@code INFO} - 일반적인 비즈니스 이벤트 (기본값)</li>
     *   <li>{@code WARN} - 주의가 필요한 경고 이벤트</li>
     *   <li>{@code ERROR} - 실패를 나타내는 오류 이벤트</li>
     *   <li>{@code CRITICAL} - 즉각적인 조치가 필요한 치명적 이벤트</li>
     * </ul>
     *
     * <h3>예시:</h3>
     * <pre>{@code
     * eventProducer.publish(
     *     new PaymentFailedPayload(order),
     *     EventSeverity.ERROR
     * );
     * }</pre>
     *
     * @param <T> 이벤트 페이로드 타입
     * @param payload 발행할 도메인 이벤트 페이로드
     * @param severity 이벤트의 중요도 레벨
     * @throws com.project.curve.core.exception.InvalidEventException 페이로드가 유효하지 않은 경우
     * @throws com.project.curve.core.exception.EventSerializationException 직렬화 실패 시
     */
    <T extends DomainEventPayload> void publish(T payload, EventSeverity severity);
}
