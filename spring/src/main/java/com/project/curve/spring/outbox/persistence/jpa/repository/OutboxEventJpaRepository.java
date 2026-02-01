package com.project.curve.spring.outbox.persistence.jpa.repository;

import com.project.curve.core.outbox.OutboxStatus;
import com.project.curve.spring.outbox.persistence.jpa.entity.OutboxEventJpaEntity;
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
     * 상태별로 이벤트를 조회합니다 (발생 시간 오름차순, limit 지원).
     * <p>
     * 오래된 이벤트를 먼저 처리하여 순서를 보장하는 데 도움을 줍니다.
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
     * 비관적 락(FOR UPDATE SKIP LOCKED)을 사용하여 PENDING 이벤트를 조회합니다.
     * <p>
     * 다중 인스턴스 환경에서 동일 이벤트의 중복 처리를 방지합니다.
     * 다른 인스턴스에 의해 이미 잠긴 행은 건너뛰고 잠금되지 않은 행만 반환합니다.
     * 또한 백오프 전략을 준수하기 위해 nextRetryAt을 확인합니다.
     *
     * @param status   조회할 상태
     * @param now      현재 타임스탬프
     * @param pageable 페이징 (limit 설정)
     * @return 락이 획득된 이벤트 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.status = :status AND e.nextRetryAt <= :now ORDER BY e.occurredAt ASC")
    List<OutboxEventJpaEntity> findByStatusAndNextRetryAtLessThanEqualForUpdateSkipLocked(
            @Param("status") OutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    /**
     * 애그리거트별로 이벤트를 조회합니다 (발생 시간 오름차순).
     *
     * @param aggregateType 애그리거트 타입
     * @param aggregateId   애그리거트 ID
     * @return 이벤트 목록
     */
    List<OutboxEventJpaEntity> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
            String aggregateType,
            String aggregateId
    );

    /**
     * 상태별 이벤트 수를 조회합니다.
     *
     * @param status 조회할 상태
     * @return 이벤트 수
     */
    long countByStatus(OutboxStatus status);

    /**
     * 오래된 이벤트를 삭제합니다 (배치 처리용).
     * <p>
     * JPQL은 LIMIT를 직접 지원하지 않으므로 서브쿼리나 네이티브 쿼리가 필요할 수 있습니다.
     * 여기서는 ID 목록을 조회한 후 삭제하는 방식을 사용하거나 네이티브 쿼리를 사용할 수 있습니다.
     * DB 호환성을 위해 ID 조회 후 삭제 방식을 권장하지만, 성능을 위해 네이티브 쿼리를 사용할 수도 있습니다.
     * <p>
     * 여기서는 먼저 ID 목록을 조회하는 쿼리 메서드를 추가합니다.
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
