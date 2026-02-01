package com.project.curve.core.outbox;

/**
 * Outbox 이벤트의 발행 상태를 나타내는 열거형.
 * <p>
 * Transactional Outbox Pattern에서 이벤트의 생명주기를 관리합니다.
 *
 * <h3>상태 전이</h3>
 * <pre>
 * PENDING → PUBLISHED (성공)
 *    ↓
 * FAILED (최대 재시도 횟수 초과)
 * </pre>
 */
public enum OutboxStatus {

    /**
     * 발행 대기 중 - 아직 Kafka로 전송되지 않음
     */
    PENDING,

    /**
     * 발행 완료 - Kafka로 성공적으로 전송됨
     */
    PUBLISHED,

    /**
     * 발행 실패 - 최대 재시도 횟수 초과
     */
    FAILED
}
