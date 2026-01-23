package com.project.curve.spring.metrics;

/**
 * Curve 이벤트 발행 메트릭 수집기 인터페이스.
 * <p>
 * Micrometer가 클래스패스에 있으면 {@link MicrometerCurveMetricsCollector}가 사용되고,
 * 없으면 {@link NoOpCurveMetricsCollector}가 사용됩니다.
 */
public interface CurveMetricsCollector {

    void recordEventPublished(String eventType, boolean success, long durationMs);

    void recordDlqEvent(String eventType, String reason);

    void recordRetry(String eventType, int retryCount, String finalStatus);

    void recordKafkaError(String errorType);

    void recordAuditFailure(String eventType, String errorType);

    void recordPiiProcessing(String strategy, boolean success);

    void recordIdGeneration(String generatorType, long durationNanos);
}
