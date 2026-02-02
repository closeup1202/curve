# Curve Operations Guide

This document describes operational procedures for monitoring, troubleshooting, and recovery in the Curve event publishing system.

## Table of Contents

- [DLQ Monitoring](#dlq-monitoring)
- [Metrics Interpretation](#metrics-interpretation)
- [Troubleshooting Matrix](#troubleshooting-matrix)
- [Recovery Procedures](#recovery-procedures)
- [Alert Configuration](#alert-configuration)
- [Runbook Checklist](#runbook-checklist)

---

## DLQ Monitoring

### Understanding the 3-Tier Failure Recovery

Curve implements a 3-tier failure recovery system to prevent event loss:

```
Event Send Attempt
        │
        ▼
┌─────────────────┐
│  Tier 1: Main   │──── Success ───▶ Event Published
│     Topic       │
└────────┬────────┘
         │ Failure
         ▼
┌─────────────────┐
│  Tier 2: DLQ    │──── Success ───▶ Event in DLQ Topic
│     Topic       │
└────────┬────────┘
         │ Failure
         ▼
┌─────────────────┐
│ Tier 3: Local   │──── Success ───▶ JSON File Backup
│     Backup      │
└────────┬────────┘
         │ Failure
         ▼
    Event Lost + Alert
```

| Tier | Component | Trigger | Description |
|------|-----------|---------|-------------|
| 1 | Main Topic | Normal operation | Events published to configured Kafka topic |
| 2 | DLQ Topic | Main topic failure | Failed events sent to Dead Letter Queue |
| 3 | Local File | DLQ failure | Events backed up to `./dlq-backup/` directory |

### Monitoring DLQ Events

#### Via Kafka UI

1. Navigate to Kafka UI (default: http://localhost:8080)
2. Select Topics from the menu
3. Find `event.audit.dlq.v1` (or your configured DLQ topic)
4. View Messages tab for failed events

#### Via Actuator Endpoint

```bash
# Get DLQ metrics
curl http://localhost:8081/actuator/curve-metrics | jq '.summary'
```

**Response:**
```json
{
  "totalEventsPublished": 1523,
  "successfulEvents": 1520,
  "failedEvents": 3,
  "successRate": "99.80%",
  "totalDlqEvents": 3,
  "totalKafkaErrors": 0
}
```

#### Via Kafka CLI

```bash
# Count messages in DLQ topic
kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9094 \
  --topic event.audit.dlq.v1

# Consume DLQ messages
kafka-console-consumer.sh \
  --bootstrap-server localhost:9094 \
  --topic event.audit.dlq.v1 \
  --from-beginning
```

### DLQ Message Structure

```json
{
  "eventId": "123456789012345678",
  "originalTopic": "event.audit.v1",
  "originalPayload": "{\"eventType\":\"ORDER_CREATED\",...}",
  "exceptionType": "org.apache.kafka.common.errors.TimeoutException",
  "exceptionMessage": "Failed to send message after 3 retries",
  "failedAt": 1704067200000
}
```

| Field | Description |
|-------|-------------|
| `eventId` | Unique event identifier (Snowflake ID) |
| `originalTopic` | Topic where the event was supposed to be sent |
| `originalPayload` | Complete event payload as JSON string |
| `exceptionType` | Java exception class that caused the failure |
| `exceptionMessage` | Human-readable error message |
| `failedAt` | Timestamp (epoch milliseconds) when failure occurred |

### Local Backup Files

**Location:** `./dlq-backup/` (configurable via `curve.kafka.dlq-backup-path`)

```bash
# List backup files
ls -la ./dlq-backup/

# Example output:
# -rw------- 1 user user 2048 Jan 20 10:30 123456789012345678.json
# -rw------- 1 user user 1856 Jan 20 10:31 123456789012345679.json
```

**File naming:** `{eventId}.json`

**File permissions:**
- POSIX systems: `600` (rw-------)
- Windows: ACL restricted to owner only

---

## Metrics Interpretation

### Accessing Metrics

```bash
# Full metrics report
curl http://localhost:8081/actuator/curve-metrics

# Summary only
curl http://localhost:8081/actuator/curve-metrics | jq '.summary'

# Specific metric
curl http://localhost:8081/actuator/curve-metrics | jq '.events.published'
```

### Key Metrics Reference

| Metric | Description | Warning Threshold | Critical Threshold |
|--------|-------------|-------------------|-------------------|
| `successRate` | Event publishing success percentage | < 99% | < 95% |
| `totalDlqEvents` | Events sent to DLQ | > 0 | > 10 (increasing) |
| `totalKafkaErrors` | Kafka producer errors | > 0 | > 5 |
| `curve.events.retry.count` | Retry attempts | Increasing | Rapidly increasing |
| `curve.events.publish.duration` | Publishing latency | > 100ms avg | > 500ms avg |

### Health Status Interpretation

| Status | Indicators | Meaning | Action |
|--------|------------|---------|--------|
| **Healthy** | successRate >= 99.5%, totalDlqEvents = 0 | Normal operation | Monitor |
| **Warning** | successRate 95-99.5%, totalDlqEvents > 0 stable | Intermittent issues | Investigate |
| **Critical** | successRate < 95%, totalDlqEvents increasing | System failure | Immediate action |

### Outbox Publisher Metrics

For Transactional Outbox Pattern users:

| Metric | Description | Action if Abnormal |
|--------|-------------|-------------------|
| `circuitBreakerState` | CLOSED/OPEN/HALF-OPEN | OPEN = Kafka connectivity issue |
| `consecutiveFailures` | Consecutive failure count | > 3 = circuit breaker may open |
| `timeSinceLastSuccessMs` | Time since last success | > 60000 = check Kafka |
| `totalPending` | Pending outbox events | Should trend toward 0 |
| `totalFailed` | Permanently failed events | Requires manual intervention |

### Circuit Breaker States

| State | Behavior | Duration | Transition |
|-------|----------|----------|------------|
| **CLOSED** | Normal operation | - | Opens after 5 consecutive failures |
| **OPEN** | All requests blocked | 60 seconds | Transitions to HALF-OPEN |
| **HALF-OPEN** | Allows test requests | Until success/failure | Success→CLOSED, Failure→OPEN |

---

## Troubleshooting Matrix

### Symptoms and Solutions

| Symptom | Possible Cause | Verification | Solution |
|---------|---------------|--------------|----------|
| Events not published | AOP disabled | Check `curve.aop.enabled` in config | Set to `true` |
| Events not published | Method not public | Review method signature | Make method `public` |
| `TimeoutException` | Kafka unresponsive | `docker-compose ps kafka` | Restart Kafka |
| `TimeoutException` | Network latency | Ping broker | Increase `request-timeout-ms` |
| High DLQ count | Kafka broker down | Check broker logs | Restore Kafka, recover DLQ |
| Circuit breaker OPEN | 5+ consecutive failures | Check Kafka health | Wait 60s or fix Kafka |
| Local backup files exist | Both main and DLQ failed | Check all Kafka connectivity | Manual recovery required |
| PII encryption error | Missing encryption key | Check `PII_ENCRYPTION_KEY` env | Set environment variable |
| Worker ID conflict | Duplicate worker IDs | Check instance configurations | Assign unique IDs |
| Outbox events stuck PENDING | Kafka unreachable | Check circuit breaker state | Fix Kafka connectivity |
| Slow event publishing | Sync mode under high load | Check `async-mode` | Enable async mode |
| `ClockMovedBackwardsException` | System time changed | Check NTP sync | Restart application |

### Common Error Messages

| Error Message | Cause | Solution |
|--------------|-------|----------|
| `Kafka topic is required` | Missing topic configuration | Set `curve.kafka.topic` |
| `workerId must be between 0 and 1023` | Invalid worker ID | Use valid range |
| `PII encryption key is not configured` | Missing encryption key | Set `PII_ENCRYPTION_KEY` env var |
| `Failed to send message after N retries` | Kafka connectivity issue | Check broker status |
| `Circuit breaker is OPEN` | Too many consecutive failures | Wait for half-open or fix Kafka |

### Health Check Responses

```bash
curl http://localhost:8081/actuator/health/curve
```

| Status | kafkaProducerInitialized | Meaning | Action |
|--------|-------------------------|---------|--------|
| UP | true | Healthy | None |
| DOWN | false | KafkaTemplate not initialized | Check Kafka configuration |
| DOWN | true (with error) | Runtime issue | Check exception details |

---

## Recovery Procedures

### Procedure 1: DLQ Event Recovery

**When to use:** Events accumulated in DLQ topic after temporary Kafka issues have been resolved.

**Prerequisites:**
- Kafka is now healthy
- `kafka-console-producer.sh` available in PATH
- Access to DLQ topic

**Steps:**

1. **Verify Kafka is healthy:**
```bash
# Check Kafka container
docker-compose ps kafka

# Check Curve health
curl http://localhost:8081/actuator/health/curve
```

2. **List DLQ events to recover:**
```bash
./scripts/dlq-recovery.sh --list
```

3. **Execute recovery:**
```bash
./scripts/dlq-recovery.sh \
  --topic event.audit.v1 \
  --broker localhost:9094 \
  --dir ./dlq-backup
```

4. **Recover specific file:**
```bash
./scripts/dlq-recovery.sh \
  --file 123456789012345678.json \
  --topic event.audit.v1 \
  --broker localhost:9094
```

5. **Verify recovery:**
- Check Kafka UI for recovered events
- Verify backup files are processed (moved to `recovered/` subdirectory)

---

### Procedure 2: Local Backup File Recovery

**When to use:** Both main topic and DLQ failed, events backed up to local files.

**Steps:**

1. **List backup files:**
```bash
ls -la ./dlq-backup/*.json
```

2. **Validate JSON format:**
```bash
# Check all files
for f in ./dlq-backup/*.json; do
  jq empty "$f" 2>/dev/null || echo "Invalid: $f"
done
```

3. **Use recovery script:**
```bash
./scripts/dlq-recovery.sh \
  --dir ./dlq-backup \
  --topic event.audit.v1 \
  --broker localhost:9094
```

4. **Manual recovery (if script fails):**
```bash
# For each backup file
EVENT_ID="123456789012345678"

cat ./dlq-backup/${EVENT_ID}.json | \
  kafka-console-producer.sh \
  --broker-list localhost:9094 \
  --topic event.audit.v1
```

5. **Archive recovered files:**
```bash
mkdir -p ./dlq-backup/recovered
mv ./dlq-backup/*.json ./dlq-backup/recovered/
```

---

### Procedure 3: Outbox Event Recovery

**When to use:** Outbox events stuck in FAILED status after circuit breaker issues.

**Steps:**

1. **Check outbox statistics:**
```bash
curl http://localhost:8081/actuator/curve-metrics | jq '.summary'
```

2. **Query failed events (requires database access):**
```sql
-- List failed events
SELECT id, event_id, aggregate_type, aggregate_id, status, retry_count, last_error
FROM curve_outbox_event
WHERE status = 'FAILED'
ORDER BY occurred_at DESC
LIMIT 100;

-- Count by status
SELECT status, COUNT(*) as count
FROM curve_outbox_event
GROUP BY status;
```

3. **Reset failed events for retry:**
```sql
-- Reset specific event
UPDATE curve_outbox_event
SET status = 'PENDING', retry_count = 0, last_error = NULL, next_retry_at = NOW()
WHERE id = 'specific-event-id';

-- Reset all failed events (use with caution)
UPDATE curve_outbox_event
SET status = 'PENDING', retry_count = 0, last_error = NULL, next_retry_at = NOW()
WHERE status = 'FAILED';
```

4. **Monitor recovery:**
```bash
watch -n 5 'curl -s http://localhost:8081/actuator/curve-metrics | jq ".summary"'
```

---

### Procedure 4: Circuit Breaker Reset

**When to use:** Circuit breaker stuck in OPEN state after Kafka recovery.

**Steps:**

1. **Verify Kafka is healthy:**
```bash
curl http://localhost:8081/actuator/health/curve
```

2. **Check circuit breaker state:**
```bash
curl http://localhost:8081/actuator/curve-metrics | jq '.summary.circuitBreakerState'
```

3. **Wait for automatic half-open (60 seconds)**

   The circuit breaker will automatically transition to HALF-OPEN state after 60 seconds, allowing test requests.

4. **Alternative: Restart application:**
```bash
# Graceful shutdown
kill -TERM $(pgrep -f 'your-application')

# Or via actuator (if enabled)
curl -X POST http://localhost:8081/actuator/shutdown
```

5. **Monitor state transition:**
```bash
watch -n 10 'curl -s http://localhost:8081/actuator/curve-metrics | jq ".summary.circuitBreakerState"'
```

---

## Alert Configuration

### Prometheus Alert Rules

```yaml
groups:
  - name: curve-alerts
    rules:
      # DLQ Events Alert
      - alert: CurveDlqEventsHigh
        expr: curve_events_dlq_count_total > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High DLQ event count"
          description: "{{ $value }} events accumulated in DLQ"

      # Success Rate Alert
      - alert: CurveSuccessRateLow
        expr: (curve_events_published_success_total / curve_events_published_total) < 0.95
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Low event publishing success rate"
          description: "Success rate is {{ $value | humanizePercentage }}"

      # Circuit Breaker Alert
      - alert: CurveCircuitBreakerOpen
        expr: curve_circuit_breaker_state == 1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker is OPEN"
          description: "Outbox publisher circuit breaker is open, events are not being published"

      # Kafka Producer Down
      - alert: CurveKafkaProducerDown
        expr: curve_health_status == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Curve Kafka producer is down"
          description: "Kafka producer failed to initialize or is unhealthy"

      # High Latency Alert
      - alert: CurvePublishLatencyHigh
        expr: histogram_quantile(0.95, curve_events_publish_duration_seconds_bucket) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High event publishing latency"
          description: "95th percentile latency is {{ $value }}s"

      # Outbox Backlog Alert
      - alert: CurveOutboxBacklogHigh
        expr: curve_outbox_pending_total > 1000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High outbox backlog"
          description: "{{ $value }} events pending in outbox"
```

### Grafana Dashboard Panels

Recommended panels for Curve monitoring dashboard:

1. **Event Publishing Rate** - `rate(curve_events_published_total[5m])`
2. **Success Rate Gauge** - Current success percentage
3. **DLQ Event Count** - `curve_events_dlq_count_total` over time
4. **Publishing Latency** - `histogram_quantile(0.95, curve_events_publish_duration_seconds_bucket)`
5. **Circuit Breaker State** - Current state indicator (CLOSED/OPEN/HALF-OPEN)
6. **Outbox Queue Depth** - `curve_outbox_pending_total` over time
7. **Retry Count** - `rate(curve_events_retry_count_total[5m])`
8. **Kafka Errors** - `curve_kafka_producer_errors_total` over time

---

## Runbook Checklist

### Daily Operations

- [ ] Check `/actuator/health/curve` status
- [ ] Review `/actuator/curve-metrics` summary
- [ ] Verify DLQ topic is empty or stable
- [ ] Check for local backup files in `./dlq-backup/`
- [ ] Review application logs for WARN/ERROR entries

### Weekly Operations

- [ ] Review DLQ event patterns and root causes
- [ ] Analyze publishing latency trends
- [ ] Verify outbox cleanup job ran successfully
- [ ] Archive old backup files (if any)
- [ ] Review and rotate logs

### Incident Response

- [ ] Identify affected time range
- [ ] Check circuit breaker state history
- [ ] Count events in DLQ and local backup
- [ ] Determine root cause (Kafka, network, configuration)
- [ ] Execute appropriate recovery procedure
- [ ] Verify event delivery to consumers
- [ ] Document incident in post-mortem

### Monthly Operations

- [ ] Review alert thresholds and adjust if needed
- [ ] Analyze success rate trends
- [ ] Capacity planning based on event volume
- [ ] Review and update this runbook if necessary

---

## Additional Resources

- [Configuration Guide](CONFIGURATION.en.md) - Detailed configuration options
- [DLQ Recovery Script](../scripts/dlq-recovery.sh) - Automated recovery tool
- [Sample Application](../sample/) - Working examples
- [README](../README.md) - Project overview and quick start
