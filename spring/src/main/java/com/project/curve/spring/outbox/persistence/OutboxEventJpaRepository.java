package com.project.curve.spring.outbox.persistence;

import com.project.curve.core.outbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Outbox 이벤트 Spring Data JPA 리포지토리.
 * <p>
 * PENDING 이벤트를 효율적으로 조회하기 위한 커스텀 쿼리를 제공합니다.
 *
 * @see OutboxEventJpaEntity
 */
@Repository
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
}
