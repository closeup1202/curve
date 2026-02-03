# Troubleshooting Guide

This guide helps you diagnose and resolve common issues with Curve.

## Table of Contents

- [Event Publishing Issues](#event-publishing-issues)
- [Kafka Connection Issues](#kafka-connection-issues)
- [Outbox Pattern Issues](#outbox-pattern-issues)
- [PII Processing Issues](#pii-processing-issues)
- [ID Generation Issues](#id-generation-issues)
- [Performance Issues](#performance-issues)
- [Health Check Failures](#health-check-failures)

---

## Event Publishing Issues

### Events Not Being Published

**Symptoms:**
- `@PublishEvent` annotated methods execute but no events appear in Kafka
- No errors in logs

**Possible Causes & Solutions:**

1. **AOP not enabled**
   ```yaml
   curve:
     aop:
       enabled: true  # Ensure this is true
   ```

2. **Method not proxied** (Spring AOP limitation)
   ```java
   // BAD: Internal call bypasses AOP
   public void methodA() {
       methodB();  // @PublishEvent on methodB won't trigger
   }

   // GOOD: Use self-injection or refactor
   @Autowired
   private MyService self;

   public void methodA() {
       self.methodB();  // AOP will intercept
   }
   ```

3. **Exception thrown before event creation**
   - Check if method throws exception before returning
   - Events are only published on successful method completion

### Events Published But Not in Kafka

**Symptoms:**
- Logs show event creation but Kafka topic is empty
- DLQ topic has events

**Diagnosis:**
```bash
# Check DLQ topic
kafka-console-consumer --bootstrap-server localhost:9094 \
  --topic event.audit.dlq.v1 --from-beginning
```

**Solutions:**
1. Check Kafka connectivity (see [Kafka Connection Issues](#kafka-connection-issues))
2. Verify topic exists and has correct permissions
3. Check for serialization errors in logs

### Duplicate Events

**Symptoms:**
- Same event appears multiple times in Kafka

**Possible Causes:**
1. **Retry mechanism triggering**
   - Normal behavior when initial send fails
   - Check `curve.retry.max-attempts` setting

2. **Application restart during async send**
   - Use Outbox pattern for exactly-once semantics
   ```yaml
   curve:
     outbox:
       enabled: true
   ```

---

## Kafka Connection Issues

### Connection Refused

**Error:**
```
org.apache.kafka.common.errors.TimeoutException:
Failed to update metadata after 60000 ms
```

**Solutions:**

1. **Verify Kafka is running**
   ```bash
   docker ps | grep kafka
   # or
   nc -zv localhost 9094
   ```

2. **Check bootstrap servers configuration**
   ```yaml
   spring:
     kafka:
       bootstrap-servers: localhost:9094  # Correct address?
   ```

3. **Network/Firewall issues**
   - Ensure port 9094 is accessible
   - Check Docker network configuration

### SSL/TLS Handshake Failure

**Error:**
```
javax.net.ssl.SSLHandshakeException: PKIX path building failed
```

**Solutions:**
```yaml
spring:
  kafka:
    ssl:
      trust-store-location: classpath:kafka.truststore.jks
      trust-store-password: ${KAFKA_TRUSTSTORE_PASSWORD}
    properties:
      security.protocol: SSL
```

### Authentication Failure

**Error:**
```
org.apache.kafka.common.errors.SaslAuthenticationException
```

**Solutions:**
```yaml
spring:
  kafka:
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: PLAIN
      sasl.jaas.config: >
        org.apache.kafka.common.security.plain.PlainLoginModule required
        username="${KAFKA_USERNAME}"
        password="${KAFKA_PASSWORD}";
```

---

## Outbox Pattern Issues

### Events Stuck in PENDING Status

**Symptoms:**
- Events in outbox table remain in PENDING status
- No events being published to Kafka

**Diagnosis:**
```sql
SELECT status, COUNT(*) FROM curve_outbox_events
GROUP BY status;
```

**Solutions:**

1. **Publisher not enabled**
   ```yaml
   curve:
     outbox:
       enabled: true
       publisher-enabled: true  # Must be true
   ```

2. **Circuit breaker is open**
   - Check logs for: `Circuit breaker is OPEN`
   - Wait for recovery or fix Kafka connection
   ```sql
   -- Check failed events
   SELECT * FROM curve_outbox_events
   WHERE status = 'PENDING' AND retry_count > 0;
   ```

3. **Database lock contention**
   - Reduce batch size
   ```yaml
   curve:
     outbox:
       batch-size: 50  # Reduce from default 100
   ```

### Outbox Table Not Created

**Error:**
```
Table 'curve_outbox_events' doesn't exist
```

**Solutions:**

1. **Set initialization mode**
   ```yaml
   curve:
     outbox:
       initialize-schema: always  # Create if not exists
   ```

2. **Create manually with migration tool**
   ```sql
   CREATE TABLE curve_outbox_events (
       id VARCHAR(36) PRIMARY KEY,
       event_type VARCHAR(255) NOT NULL,
       payload TEXT NOT NULL,
       status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
       retry_count INT NOT NULL DEFAULT 0,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       published_at TIMESTAMP,
       error_message TEXT,
       INDEX idx_status_created (status, created_at)
   );
   ```

### Events Processed Multiple Times

**Symptoms:**
- Duplicate processing after restart
- Events published more than once

**Cause:** Multiple publisher instances running

**Solutions:**
1. **Use distributed lock** (recommended for clustered environments)
2. **Disable publisher on some instances**
   ```yaml
   # On secondary instances
   curve:
     outbox:
       publisher-enabled: false  # Let CDC handle publishing
   ```

---

## PII Processing Issues

### PII Fields Not Masked

**Symptoms:**
- Sensitive data appears in plain text in events

**Possible Causes:**

1. **Missing `@PiiField` annotation**
   ```java
   public class UserPayload {
       @PiiField(type = PiiType.EMAIL)  // Required!
       private String email;
   }
   ```

2. **PII processor not configured**
   ```yaml
   curve:
     pii:
       enabled: true
   ```

3. **Field is null or empty**
   - Null/empty fields are not processed

### Encryption Key Not Found

**Error:**
```
PiiEncryptionException: Encryption key not configured
```

**Solution:**
```bash
# Set environment variable
export CURVE_PII_ENCRYPTION_KEY="your-32-byte-base64-encoded-key"
```

Generate a key:
```java
SecureRandom random = new SecureRandom();
byte[] key = new byte[32];
random.nextBytes(key);
String base64Key = Base64.getEncoder().encodeToString(key);
```

### Decryption Failure

**Error:**
```
javax.crypto.AEADBadTagException: Tag mismatch!
```

**Possible Causes:**
1. Wrong encryption key
2. Corrupted encrypted data
3. Key rotation without re-encryption

**Solution:** Verify key consistency across environments

---

## ID Generation Issues

### Duplicate IDs Generated

**Symptoms:**
- `DuplicateKeyException` or constraint violations
- Same ID appearing for different events

**Cause:** Multiple instances using same worker ID

**Solutions:**

1. **Assign unique worker IDs**
   ```yaml
   # Instance 1
   curve:
     id-generator:
       worker-id: 1

   # Instance 2
   curve:
     id-generator:
       worker-id: 2
   ```

2. **Use environment-based configuration**
   ```yaml
   curve:
     id-generator:
       worker-id: ${WORKER_ID:1}
   ```

3. **Kubernetes StatefulSet**
   ```yaml
   curve:
     id-generator:
       worker-id: ${POD_ORDINAL:1}
   ```

### Worker ID Out of Range

**Error:**
```
IllegalArgumentException: Worker ID must be between 0 and 1023
```

**Solution:** Ensure worker ID is in valid range (0-1023)

### Clock Moved Backwards

**Error:**
```
ClockMovedBackwardsException: Clock moved backwards
```

**Cause:** System clock adjustment (NTP sync, VM migration)

**Solutions:**
1. Use NTP with slew mode instead of step mode
2. Implement clock skew tolerance (built-in: 5ms)
3. Restart application if clock difference is large

---

## Performance Issues

### High Latency in Event Publishing

**Diagnosis:**
```bash
# Check metrics endpoint
curl http://localhost:8080/actuator/curve-metrics
```

**Solutions:**

1. **Enable async mode**
   ```yaml
   curve:
     kafka:
       async-mode: true
       async-timeout-ms: 5000
   ```

2. **Tune Kafka producer**
   ```yaml
   spring:
     kafka:
       producer:
         batch-size: 16384
         linger-ms: 5
         buffer-memory: 33554432
   ```

3. **Reduce retry attempts**
   ```yaml
   curve:
     retry:
       max-attempts: 2
       initial-interval: 500
   ```

### Memory Issues with Large Events

**Symptoms:**
- OutOfMemoryError
- GC pressure

**Solutions:**
1. Limit payload size
2. Use compression
   ```yaml
   spring:
     kafka:
       producer:
         compression-type: lz4
   ```
3. Stream large data separately, reference by ID in events

### Outbox Table Growing Too Large

**Diagnosis:**
```sql
SELECT COUNT(*) FROM curve_outbox_events;
SELECT status, COUNT(*) FROM curve_outbox_events GROUP BY status;
```

**Solutions:**
```yaml
curve:
  outbox:
    cleanup-enabled: true
    retention-days: 3        # Reduce retention
    cleanup-cron: "0 0 * * * *"  # Run every hour
```

---

## Health Check Failures

### Curve Health Indicator DOWN

**Check health endpoint:**
```bash
curl http://localhost:8080/actuator/health/curve
```

**Possible causes:**
1. Kafka connection lost
2. Outbox publisher stopped
3. Too many failed events

**Diagnosis:**
```bash
# Full health details
curl http://localhost:8080/actuator/health/curve | jq
```

### Actuator Endpoint Not Available

**Error:** 404 on `/actuator/curve-metrics`

**Solution:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,curve-metrics
```

---

## Getting Help

If you can't resolve your issue:

1. **Check logs** with DEBUG level:
   ```yaml
   logging:
     level:
       com.project.curve: DEBUG
   ```

2. **Gather diagnostics:**
   - Application logs
   - Health endpoint output
   - Metrics endpoint output
   - Kafka topic state

3. **Open an issue** at https://github.com/closeup1202/curve/issues with:
   - Curve version
   - Spring Boot version
   - Java version
   - Configuration (sanitized)
   - Error messages and stack traces
   - Steps to reproduce
