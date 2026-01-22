package com.project.curve.core.outbox;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 이벤트 저장소 포트 인터페이스.
 * <p>
 * Hexagonal Architecture의 Port 역할을 하며, 영속성 기술(JPA, MongoDB 등)에 독립적입니다.
 *
 * <h3>구현체 예시</h3>
 * <ul>
 *   <li>JpaOutboxEventRepository (Spring Data JPA)</li>
 *   <li>MongoOutboxEventRepository (MongoDB)</li>
 *   <li>RedisOutboxEventRepository (Redis)</li>
 * </ul>
 *
 * @see OutboxEvent
 */
public interface OutboxEventRepository {

    /**
     * Outbox 이벤트 저장.
     * <p>
     * 비즈니스 로직과 같은 트랜잭션 내에서 호출되어야 원자성이 보장됩니다.
     *
     * @param event 저장할 이벤트
     * @return 저장된 이벤트
     */
    OutboxEvent save(OutboxEvent event);

    /**
     * 이벤트 ID로 조회.
     *
     * @param eventId 이벤트 ID
     * @return 이벤트 (존재하지 않으면 empty)
     */
    Optional<OutboxEvent> findById(String eventId);

    /**
     * 특정 상태의 이벤트 조회.
     * <p>
     * 주로 PENDING 상태의 이벤트를 조회하여 발행합니다.
     *
     * @param status 조회할 상태
     * @param limit  최대 조회 개수
     * @return 이벤트 목록
     */
    List<OutboxEvent> findByStatus(OutboxStatus status, int limit);

    /**
     * 집합체(Aggregate) 기준으로 이벤트 조회.
     * <p>
     * 특정 Order의 모든 이벤트를 조회할 때 사용합니다.
     *
     * @param aggregateType 집합체 타입 (예: "Order")
     * @param aggregateId   집합체 ID (예: orderId)
     * @return 이벤트 목록 (발생 시각 오름차순)
     */
    List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId);

    /**
     * 이벤트 삭제 (선택적).
     * <p>
     * PUBLISHED 상태의 오래된 이벤트를 정리할 때 사용합니다.
     *
     * @param eventId 삭제할 이벤트 ID
     */
    void deleteById(String eventId);

    /**
     * 전체 이벤트 개수 조회.
     *
     * @return 이벤트 개수
     */
    long count();

    /**
     * 상태별 이벤트 개수 조회.
     *
     * @param status 조회할 상태
     * @return 이벤트 개수
     */
    long countByStatus(OutboxStatus status);
}
