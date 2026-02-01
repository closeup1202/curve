package com.project.curve.kafka.dlq;

/**
 * DLQ(Dead Letter Queue)에 저장되는 실패 이벤트 레코드.
 * <p>
 * Kafka 전송에 실패한 이벤트의 원본 페이로드와 메타데이터를 함께 저장하여,
 * 추후 실패 원인을 분석하거나 재처리할 수 있도록 합니다.
 *
 * @param eventId          원본 이벤트 ID
 * @param originalTopic    원본 토픽 이름
 * @param originalPayload  원본 이벤트 페이로드 (JSON 문자열)
 * @param exceptionType    발생한 예외 타입 (클래스명)
 * @param exceptionMessage 예외 메시지
 * @param failedAt         실패 발생 시각 (Epoch Millis)
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
