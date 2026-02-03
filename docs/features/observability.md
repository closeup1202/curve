---
title: Observability - Metrics, Health Checks, and Monitoring
description: Learn how to monitor Curve with Spring Boot Actuator, custom metrics, and health checks.
keywords: observability, monitoring, metrics, health checks, spring boot actuator
---

# Observability

Curve provides built-in observability through Spring Boot Actuator, custom metrics, and health checks.

## Health Checks

### Curve Health Indicator

Check Curve's operational status:

```bash
curl http://localhost:8080/actuator/health/curve
```

**Response:**

```json
{
  "status": "UP",
  "details": {
    "kafkaProducerInitialized": true,
    "producerMetrics": 42,
    "topic": "event.audit.v1",
    "dlqTopic": "event.audit.dlq.v1",
    "outboxEnabled": true,
    "backupEnabled": true
  }
}
```

### Configuration

```yaml title="application.yml"
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,curve-metrics
  endpoint:
    health:
      show-details: always
```

---

## Custom Metrics Endpoint

Curve exposes a dedicated metrics endpoint:

```bash
curl http://localhost:8080/actuator/curve-metrics
```

**Response:**

```json
{
  "summary": {
    "totalEventsPublished": 1523,
    "successfulEvents": 1520,
    "failedEvents": 3,
    "successRate": "99.80%",
    "totalDlqEvents": 3,
    "totalKafkaErrors": 0
  },
  "events": {
    "published": [
      {
        "name": "events.published.total",
        "description": "Total published events",
        "baseUnit": "events",
        "measurements": [
          { "statistic": "COUNT", "value": 1523.0 }
        ]
      }
    ],
    "publishDuration": [
      {
        "name": "events.publish.duration",
        "description": "Event publish duration",
        "baseUnit": "milliseconds",
        "measurements": [
          { "statistic": "MEAN", "value": 45.2 },
          { "statistic": "MAX", "value": 150.0 }
        ]
      }
    ]
  },
  "dlq": {
    "totalDlqEvents": 3,
    "recentDlqEvents": [
      {
        "eventType": "ORDER_CREATED",
        "failureReason": "Kafka timeout",
        "timestamp": "2025-02-03T10:30:00Z"
      }
    ]
  },
  "kafka": {
    "connectionCount": 1,
    "inFlightRequests": 0,
    "requestLatencyAvg": 25.5
  }
}
```

---

## Micrometer Metrics

Curve integrates with Micrometer for standard metrics:

### Available Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `curve.events.published.total` | Counter | Total events published |
| `curve.events.failed.total` | Counter | Total failed events |
| `curve.events.publish.duration` | Timer | Event publish duration |
| `curve.dlq.events.total` | Counter | Total DLQ events |
| `curve.outbox.pending` | Gauge | Pending outbox events |
| `curve.kafka.errors.total` | Counter | Kafka errors |

### Prometheus Integration

```yaml title="application.yml"
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: prometheus
```

**Scrape metrics:**

```bash
curl http://localhost:8080/actuator/prometheus | grep curve
```

**Output:**

```
# TYPE curve_events_published_total counter
curve_events_published_total{eventType="ORDER_CREATED",} 856.0
curve_events_published_total{eventType="USER_REGISTERED",} 667.0

# TYPE curve_events_publish_duration_seconds summary
curve_events_publish_duration_seconds_count 1523.0
curve_events_publish_duration_seconds_sum 68.8
```

---

## Logging

### Enable Debug Logging

```yaml title="application.yml"
logging:
  level:
    io.github.closeup1202.curve: DEBUG
    io.github.closeup1202.curve.kafka: TRACE  # Kafka-specific
```

### Log Output

```
2025-02-03 10:30:00.123 DEBUG [curve] Publishing event: ORDER_CREATED
2025-02-03 10:30:00.125 DEBUG [curve.kafka] Sending to topic: event.audit.v1
2025-02-03 10:30:00.150 INFO  [curve] Event published successfully: eventId=7355889748156289024
```

### Structured Logging (JSON)

```yaml
logging:
  pattern:
    console: '{"time":"%d","level":"%p","logger":"%c","message":"%m"}%n'
```

---

## Distributed Tracing

