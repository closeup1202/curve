package com.project.curve.spring.metrics;

/**
 * Curve 이벤트 발행 지표(Metrics) 수집을 위한 인터페이스.
 * <p>
 * Micrometer가 클래스패스에 있으면 {@link MicrometerCurveMetricsCollector}를 사용하고,
 * 그렇지 않으면 {@link NoOpCurveMetricsCollector}를 사용하여 지표 수집을 생략합니다.
 */
public interface CurveMetricsCollector {

    /**
     * 이벤트 발행 결과를 기록합니다.
     *
     * @param eventType  이벤트 타입
     * @param success    성공 여부
     * @param durationMs 소요 시간 (밀리초)
     */
    void recordEventPublished(String eventType, boolean success, long durationMs);

    /**
     * DLQ 전송 이벤트를 기록합니다.
     *
     * @param eventType 이벤트 타입
     * @param reason    전송 사유 (예: 예외 클래스명)
     */
    void recordDlqEvent(String eventType, String reason);

    /**
     * 재시도 횟수를 기록합니다.
     *
     * @param eventType   이벤트 타입
     * @param retryCount  재시도 횟수
     * @param finalStatus 최종 상태 (success, failure 등)
     */
    void recordRetry(String eventType, int retryCount, String finalStatus);

    /**
     * Kafka 프로듀서 에러를 기록합니다.
     *
     * @param errorType 에러 타입
     */
    void recordKafkaError(String errorType);

    /**
     * 감사(Audit) 실패를 기록합니다.
     *
     * @param eventType 이벤트 타입
     * @param errorType 에러 타입
     */
    void recordAuditFailure(String eventType, String errorType);

    /**
     * PII 처리 결과를 기록합니다.
     *
     * @param strategy 처리 전략 (MASK, ENCRYPT 등)
     * @param success  성공 여부
     */
    void recordPiiProcessing(String strategy, boolean success);

    /**
     * ID 생성 소요 시간을 기록합니다.
     *
     * @param generatorType 생성기 타입
     * @param durationNanos 소요 시간 (나노초)
     */
    void recordIdGeneration(String generatorType, long durationNanos);
}
