---
title: Quick Start - Curve Event Publishing Library
description: Get started with Curve in 5 minutes. Learn how to add declarative event publishing to your Spring Boot application with Kafka support.
keywords: curve quick start, spring boot kafka tutorial, event publishing setup, microservices tutorial
---

# Quick Start

Get Curve up and running in your Spring Boot application in under 5 minutes.

## Prerequisites

- Java 17 or higher
- Spring Boot 3.x
- Apache Kafka (or Docker)

## Step 1: Add Dependency

Add Curve to your project:

=== "Gradle"

    ```gradle title="build.gradle"
    dependencies {
        implementation 'io.github.closeup1202:curve:0.1.0'
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <dependency>
        <groupId>io.github.closeup1202</groupId>
        <artifactId>curve</artifactId>
        <version>0.1.0</version>
    </dependency>
    ```

## Step 2: Configure Kafka

Add Kafka configuration to your `application.yml`:

```yaml title="application.yml"
spring:
  kafka:
    bootstrap-servers: localhost:9092

curve:
  enabled: true
  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
```

!!! tip "Local Kafka Setup"
    Don't have Kafka running? Use Docker Compose:

    ```bash
    docker-compose up -d
    ```

## Step 3: Publish Your First Event

Add the `@PublishEvent` annotation to any service method:

```java title="OrderService.java"
import io.github.closeup1202.curve.spring.audit.annotation.PublishEvent;
import io.github.closeup1202.curve.core.type.EventSeverity;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @PublishEvent(
        eventType = "ORDER_CREATED",
        severity = EventSeverity.INFO
    )
    public Order createOrder(OrderRequest request) {
        // Your business logic
        return orderRepository.save(new Order(request));
    }
}
```

## Step 4: Verify

Start your application and create an order. Check the Kafka topic:

```bash
# View events in Kafka
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic event.audit.v1 --from-beginning
```

**Expected output:**

```json
{
  "eventId": "7355889748156289024",
  "eventType": "ORDER_CREATED",
  "occurredAt": "2026-02-03T10:30:00Z",
  "publishedAt": "2026-02-03T10:30:00.123Z",
  "severity": "INFO",
  "metadata": {
    "source": {
      "serviceName": "order-service",
      "serviceVersion": "1.0.0",
      "hostname": "localhost"
    },
    "actor": {
      "userId": "user123",
      "sessionId": "session-abc"
    },
    "trace": {
      "traceId": "trace-xyz",
      "spanId": "span-123"
    }
  },
  "payload": {
    "orderId": 12345,
    "customerId": "CUST-001",
    "amount": 99.99
  }
}
```

## Step 5: Monitor (Optional)

Check health and metrics:

```bash
# Health check
curl http://localhost:8080/actuator/health/curve

# Metrics
curl http://localhost:8080/actuator/curve-metrics
```

---

## :tada: Success!

You've successfully published your first event with Curve!

## Next Steps

<div class="grid cards" markdown>

-   :material-book-open-variant:{ .lg .middle } **Learn Features**

    ---

    Explore PII protection, DLQ, and observability

    [:octicons-arrow-right-24: Features](../features/overview.md)

-   :material-cog:{ .lg .middle } **Advanced Configuration**

    ---

    Production-ready settings and optimization

    [:octicons-arrow-right-24: Configuration](../CONFIGURATION.md)

-   :material-code-braces:{ .lg .middle } **API Reference**

    ---

    Detailed annotation and property reference

    [:octicons-arrow-right-24: API Docs](../api/annotations.md)

-   :material-help-circle:{ .lg .middle } **Need Help?**

    ---

    Troubleshooting and FAQ

    [:octicons-arrow-right-24: Troubleshooting](../TROUBLESHOOTING.md)

</div>

---

## Common Issues

!!! warning "Kafka Connection Failed"
    If you see `Connection to node -1 could not be established`, ensure Kafka is running:

    ```bash
    docker-compose ps
    ```

!!! warning "Events Not Publishing"
    1. Check `curve.enabled=true` in application.yml
    2. Verify Kafka bootstrap servers
    3. Check logs for errors

See [Troubleshooting Guide](../TROUBLESHOOTING.md) for more solutions.
