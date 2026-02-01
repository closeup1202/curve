package com.project.curve.spring.metrics;

/**
 * 지표를 수집하지 않는 NoOp 구현체.
 * <p>
 * Micrometer가 클래스패스에 없을 때 자동으로 등록되어,
 * null 체크 없이 안전하게 지표 수집 호출을 무시합니다.
 */
public class NoOpCurveMetricsCollector implements CurveMetricsCollector {

    @Override
    public void recordEventPublished(String eventType, boolean success, long durationMs) {
        // no-op
    }

    @Override
    public void recordDlqEvent(String eventType, String reason) {
        // no-op
    }

    @Override
    public void recordRetry(String eventType, int retryCount, String finalStatus) {
        // no-op
    }

    @Override
    public void recordKafkaError(String errorType) {
        // no-op
    }

    @Override
    public void recordAuditFailure(String eventType, String errorType) {
        // no-op
    }

    @Override
    public void recordPiiProcessing(String strategy, boolean success) {
        // no-op
    }

    @Override
    public void recordIdGeneration(String generatorType, long durationNanos) {
        // no-op
    }
}
