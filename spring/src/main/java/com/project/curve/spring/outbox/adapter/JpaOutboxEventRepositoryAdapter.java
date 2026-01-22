package com.project.curve.spring.outbox.adapter;

import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.core.outbox.OutboxStatus;
import com.project.curve.spring.outbox.persistence.OutboxEventJpaEntity;
import com.project.curve.spring.outbox.persistence.OutboxEventJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
public class JpaOutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    public JpaOutboxEventRepositoryAdapter(OutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventJpaEntity entity = jpaRepository.findById(event.getEventId())
                .map(existing -> {
                    existing.updateFromDomain(event);
                    return existing;
                })
                .orElseGet(() -> OutboxEventJpaEntity.fromDomain(event));

        OutboxEventJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
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
