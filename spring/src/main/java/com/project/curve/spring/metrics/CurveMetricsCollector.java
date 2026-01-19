package com.project.curve.spring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Curve 이벤트 발행 메트릭 수집기
 * Micrometer를 사용하여 이벤트 발행 관련 메트릭을 수집합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurveMetricsCollector {

    private final MeterRegistry meterRegistry;

    /**
     * 이벤트 발행 성공/실패 메트릭을 기록합니다.
     *
     * @param eventType 이벤트 타입
     * @param success   성공 여부
     * @param durationMs 처리 시간 (밀리초)
     */
    public void recordEventPublished(String eventType, boolean success, long durationMs) {
        try {
            // 이벤트 발행 카운터
            Counter.builder("curve.events.published")
                    .tag("eventType", eventType)
                    .tag("success", String.valueOf(success))
                    .description("Total number of events published")
                    .register(meterRegistry)
                    .increment();

            // 이벤트 발행 처리 시간
            Timer.builder("curve.events.publish.duration")
                    .tag("eventType", eventType)
                    .tag("success", String.valueOf(success))
                    .description("Event publish duration in milliseconds")
                    .register(meterRegistry)
                    .record(durationMs, TimeUnit.MILLISECONDS);

            log.debug("Recorded event publish metric: eventType={}, success={}, duration={}ms",
                    eventType, success, durationMs);
        } catch (Exception e) {
            log.warn("Failed to record event publish metric", e);
        }
    }

    /**
     * DLQ(Dead Letter Queue) 전송 메트릭을 기록합니다.
     *
     * @param eventType 이벤트 타입
     * @param reason    DLQ 전송 이유
     */
    public void recordDlqEvent(String eventType, String reason) {
        try {
            Counter.builder("curve.events.dlq.count")
                    .tag("eventType", eventType)
                    .tag("reason", reason)
                    .description("Total number of events sent to DLQ")
                    .register(meterRegistry)
                    .increment();

            log.debug("Recorded DLQ event metric: eventType={}, reason={}", eventType, reason);
        } catch (Exception e) {
            log.warn("Failed to record DLQ metric", e);
        }
    }

    /**
     * 재시도 메트릭을 기록합니다.
     *
     * @param eventType   이벤트 타입
     * @param retryCount  재시도 횟수
     * @param finalStatus 최종 상태 (success/failure)
     */
    public void recordRetry(String eventType, int retryCount, String finalStatus) {
        try {
            Counter.builder("curve.events.retry.count")
                    .tag("eventType", eventType)
                    .tag("finalStatus", finalStatus)
                    .description("Total number of event publish retries")
                    .register(meterRegistry)
                    .increment(retryCount);

            log.debug("Recorded retry metric: eventType={}, retryCount={}, finalStatus={}",
                    eventType, retryCount, finalStatus);
        } catch (Exception e) {
            log.warn("Failed to record retry metric", e);
        }
    }

    /**
     * Kafka 프로듀서 에러 메트릭을 기록합니다.
     *
     * @param errorType 에러 타입
     */
    public void recordKafkaError(String errorType) {
        try {
            Counter.builder("curve.kafka.producer.errors")
                    .tag("errorType", errorType)
                    .description("Total number of Kafka producer errors")
                    .register(meterRegistry)
                    .increment();

            log.debug("Recorded Kafka error metric: errorType={}", errorType);
        } catch (Exception e) {
            log.warn("Failed to record Kafka error metric", e);
        }
    }

    /**
     * 감사 이벤트 실패 메트릭을 기록합니다.
     *
     * @param eventType 이벤트 타입
     * @param errorType 에러 타입
     */
    public void recordAuditFailure(String eventType, String errorType) {
        try {
            Counter.builder("curve.audit.failures")
                    .tag("eventType", eventType)
                    .tag("errorType", errorType)
                    .description("Total number of audit event failures")
                    .register(meterRegistry)
                    .increment();

            log.debug("Recorded audit failure metric: eventType={}, errorType={}", eventType, errorType);
        } catch (Exception e) {
            log.warn("Failed to record audit failure metric", e);
        }
    }

    /**
     * PII 처리 메트릭을 기록합니다.
     *
     * @param strategy PII 처리 전략 (MASK, ENCRYPT, HASH)
     * @param success  성공 여부
     */
    public void recordPiiProcessing(String strategy, boolean success) {
        try {
            Counter.builder("curve.pii.processing")
                    .tag("strategy", strategy)
                    .tag("success", String.valueOf(success))
                    .description("Total number of PII processing operations")
                    .register(meterRegistry)
                    .increment();

            log.debug("Recorded PII processing metric: strategy={}, success={}", strategy, success);
        } catch (Exception e) {
            log.warn("Failed to record PII processing metric", e);
        }
    }

    /**
     * ID 생성 메트릭을 기록합니다.
     *
     * @param generatorType ID 생성기 타입
     * @param durationNanos 생성 시간 (나노초)
     */
    public void recordIdGeneration(String generatorType, long durationNanos) {
        try {
            Counter.builder("curve.id.generation.count")
                    .tag("generatorType", generatorType)
                    .description("Total number of IDs generated")
                    .register(meterRegistry)
                    .increment();

            Timer.builder("curve.id.generation.duration")
                    .tag("generatorType", generatorType)
                    .description("ID generation duration in nanoseconds")
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            log.debug("Recorded ID generation metric: generatorType={}, duration={}ns",
                    generatorType, durationNanos);
        } catch (Exception e) {
            log.warn("Failed to record ID generation metric", e);
        }
    }
}
