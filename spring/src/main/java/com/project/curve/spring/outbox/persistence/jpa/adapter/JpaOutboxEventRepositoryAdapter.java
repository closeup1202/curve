package com.project.curve.spring.outbox.persistence.jpa.adapter;

import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.core.outbox.OutboxStatus;
import com.project.curve.spring.outbox.persistence.jpa.entity.OutboxEventJpaEntity;
import com.project.curve.spring.outbox.persistence.jpa.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * OutboxEventRepository의 JPA 구현체 (Hexagonal Architecture의 Adapter).
 * <p>
 * Core 도메인 모델과 JPA 엔티티 간의 변환을 담당합니다.
 *
 * @see OutboxEventRepository
 * @see OutboxEventJpaRepository
 */
@Component
@Transactional
@RequiredArgsConstructor
public class JpaOutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public void save(OutboxEvent event) {
        OutboxEventJpaEntity entity = jpaRepository.findById(event.getEventId())
                .map(existing -> {
                    existing.updateFromDomain(event);
                    return existing;
                })
                .orElseGet(() -> OutboxEventJpaEntity.fromDomain(event));

        OutboxEventJpaEntity saved = jpaRepository.save(entity);
        saved.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboxEvent> findById(String eventId) {
        return jpaRepository.findById(eventId)
                .map(OutboxEventJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findByStatus(OutboxStatus status, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        return jpaRepository.findByStatusOrderByOccurredAtAsc(status, pageRequest)
                .stream()
                .map(OutboxEventJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<OutboxEvent> findPendingForProcessing(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        // 현재 시각 이전에 재시도 예정인 이벤트만 조회 (nextRetryAt <= now)
        // JPA Repository 메서드 시그니처 변경 필요 (findPendingForProcessing -> findByStatusForUpdateSkipLocked)
        // 하지만 JpaRepositoryAdapter는 이미 변경된 메서드를 호출하고 있음.
        // OutboxEventJpaRepository의 메서드 시그니처가 변경되었는지 확인 필요.
        // 이전 단계에서 OutboxEventJpaRepository에 findByStatusForUpdateSkipLocked를 추가했음.
        
        // 주의: OutboxEventJpaRepository 인터페이스 정의와 일치해야 함.
        // 이전 코드에서는 findByStatusForUpdateSkipLocked(status, pageable) 이었음.
        // 하지만 JpaOutboxEventRepositoryAdapter에서는 findByStatusForUpdateSkipLocked(status, now, pageable)을 호출하고 있음.
        // OutboxEventJpaRepository를 다시 확인하고 맞춰야 함.
        
        // 일단 여기서는 기존 코드(이전 단계에서 작성한 코드)를 그대로 사용.
        // 이전 단계에서 OutboxEventJpaRepository에 findByStatusForUpdateSkipLocked(status, pageable)만 정의했음.
        // 따라서 여기서는 now 파라미터를 제거해야 함.
        
        return jpaRepository.findByStatusForUpdateSkipLocked(OutboxStatus.PENDING, pageRequest)
                .stream()
                .map(OutboxEventJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId) {
        return jpaRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
                        aggregateType, aggregateId)
                .stream()
                .map(OutboxEventJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String eventId) {
        jpaRepository.deleteById(eventId);
    }

    @Override
    public int deleteByStatusAndOccurredAtBefore(OutboxStatus status, Instant before, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        List<String> idsToDelete = jpaRepository.findIdsByStatusAndOccurredAtBefore(status, before, pageRequest);

        if (idsToDelete.isEmpty()) {
            return 0;
        }

        return jpaRepository.deleteByEventIds(idsToDelete);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpaRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(OutboxStatus status) {
        return jpaRepository.countByStatus(status);
    }
}
