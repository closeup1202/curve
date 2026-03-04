package com.project.curve.autoconfigure.actuator;

import com.project.curve.spring.outbox.publisher.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Actuator endpoint for Outbox management operations.
 * <p>
 * Accessible via {@code /actuator/curve-outbox}.
 *
 * <h3>Available Operations</h3>
 * <ul>
 *   <li>GET /actuator/curve-outbox — Returns current outbox statistics</li>
 *   <li>POST /actuator/curve-outbox — Replays outbox events since a given timestamp</li>
 * </ul>
 *
 * <h3>Replay Example</h3>
 * <pre>
 * POST /actuator/curve-outbox
 * Content-Type: application/vnd.spring-boot.actuator.v3+json
 *
 * {"since": "2026-03-01T00:00:00Z", "limit": 100}
 * </pre>
 *
 * <b>Idempotency Note:</b> Replay re-publishes events regardless of their current status.
 * Consumers must handle duplicate events.
 */
@Endpoint(id = "curve-outbox")
@RequiredArgsConstructor
public class CurveOutboxEndpoint {

    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * Returns current outbox publisher statistics.
     *
     * @return outbox stats map
     */
    @ReadOperation
    public Map<String, Object> stats() {
        OutboxEventPublisher.PublisherStats stats = outboxEventPublisher.getStats();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", stats.totalPending());
        result.put("published", stats.totalPublished());
        result.put("failed", stats.totalFailed());
        result.put("publishedSinceStart", stats.publishedCountSinceStart());
        result.put("failedSinceStart", stats.failedCountSinceStart());
        result.put("circuitBreakerState", stats.circuitBreakerState());
        result.put("consecutiveFailures", stats.consecutiveFailures());
        result.put("timeSinceLastSuccessMs", stats.timeSinceLastSuccessMs());
        return result;
    }

    /**
     * Replays outbox events that occurred at or after the given timestamp.
     *
     * @param since ISO-8601 timestamp string (e.g., "2026-03-01T00:00:00Z"); required
     * @param limit maximum number of events to replay (default: 100)
     * @return replay result summary
     */
    @WriteOperation
    public Map<String, Object> replay(String since, @Nullable Integer limit) {
        Instant sinceInstant;
        try {
            sinceInstant = Instant.parse(since);
        } catch (DateTimeParseException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Invalid 'since' format. Expected ISO-8601 (e.g. 2026-03-01T00:00:00Z)");
            return error;
        }

        int effectiveLimit = (limit != null && limit > 0) ? limit : 100;
        OutboxEventPublisher.ReplayResult result = outboxEventPublisher.replay(sinceInstant, effectiveLimit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("since", since);
        response.put("limit", effectiveLimit);
        response.put("total", result.total());
        response.put("success", result.success());
        response.put("failed", result.failed());
        response.put("failedEventIds", result.failedEventIds());
        return response;
    }
}
