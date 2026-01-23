package com.project.curve.spring.outbox.persistence;

import com.project.curve.core.outbox.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 이벤트 Spring Data JPA 리포지토리.
 * <p>
 * PENDING 이벤트를 효율적으로 조회하기 위한 커스텀 쿼리를 제공합니다.
 *
 * @see OutboxEventJpaEntity
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {

    /**
     * 상태별 이벤트 조회 (발생 시각 오름차순, Limit 지원).
     * <p>
     * 오래된 이벤트부터 처리하여 순서 보장을 돕습니다.
     *
     * @param status   조회할 상태
     * @param pageable 페이징 (limit 설정)
     * @return 이벤트 목록
     */
    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.status = :status ORDER BY e.occurredAt ASC")
    List<OutboxEventJpaEntity> findByStatusOrderByOccurredAtAsc(
            @Param("status") OutboxStatus status,
            Pageable pageable
    );

    /**
     * PENDING 이벤트를 비관적 잠금(FOR UPDATE SKIP LOCKED)으로 조회.
     * <p>
     * 다중 인스턴스 환경에서 동일 이벤트의 중복 처리를 방지합니다.
     * 다른 인스턴스가 이미 잠근 행은 건너뛰고, 잠기지 않은 행만 반환합니다.
     *
     * @param status   조회할 상태
     * @param pageable 페이징 (limit 설정)
     * @return 잠금이 획득된 이벤트 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.status = :status ORDER BY e.occurredAt ASC")
    List<OutboxEventJpaEntity> findByStatusForUpdateSkipLocked(
            @Param("status") OutboxStatus status,
            Pageable pageable
    );

    /**
     * 집합체 기준 이벤트 조회 (발생 시각 오름차순).
     *
     * @param aggregateType 집합체 타입
     * @param aggregateId   집합체 ID
     * @return 이벤트 목록
     */
    List<OutboxEventJpaEntity> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
            String aggregateType,
            String aggregateId
    );

    /**
     * 상태별 이벤트 개수 조회.
     *
     * @param status 조회할 상태
     * @return 이벤트 개수
     */
    long countByStatus(OutboxStatus status);

    /**
     * 오래된 이벤트 삭제 (배치 처리용).
     * <p>
     * JPQL은 LIMIT를 직접 지원하지 않으므로, 서브쿼리나 네이티브 쿼리를 사용해야 할 수 있습니다.
     * 여기서는 ID 목록을 조회 후 삭제하는 방식을 사용하거나, 네이티브 쿼리를 사용할 수 있습니다.
     * DB 호환성을 위해 ID 조회 후 삭제 방식을 권장하지만, 성능을 위해 네이티브 쿼리를 사용할 수도 있습니다.
     * <p>
     * 여기서는 ID 목록을 먼저 조회하는 방식을 사용하기 위해 조회 메서드를 추가합니다.
     */
    @Query("SELECT e.eventId FROM OutboxEventJpaEntity e WHERE e.status = :status AND e.occurredAt < :before")
    List<String> findIdsByStatusAndOccurredAtBefore(
            @Param("status") OutboxStatus status,
            @Param("before") Instant before,
            Pageable pageable
    );

    @Modifying
    @Query("DELETE FROM OutboxEventJpaEntity e WHERE e.eventId IN :ids")
    int deleteByEventIds(@Param("ids") List<String> ids);
}
