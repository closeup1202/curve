package com.project.curve.core.outbox;

import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Transactional Outbox Pattern을 위한 이벤트 저장소 도메인 모델.
 * <p>
 * DB 트랜잭션과 이벤트 발행의 원자성을 보장하기 위해 사용됩니다.
 *
 * <h3>동작 방식</h3>
 * <ol>
 *   <li>비즈니스 로직과 같은 트랜잭션에 OutboxEvent 저장</li>
 *   <li>트랜잭션 커밋 → DB에 OutboxEvent 영구 저장</li>
 *   <li>별도 스케줄러가 PENDING 상태의 이벤트를 Kafka로 발행</li>
 *   <li>발행 성공 시 PUBLISHED, 실패 시 재시도 카운트 증가</li>
 * </ol>
 *
 * <h3>원자성 보장</h3>
 * <pre>
 * @Transactional
 * public Order createOrder() {
 *     Order order = orderRepo.save(...);     // DB 저장
 *     outboxRepo.save(outboxEvent);          // Outbox 저장 (같은 트랜잭션)
 *     return order;
 * }
 * // 트랜잭션 커밋 → 둘 다 저장되거나 둘 다 롤백
 * </pre>
 *
 * @see OutboxStatus
 */
@Getter
public class OutboxEvent {

    private final String eventId;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final String payload;
    private final Instant occurredAt;

    private OutboxStatus status;
    private int retryCount;
    private Instant publishedAt;
    private String errorMessage;
    private Instant nextRetryAt;

    /**
     * Outbox 이벤트 생성자.
     *
     * @param eventId       이벤트 고유 ID
     * @param aggregateType 집합체 타입 (예: "Order", "User")
     * @param aggregateId   집합체 ID (예: orderId)
     * @param eventType     이벤트 타입 (예: "ORDER_CREATED")
     * @param payload       이벤트 페이로드 (JSON)
     * @param occurredAt    이벤트 발생 시각
     */
    public OutboxEvent(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant occurredAt
    ) {
        validateNotBlank(eventId, "eventId");
        validateNotBlank(aggregateType, "aggregateType");
        validateNotBlank(aggregateId, "aggregateId");
        validateNotBlank(eventType, "eventType");
        validateNotBlank(payload, "payload");

        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }

        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = occurredAt; // 처음에는 발생 즉시 처리 대상
    }

    /**
     * 영속성 계층에서 도메인 모델을 복원할 때 사용하는 팩토리 메서드.
     * <p>
     * 생성자와 달리 기존 상태(status, retryCount 등)를 직접 설정합니다.
     *
     * @param eventId       이벤트 ID
     * @param aggregateType 집합체 타입
     * @param aggregateId   집합체 ID
     * @param eventType     이벤트 타입
     * @param payload       페이로드
     * @param occurredAt    발생 시각
     * @param status        현재 상태
     * @param retryCount    재시도 횟수
     * @param publishedAt   발행 시각 (nullable)
     * @param errorMessage  에러 메시지 (nullable)
     * @param nextRetryAt   다음 재시도 시각 (nullable)
     * @return 복원된 OutboxEvent
     */
    public static OutboxEvent restore(
            String eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload,
            Instant occurredAt,
            OutboxStatus status,
            int retryCount,
            Instant publishedAt,
            String errorMessage,
            Instant nextRetryAt
    ) {
        OutboxEvent event = new OutboxEvent(eventId, aggregateType, aggregateId, eventType, payload, occurredAt);
        event.status = status;
        event.retryCount = retryCount;
        event.publishedAt = publishedAt;
        event.errorMessage = errorMessage;
        event.nextRetryAt = nextRetryAt != null ? nextRetryAt : occurredAt;
        return event;
    }

    /**
     * 이벤트 발행 성공 처리.
     */
    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.errorMessage = null;
        this.nextRetryAt = null;
    }

    /**
     * 이벤트 발행 실패 처리.
     *
     * @param errorMessage 실패 원인
     */
    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.nextRetryAt = null;
    }

    /**
     * 재시도 횟수 증가 및 다음 재시도 시간 설정.
     *
     * @param backoffMs 재시도 지연 시간 (밀리초)
     * @return 증가된 재시도 횟수
     */
    public int scheduleNextRetry(long backoffMs) {
        this.retryCount++;
        this.nextRetryAt = Instant.now().plus(backoffMs, ChronoUnit.MILLIS);
        return this.retryCount;
    }

    /**
     * 최대 재시도 횟수 초과 여부 확인.
     *
     * @param maxRetries 최대 재시도 횟수
     * @return 초과 여부
     */
    public boolean exceededMaxRetries(int maxRetries) {
        return this.retryCount >= maxRetries;
    }

    /**
     * 발행 가능 여부 확인.
     *
     * @return PENDING 상태이면 true
     */
    public boolean canPublish() {
        return this.status == OutboxStatus.PENDING;
    }

    // Getters

    // Private helpers

    private void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "eventId='" + eventId + '\'' +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", status=" + status +
                ", retryCount=" + retryCount +
                ", occurredAt=" + occurredAt +
                ", nextRetryAt=" + nextRetryAt +
                '}';
    }
}
