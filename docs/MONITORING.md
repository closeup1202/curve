# Monitoring & Dashboard Guide

This guide explains how to monitor Curve event publishing and set up dashboards for failed event tracking.

## Table of Contents

- [Available Metrics](#available-metrics)
- [Health Indicators](#health-indicators)
- [Prometheus Integration](#prometheus-integration)
- [Grafana Dashboards](#grafana-dashboards)
- [Alerting Rules](#alerting-rules)
- [Log Monitoring](#log-monitoring)

---

## Available Metrics

### Curve Custom Endpoint

Access Curve-specific metrics:
```bash
curl http://localhost:8080/actuator/curve-metrics
```

**Response:**
```json
{
  "eventsPublished": 1523,
  "eventsFailed": 12,
  "dlqEvents": 8,
  "outboxPending": 3,
  "lastPublishTime": "2024-01-15T10:30:45Z"
}
```

### Micrometer Metrics

Curve exposes the following metrics via Micrometer:

| Metric Name | Type | Description |
|-------------|------|-------------|
| `curve.events.published` | Counter | Total successfully published events |
| `curve.events.failed` | Counter | Total failed event publications |
| `curve.events.dlq` | Counter | Events sent to DLQ |
| `curve.events.publish.duration` | Timer | Event publishing duration |
| `curve.outbox.pending` | Gauge | Current pending outbox events |
| `curve.outbox.processed` | Counter | Processed outbox events |
| `curve.circuit.state` | Gauge | Circuit breaker state (0=closed, 1=open, 2=half-open) |

### Enabling Metrics

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,curve-metrics
  metrics:
    tags:
      application: ${spring.application.name}
```

---

## Health Indicators

### Curve Health Check

```bash
curl http://localhost:8080/actuator/health/curve
```

**Healthy Response:**
```json
{
  "status": "UP",
  "details": {
    "kafkaProducerInitialized": true,
    "clusterId": "lkc-abc123",
    "nodeCount": 3,
    "topic": "event.audit.v1",
    "dlqTopic": "event.audit.dlq.v1"
  }
}
```

**Unhealthy Response:**
```json
{
  "status": "DOWN",
  "details": {
    "error": "Kafka broker unreachable: Connection refused"
  }
}
```

### Health Configuration

```yaml
management:
  health:
    curve:
      enabled: true
  endpoint:
    health:
      show-details: always
```

---

## Prometheus Integration

### Configuration

Add to your `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  prometheus:
    metrics:
      export:
        enabled: true
```

### Scrape Configuration

Add to `prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'curve-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app-host:8080']
    scrape_interval: 15s
```

### Key Prometheus Queries

**Event Publishing Rate:**
```promql
rate(curve_events_published_total[5m])
```

**Error Rate:**
```promql
rate(curve_events_failed_total[5m]) / rate(curve_events_published_total[5m]) * 100
```

**Publishing Latency (p99):**
```promql
histogram_quantile(0.99, rate(curve_events_publish_duration_seconds_bucket[5m]))
```

**Outbox Queue Depth:**
```promql
curve_outbox_pending
```

**Circuit Breaker State:**
```promql
curve_circuit_state
```

---

## Grafana Dashboards

### Dashboard JSON

Import this dashboard into Grafana:

```json
{
  "title": "Curve Event Publishing",
  "panels": [
    {
      "title": "Events Published per Second",
      "type": "graph",
      "targets": [
        {
          "expr": "rate(curve_events_published_total[1m])",
          "legendFormat": "{{instance}}"
        }
      ]
    },
    {
      "title": "Failed Events",
      "type": "stat",
      "targets": [
        {
          "expr": "increase(curve_events_failed_total[1h])"
        }
      ],
      "thresholds": {
        "steps": [
          {"color": "green", "value": 0},
          {"color": "yellow", "value": 10},
          {"color": "red", "value": 50}
        ]
      }
    },
    {
      "title": "Publishing Latency",
      "type": "graph",
      "targets": [
        {
          "expr": "histogram_quantile(0.50, rate(curve_events_publish_duration_seconds_bucket[5m]))",
          "legendFormat": "p50"
        },
        {
          "expr": "histogram_quantile(0.95, rate(curve_events_publish_duration_seconds_bucket[5m]))",
          "legendFormat": "p95"
        },
        {
          "expr": "histogram_quantile(0.99, rate(curve_events_publish_duration_seconds_bucket[5m]))",
          "legendFormat": "p99"
        }
      ]
    },
    {
      "title": "Outbox Queue Depth",
      "type": "graph",
      "targets": [
        {
          "expr": "curve_outbox_pending",
          "legendFormat": "{{instance}}"
        }
      ]
    },
    {
      "title": "Circuit Breaker State",
      "type": "stat",
      "targets": [
        {
          "expr": "curve_circuit_state"
        }
      ],
      "mappings": [
        {"value": 0, "text": "CLOSED"},
        {"value": 1, "text": "OPEN"},
        {"value": 2, "text": "HALF-OPEN"}
      ]
    },
    {
      "title": "DLQ Events (Last 24h)",
      "type": "stat",
      "targets": [
        {
          "expr": "increase(curve_events_dlq_total[24h])"
        }
      ]
    }
  ]
}
```

### Dashboard Panels Explained

| Panel | Purpose | Action When High |
|-------|---------|------------------|
| Events/sec | Publishing throughput | Normal operation |
| Failed Events | Publication failures | Check Kafka connectivity |
| Latency | Publishing performance | Enable async mode |
| Queue Depth | Outbox backlog | Scale up or fix Kafka |
| Circuit State | Failure protection | Check underlying issue |
| DLQ Events | Unrecoverable failures | Manual review required |

---

## Alerting Rules

### Prometheus Alerting Rules

Create `curve-alerts.yml`:

```yaml
groups:
  - name: curve
    rules:
      # High error rate
      - alert: CurveHighErrorRate
        expr: |
          rate(curve_events_failed_total[5m]) /
          rate(curve_events_published_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High event publishing error rate"
          description: "Error rate is {{ $value | humanizePercentage }} over last 5 minutes"

      # Circuit breaker open
      - alert: CurveCircuitBreakerOpen
        expr: curve_circuit_state == 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Curve circuit breaker is OPEN"
          description: "Event publishing circuit breaker has opened due to failures"

      # Outbox queue growing
      - alert: CurveOutboxQueueGrowing
        expr: curve_outbox_pending > 1000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Outbox queue is growing"
          description: "{{ $value }} events pending in outbox"

      # High latency
      - alert: CurveHighLatency
        expr: |
          histogram_quantile(0.99, rate(curve_events_publish_duration_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High event publishing latency"
          description: "p99 latency is {{ $value }}s"

      # No events published
      - alert: CurveNoEventsPublished
        expr: |
          increase(curve_events_published_total[15m]) == 0
          and increase(curve_events_failed_total[15m]) == 0
        for: 15m
        labels:
          severity: info
        annotations:
          summary: "No events published"
          description: "No events have been published in the last 15 minutes"

      # DLQ spike
      - alert: CurveDLQSpike
        expr: increase(curve_events_dlq_total[1h]) > 100
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High number of DLQ events"
          description: "{{ $value }} events sent to DLQ in the last hour"
```

### Slack Alert Example

Configure Alertmanager for Slack:
```yaml
receivers:
  - name: 'curve-alerts'
    slack_configs:
      - channel: '#alerts'
        send_resolved: true
        title: '{{ .Status | toUpper }}: {{ .CommonAnnotations.summary }}'
        text: '{{ .CommonAnnotations.description }}'
```

---

## Log Monitoring

### Important Log Patterns

**Event Published Successfully:**
```
INFO  c.p.c.k.producer.KafkaEventProducer - Event published: id=1234567890, topic=event.audit.v1
```

**Event Failed:**
```
ERROR c.p.c.k.producer.KafkaEventProducer - Failed to publish event: id=1234567890, error=Connection refused
```

**DLQ Event:**
```
WARN  c.p.c.k.producer.KafkaEventProducer - Event sent to DLQ: id=1234567890, originalError=Timeout
```

**Circuit Breaker State Change:**
```
WARN  c.p.c.s.outbox.publisher.OutboxEventPublisher - Circuit breaker state changed: CLOSED -> OPEN
```

### ELK Stack Integration

**Logstash Filter:**
```ruby
filter {
  if [logger_name] =~ /curve/ {
    grok {
      match => {
        "message" => "Event %{WORD:event_action}: id=%{NUMBER:event_id}"
      }
    }
    if [event_action] == "published" {
      mutate { add_tag => ["curve_success"] }
    } else if [event_action] == "failed" {
      mutate { add_tag => ["curve_failure"] }
    }
  }
}
```

**Kibana Saved Searches:**

1. **Failed Events:**
   ```
   logger_name:*curve* AND level:ERROR
   ```

2. **DLQ Events:**
   ```
   message:"sent to DLQ"
   ```

3. **Circuit Breaker Events:**
   ```
   message:"Circuit breaker"
   ```

### Structured Logging

Enable JSON logging for better parsing:

```yaml
logging:
  pattern:
    console: '{"timestamp":"%d","level":"%level","logger":"%logger","message":"%msg"}%n'
```

Or use Logback with JSON encoder:
```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

---

## Quick Reference

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health/curve` | Curve health status |
| `/actuator/curve-metrics` | Curve-specific metrics |
| `/actuator/prometheus` | Prometheus metrics |

### Key Metrics to Watch

| Metric | Normal | Warning | Critical |
|--------|--------|---------|----------|
| Error Rate | < 1% | 1-5% | > 5% |
| Latency (p99) | < 100ms | 100-500ms | > 500ms |
| Outbox Queue | < 100 | 100-1000 | > 1000 |
| Circuit State | CLOSED | HALF-OPEN | OPEN |

### Emergency Commands

```bash
# Check Kafka connectivity
nc -zv kafka-host 9092

# View recent DLQ events
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic event.audit.dlq.v1 --from-beginning --max-messages 10

# Check outbox table
psql -c "SELECT status, COUNT(*) FROM curve_outbox_events GROUP BY status;"

# Force close circuit breaker (if supported)
curl -X POST http://localhost:8080/actuator/curve/circuit-breaker/reset
```
