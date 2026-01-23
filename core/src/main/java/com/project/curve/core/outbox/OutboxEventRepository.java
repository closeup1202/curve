package com.project.curve.core.outbox;

import java.time.Instant;
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
     */
    void save(OutboxEvent event);

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
     * 발행 대상 PENDING 이벤트를 비관적 잠금(FOR UPDATE SKIP LOCKED)으로 조회.
     * <p>
     * 다중 인스턴스 환경에서 동일 이벤트의 중복 처리를 방지합니다.
     * 이미 다른 인스턴스가 잠근 행은 건너뛰고, 잠기지 않은 행만 반환합니다.
     *
     * @param limit 최대 조회 개수
     * @return 잠금이 획득된 PENDING 이벤트 목록
     */
    List<OutboxEvent> findPendingForProcessing(int limit);

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
     * 오래된 이벤트 일괄 삭제.
     * <p>
     * 특정 상태이고, 기준 시간 이전에 발생한 이벤트를 삭제합니다.
     * 대량 삭제 시 DB 부하를 줄이기 위해 배치 단위로 삭제하는 것이 좋습니다.
     *
     * @param status 삭제할 상태 (주로 PUBLISHED)
     * @param before 기준 시간 (이 시간 이전 데이터 삭제)
     * @param limit  한 번에 삭제할 최대 개수
     * @return 삭제된 개수
     */
    int deleteByStatusAndOccurredAtBefore(OutboxStatus status, Instant before, int limit);

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
