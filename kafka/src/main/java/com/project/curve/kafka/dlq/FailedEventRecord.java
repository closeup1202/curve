package com.project.curve.kafka.dlq;

/**
 * DLQ(Dead Letter Queue)에 저장되는 실패 이벤트 레코드
 * <p>
 * Kafka 전송에 실패한 이벤트의 메타데이터와 원본 페이로드를 포함하여 DLQ에 저장
 * 이를 통해 실패 원인 추적 및 재처리 가능
 *
 * @param eventId 원본 이벤트 ID
 * @param originalTopic 원본 토픽 이름
 * @param originalPayload 원본 이벤트 페이로드 (JSON)
 * @param exceptionType 발생한 예외 타입
 * @param exceptionMessage 예외 메시지
 * @param failedAt 실패 발생 시각 (epoch millis)
 */
public record FailedEventRecord(
        String eventId,
        String originalTopic,
        String originalPayload,
        String exceptionType,
        String exceptionMessage,
        long failedAt
) {
}
