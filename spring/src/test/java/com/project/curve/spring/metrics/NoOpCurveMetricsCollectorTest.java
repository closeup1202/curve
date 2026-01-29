package com.project.curve.spring.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NoOpCurveMetricsCollector 테스트")
class NoOpCurveMetricsCollectorTest {

    @Test
    @DisplayName("NoOpCurveMetricsCollector 생성")
    void createNoOpCollector() {
        // when
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // then
        assertNotNull(collector);
    }

    @Test
    @DisplayName("CurveMetricsCollector 인터페이스 구현")
    void implementsCurveMetricsCollector() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // then
        assertTrue(collector instanceof CurveMetricsCollector);
    }

    @Test
    @DisplayName("recordEventPublished 호출 시 예외 없음")
    void recordEventPublishedDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordEventPublished("ORDER_CREATED", true, 100L)
        );
    }

    @Test
    @DisplayName("recordDlqEvent 호출 시 예외 없음")
    void recordDlqEventDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordDlqEvent("ORDER_CREATED", "KAFKA_ERROR")
        );
    }

    @Test
    @DisplayName("recordRetry 호출 시 예외 없음")
    void recordRetryDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordRetry("ORDER_CREATED", 3, "SUCCESS")
        );
    }

    @Test
    @DisplayName("recordKafkaError 호출 시 예외 없음")
    void recordKafkaErrorDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordKafkaError("CONNECTION_ERROR")
        );
    }

    @Test
    @DisplayName("recordAuditFailure 호출 시 예외 없음")
    void recordAuditFailureDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordAuditFailure("ORDER_CREATED", "VALIDATION_ERROR")
        );
    }

    @Test
    @DisplayName("recordPiiProcessing 호출 시 예외 없음")
    void recordPiiProcessingDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordPiiProcessing("MASKING", true)
        );
    }

    @Test
    @DisplayName("recordIdGeneration 호출 시 예외 없음")
    void recordIdGenerationDoesNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() ->
                collector.recordIdGeneration("SNOWFLAKE", 1000000L)
        );
    }

    @Test
    @DisplayName("null 값으로 메서드 호출 시 예외 없음")
    void methodsWithNullValuesDoNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() -> {
            collector.recordEventPublished(null, true, 0);
            collector.recordDlqEvent(null, null);
            collector.recordRetry(null, 0, null);
            collector.recordKafkaError(null);
            collector.recordAuditFailure(null, null);
            collector.recordPiiProcessing(null, false);
            collector.recordIdGeneration(null, 0);
        });
    }

    @Test
    @DisplayName("연속 호출 시 안정적으로 동작")
    void stableAcrossMultipleCalls() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 1000; i++) {
                collector.recordEventPublished("EVENT_" + i, true, i);
                collector.recordDlqEvent("EVENT_" + i, "REASON_" + i);
                collector.recordRetry("EVENT_" + i, i, "STATUS_" + i);
                collector.recordKafkaError("ERROR_" + i);
                collector.recordAuditFailure("EVENT_" + i, "ERROR_" + i);
                collector.recordPiiProcessing("STRATEGY_" + i, i % 2 == 0);
                collector.recordIdGeneration("GENERATOR_" + i, i * 1000L);
            }
        });
    }

    @Test
    @DisplayName("극한 값으로 메서드 호출 시 예외 없음")
    void methodsWithExtremeValuesDoNotThrow() {
        // given
        NoOpCurveMetricsCollector collector = new NoOpCurveMetricsCollector();

        // when & then
        assertDoesNotThrow(() -> {
            collector.recordEventPublished("", false, Long.MAX_VALUE);
            collector.recordRetry("", Integer.MAX_VALUE, "");
            collector.recordIdGeneration("", Long.MIN_VALUE);
        });
    }
}
