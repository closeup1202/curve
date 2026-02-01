package com.project.curve.core.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbox 이벤트 저장소를 위한 포트(Port) 인터페이스.
 * <p>
 * 헥사고날 아키텍처에서 포트 역할을 하며, 영속성 기술(JPA, MongoDB 등)에 독립적입니다.
 *
 * <h3>구현 예시</h3>
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
     * Outbox 이벤트를 저장합니다.
     * <p>
     * 원자성을 보장하기 위해 비즈니스 로직과 동일한 트랜잭션 내에서 호출되어야 합니다.
     *
     * @param event 저장할 이벤트
     */
    void save(OutboxEvent event);

    /**
     * ID로 이벤트를 조회합니다.
     *
     * @param eventId 이벤트 ID
     * @return 이벤트 (없으면 empty)
     */
    Optional<OutboxEvent> findById(String eventId);

    /**
     * 상태별로 이벤트를 조회합니다.
     * <p>
     * 주로 발행 대기 중인(PENDING) 이벤트를 조회할 때 사용됩니다.
     *
     * @param status 조회할 상태
     * @param limit  최대 조회 개수
     * @return 이벤트 목록
     */
    List<OutboxEvent> findByStatus(OutboxStatus status, int limit);

    /**
     * 발행을 위해 PENDING 상태의 이벤트를 비관적 락(FOR UPDATE SKIP LOCKED)과 함께 조회합니다.
     * <p>
     * 다중 인스턴스 환경에서 동일 이벤트의 중복 처리를 방지합니다.
     * 다른 인스턴스에 의해 이미 잠긴 행은 건너뛰고 잠금되지 않은 행만 반환합니다.
     *
     * @param limit 최대 조회 개수
     * @return 락이 획득된 PENDING 이벤트 목록
     */
    List<OutboxEvent> findPendingForProcessing(int limit);

    /**
     * 애그리거트별로 이벤트를 조회합니다.
     * <p>
     * 특정 주문(Order)의 모든 이벤트를 조회할 때 사용됩니다.
     *
     * @param aggregateType 애그리거트 타입 (예: "Order")
     * @param aggregateId   애그리거트 ID (예: orderId)
     * @return 이벤트 목록 (발생 시간 오름차순 정렬)
     */
    List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId);

    /**
     * 이벤트를 삭제합니다 (선택사항).
     * <p>
     * 오래된 PUBLISHED 이벤트를 정리할 때 사용됩니다.
     *
     * @param eventId 삭제할 이벤트 ID
     */
    void deleteById(String eventId);

    /**
     * 오래된 이벤트를 일괄 삭제합니다.
     * <p>
     * 특정 상태이면서 기준 시간 이전에 발생한 이벤트를 삭제합니다.
     * 대량 삭제 시 DB 부하를 줄이기 위해 배치 단위로 삭제하는 것이 권장됩니다.
     *
     * @param status 삭제할 상태 (주로 PUBLISHED)
     * @param before 기준 시간 (이 시간 이전 데이터 삭제)
     * @param limit  한 번에 삭제할 최대 개수
     * @return 삭제된 이벤트 수
     */
    int deleteByStatusAndOccurredAtBefore(OutboxStatus status, Instant before, int limit);

    /**
     * 전체 이벤트 수를 조회합니다.
     *
     * @return 이벤트 수
     */
    long count();

    /**
     * 상태별 이벤트 수를 조회합니다.
     *
     * @param status 조회할 상태
     * @return 이벤트 수
     */
    long countByStatus(OutboxStatus status);
}
