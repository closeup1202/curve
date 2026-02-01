package com.project.curve.autoconfigure.actuator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Custom Actuator endpoint that exposes metrics for the Curve event publishing system.
 * <p>
 * Accessible via the {@code /actuator/curve-metrics} path.
 *
 * <h3>Provided Metrics</h3>
 * <ul>
 *   <li>curve.events.published: Number of published events (success/failure)</li>
 *   <li>curve.events.publish.duration: Event publishing processing time</li>
 *   <li>curve.events.dlq.count: Number of events sent to DLQ</li>
 *   <li>curve.events.retry.count: Number of retries</li>
 *   <li>curve.kafka.producer.errors: Number of Kafka Producer errors</li>
 *   <li>curve.pii.processing: Number of PII processing operations</li>
 *   <li>curve.id.generation.count: Number of ID generations</li>
 * </ul>
 *
 * @see org.springframework.boot.actuate.endpoint.annotation.Endpoint
 * @see MeterRegistry
 */
@Endpoint(id = "curve-metrics")
@RequiredArgsConstructor
public class CurveMetricsEndpoint {

    private final MeterRegistry meterRegistry;

    /**
     * Retrieves all Curve-related metrics.
     *
     * @return Map of Curve metrics information
     */
    @ReadOperation
    public Map<String, Object> curveMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // Event publishing metrics
        metrics.put("events", getEventMetrics());

        // DLQ metrics
        metrics.put("dlq", getDlqMetrics());

        // Kafka metrics
        metrics.put("kafka", getKafkaMetrics());

        // PII metrics
        metrics.put("pii", getPiiMetrics());

        // ID generation metrics
        metrics.put("idGeneration", getIdGenerationMetrics());

        // Overall statistics
        metrics.put("summary", getSummaryMetrics());

        return metrics;
    }

    private Map<String, Object> getEventMetrics() {
        Map<String, Object> eventMetrics = new LinkedHashMap<>();

        // Published event count
        List<Map<String, Object>> published = meterRegistry.find("curve.events.published")
                .counters()
                .stream()
                .map(this::counterToMap)
                .collect(Collectors.toList());
        eventMetrics.put("published", published);

        // Publishing processing time
        List<Map<String, Object>> duration = meterRegistry.find("curve.events.publish.duration")
                .timers()
                .stream()
                .map(this::timerToMap)
                .collect(Collectors.toList());
        eventMetrics.put("publishDuration", duration);

        // Retry count
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

        // Total DLQ event count
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

        // Total error count
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

        // Total published event count (success + failure)
        double totalPublished = meterRegistry.find("curve.events.published")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("totalEventsPublished", totalPublished);

        // Successful event count
        double successfulEvents = meterRegistry.find("curve.events.published")
                .tag("success", "true")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("successfulEvents", successfulEvents);

        // Failed event count
        double failedEvents = meterRegistry.find("curve.events.published")
                .tag("success", "false")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("failedEvents", failedEvents);

        // Success rate
        double successRate = totalPublished > 0 ? (successfulEvents / totalPublished) * 100 : 0;
        summary.put("successRate", String.format("%.2f%%", successRate));

        // Total DLQ event count
        double totalDlq = meterRegistry.find("curve.events.dlq.count")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        summary.put("totalDlqEvents", totalDlq);

        // Total Kafka error count
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