Curve automatically propagates trace context:

### Spring Cloud Sleuth Integration

```yaml
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-sleuth'
}
```

**Trace context in events:**

```json
{
  "eventId": "7355889748156289024",
  "metadata": {
    "trace": {
      "traceId": "abc123",       // ← Propagated
      "spanId": "def456",         // ← Propagated
      "parentSpanId": "ghi789"
    }
  }
}
```

### MDC Context Propagation

Even in async mode, MDC context is preserved:

```java
@Async
@PublishEvent(eventType = "REPORT_GENERATED")
public CompletableFuture<Report> generateReport() {
    // Trace ID available in logs
    log.info("Generating report");
    return CompletableFuture.completedFuture(new Report());
}
```

---

## Dashboards

### Grafana Dashboard

Import the Curve Grafana dashboard:

```json title="curve-dashboard.json"
{
  "dashboard": {
    "title": "Curve Metrics",
    "panels": [
      {
        "title": "Event Throughput",
        "targets": [
          {
            "expr": "rate(curve_events_published_total[5m])"
          }
        ]
      },
      {
        "title": "Success Rate",
        "targets": [
          {
            "expr": "curve_events_published_total / (curve_events_published_total + curve_events_failed_total) * 100"
          }
        ]
      }
    ]
  }
}
```

### Key Panels

1. **Event Throughput** - Events/sec over time
2. **Success Rate** - Percentage of successful publishes
3. **DLQ Events** - Failed events count
4. **Publish Latency** - P50, P95, P99 latencies
5. **Outbox Queue** - Pending events in outbox

---

## Alerts

### Prometheus Alerting Rules

```yaml title="alerts.yml"
groups:
  - name: curve
    interval: 30s
    rules:
      - alert: HighEventFailureRate
        expr: |
          (
            rate(curve_events_failed_total[5m])
            /
            rate(curve_events_published_total[5m])
          ) > 0.01
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High event failure rate ({{ $value }}%)"

      - alert: DLQEventsDetected
        expr: curve_dlq_events_total > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $value }} events in DLQ"

      - alert: OutboxQueueGrowing
        expr: curve_outbox_pending > 1000
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: "Outbox queue has {{ $value }} pending events"

      - alert: KafkaConnectionLost
        expr: curve_kafka_connection_count == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Kafka connection lost"
```

---

## Best Practices

### :white_check_mark: DO

- **Enable health checks** - Monitor Curve status
- **Set up alerts** - Notify on failures
- **Monitor DLQ** - Investigate failed events
- **Track success rate** - Aim for >99.9%
- **Use distributed tracing** - Debug issues across services
- **Dashboard key metrics** - Visualize trends

### :x: DON'T

- Ignore DLQ events
- Disable metrics in production
- Skip alerting setup
- Log at TRACE level in production

---

## Troubleshooting

### Metrics Not Appearing

!!! failure "Metrics endpoint returns empty"

    **Check:**

    1. Actuator is enabled: `management.endpoints.web.exposure.include=curve-metrics`
    2. Curve is enabled: `curve.enabled=true`
    3. Events have been published

### High Latency

!!! failure "Publish duration > 1 second"

    **Possible causes:**

    - Network latency to Kafka
    - Large payloads
    - Kafka broker overload

    **Solutions:**

    1. Enable async mode: `curve.kafka.async-mode=true`
    2. Reduce payload size
    3. Scale Kafka brokers

---

## Production Checklist

- [ ] Enable health checks
- [ ] Set up Prometheus scraping
- [ ] Create Grafana dashboards
- [ ] Configure alerting rules
- [ ] Enable distributed tracing
- [ ] Set up log aggregation
- [ ] Monitor DLQ topic
- [ ] Test failover scenarios

---

## What's Next?

<div class="grid cards" markdown>

-   :material-server:{ .lg .middle } **Operations Guide**

    ---

    Production deployment best practices

    [:octicons-arrow-right-24: Operations](../OPERATIONS.md)

-   :material-help-circle:{ .lg .middle } **Troubleshooting**

    ---

    Common issues and solutions

    [:octicons-arrow-right-24: Troubleshooting](../TROUBLESHOOTING.md)

</div>
