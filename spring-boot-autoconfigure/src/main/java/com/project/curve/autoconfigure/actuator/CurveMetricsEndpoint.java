package com.project.curve.autoconfigure.actuator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Curve 이벤트 발행 시스템의 메트릭을 노출하는 커스텀 Actuator 엔드포인트.
 * <p>
 * {@code /actuator/curve-metrics} 경로로 접근할 수 있습니다.
 *
 * <h3>제공 메트릭</h3>
 * <ul>
 *   <li>curve.events.published: 발행된 이벤트 수 (성공/실패)</li>
 *   <li>curve.events.publish.duration: 이벤트 발행 처리 시간</li>
 *   <li>curve.events.dlq.count: DLQ로 전송된 이벤트 수</li>
 *   <li>curve.events.retry.count: 재시도 횟수</li>
 *   <li>curve.kafka.producer.errors: Kafka Producer 에러 수</li>
 *   <li>curve.pii.processing: PII 처리 횟수</li>
 *   <li>curve.id.generation.count: ID 생성 횟수</li>
 * </ul>
 *
 * @see org.springframework.boot.actuate.endpoint.annotation.Endpoint
 * @see MeterRegistry
 */
@Endpoint(id = "curve-metrics")
public class CurveMetricsEndpoint {

    private final MeterRegistry meterRegistry;

    public CurveMetricsEndpoint(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Curve 관련 메트릭을 모두 조회합니다.
     *
     * @return Curve 메트릭 정보 맵
     */
    @ReadOperation
    public Map<String, Object> curveMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // 이벤트 발행 메트릭
        metrics.put("events", getEventMetrics());

        // DLQ 메트릭
        metrics.put("dlq", getDlqMetrics());

        // Kafka 메트릭
        metrics.put("kafka", getKafkaMetrics());

        // PII 메트릭
        metrics.put("pii", getPiiMetrics());

        // ID 생성 메트릭
        metrics.put("idGeneration", getIdGenerationMetrics());

        // 전체 통계
        metrics.put("summary", getSummaryMetrics());

        return metrics;
    }

    private Map<String, Object> getEventMetrics() {
        Map<String, Object> eventMetrics = new LinkedHashMap<>();

        // 발행된 이벤트 카운트
        List<Map<String, Object>> published = meterRegistry.find("curve.events.published")
                .counters()
                .stream()
                .map(this::counterToMap)
                .collect(Collectors.toList());
        eventMetrics.put("published", published);

        // 발행 처리 시간
        List<Map<String, Object>> duration = meterRegistry.find("curve.events.publish.duration")
                .timers()
                .stream()
                .map(this::timerToMap)
                .collect(Collectors.toList());
        eventMetrics.put("publishDuration", duration);

        // 재시도 횟수
        List<Map<String, Object>> retries = meterRegistry.find("curve.events.retry.count")
                .counters()
                .stream()
                .map(this::counterToMap)
                .collect(Collectors.toList());
        eventMetrics.put("retries", retries);

        return eventMetrics;
    }

    private Map<String, Object> getDlqMetrics() {
        Map<String, Object> dlqMetrics = new LinkedHashMap<>();

        List<Map<String, Object>> dlqCount = meterRegistry.find("curve.events.dlq.count")
                .counters()
                .stream()
                .map(this::counterToMap)
                .collect(Collectors.toList());
        dlqMetrics.put("count", dlqCount);

        // 총 DLQ 이벤트 수
        double totalDlqEvents = dlqCount.stream()
                .mapToDouble(m -> (Double) m.get("value"))
                .sum();
        dlqMetrics.put("total", totalDlqEvents);

        return dlqMetrics;
    }

    private Map<String, Object> getKafkaMetrics() {
        Map<String, Object> kafkaMetrics = new LinkedHashMap<>();

        List<Map<String, Object>> errors = meterRegistry.find("curve.kafka.producer.errors")
                .counters()
                .stream()
                .map(this::counterToMap)
                .collect(Collectors.toList());
        kafkaMetrics.put("errors", errors);

        // 총 에러 수
        double totalErrors = errors.stream()
                .mapToDouble(m -> (Double) m.get("value"))
                .sum();
        kafkaMetrics.put("totalErrors", totalErrors);

        return kafkaMetrics;
    }

    private Map<String, Object> getPiiMetrics() {
        Map<String, Object> piiMetrics = new LinkedHashMap<>();

        List<Map<String, Object>> processing = meterRegistry.find("curve.pii.processing")
                .counters()
                .stream()
                .map(this::counterToMap)
                .collect(Collectors.toList());
        piiMetrics.put("processing", processing);

        return piiMetrics;
    }

    private Map<String, Object> getIdGenerationMetrics() {
        Map<String, Object> idMetrics = new LinkedHashMap<>();

        List<Map<String, Object>> count = meterRegistry.find("curve.id.generation.count")
                .counters()
                .stream()
                .map(this::counterToMap)
                .collect(Collectors.toList());
        idMetrics.put("count", count);

        List<Map<String, Object>> duration = meterRegistry.find("curve.id.generation.duration")
                .timers()
                .stream()
                .map(this::timerToMap)
                .collect(Collectors.toList());
        idMetrics.put("duration", duration);

        return idMetrics;
    }

    private Map<String, Object> getSummaryMetrics() {
        Map<String, Object> summary = new LinkedHashMap<>();

        // 총 발행된 이벤트 수 (성공 + 실패)
        double totalPublished = meterRegistry.find("curve.events.published")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("totalEventsPublished", totalPublished);

        // 성공한 이벤트 수
        double successfulEvents = meterRegistry.find("curve.events.published")
                .tag("success", "true")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("successfulEvents", successfulEvents);

        // 실패한 이벤트 수
        double failedEvents = meterRegistry.find("curve.events.published")
                .tag("success", "false")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("failedEvents", failedEvents);

        // 성공률
        double successRate = totalPublished > 0 ? (successfulEvents / totalPublished) * 100 : 0;
        summary.put("successRate", String.format("%.2f%%", successRate));

        // 총 DLQ 이벤트 수
        double totalDlq = meterRegistry.find("curve.events.dlq.count")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("totalDlqEvents", totalDlq);

        // 총 Kafka 에러 수
        double totalKafkaErrors = meterRegistry.find("curve.kafka.producer.errors")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("totalKafkaErrors", totalKafkaErrors);

        return summary;
    }

    private Map<String, Object> counterToMap(Counter counter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", counter.getId().getName());
        map.put("tags", tagsToMap(counter.getId()));
        map.put("value", counter.count());
        map.put("description", counter.getId().getDescription());
        return map;
    }

    private Map<String, Object> timerToMap(Timer timer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", timer.getId().getName());
        map.put("tags", tagsToMap(timer.getId()));
        map.put("count", timer.count());
        map.put("totalTime", timer.totalTime(timer.baseTimeUnit()));
        map.put("mean", timer.mean(timer.baseTimeUnit()));
        map.put("max", timer.max(timer.baseTimeUnit()));
        map.put("unit", timer.baseTimeUnit().toString());
        map.put("description", timer.getId().getDescription());
        return map;
    }

    private Map<String, String> tagsToMap(Meter.Id id) {
        return id.getTags().stream()
                .collect(Collectors.toMap(
                        io.micrometer.core.instrument.Tag::getKey,
                        io.micrometer.core.instrument.Tag::getValue
                ));
    }
}
