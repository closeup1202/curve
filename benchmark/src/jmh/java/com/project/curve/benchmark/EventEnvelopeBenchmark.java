package com.project.curve.benchmark;

import com.project.curve.core.envelope.EventActor;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.envelope.EventId;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.envelope.EventSchema;
import com.project.curve.core.envelope.EventSource;
import com.project.curve.core.envelope.EventTrace;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import lombok.Getter;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for EventEnvelope creation performance.
 *
 * <p>Tests various scenarios including:
 * <ul>
 *   <li>Baseline envelope creation</li>
 *   <li>With/without metadata</li>
 *   <li>Different payload sizes</li>
 * </ul>
 *
 * <p>Run with: ./gradlew :benchmark:jmh -PjmhInclude=EventEnvelopeBenchmark
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@State(Scope.Benchmark)
public class EventEnvelopeBenchmark {

    @Param({"SMALL", "LARGE"})
    private PayloadSize payloadSize;

    private EventId eventId;
    private EventType eventType;
    private EventMetadata fullMetadata;
    private EventMetadata minimalMetadata;
    private DomainEventPayload smallPayload;
    private DomainEventPayload largePayload;
    private Instant occurredAt;
    private Instant publishedAt;

    @Setup(Level.Trial)
    public void setup() {
        // Event ID
        this.eventId = EventId.of("1234567890123456");

        // Event Type
        this.eventType = () -> "ORDER_CREATED";

        // Metadata - realistic production data
        EventSource source = new EventSource(
                "order-service",
                "production",
                "instance-01",
                "prod-server-01",
                "1.0.0",
                "correlation-abc123",
                "causation-xyz789",
                "root-event-456"
        );

        EventActor actor = new EventActor(
                "user-12345",
                "CUSTOMER",
                "10.0.1.100"
        );

        EventTrace trace = new EventTrace(
                "trace-abc123",
                "span-xyz789",
                "correlation-abc123"
        );

        EventSchema schema = EventSchema.of("OrderCreatedEvent", 1);

        this.fullMetadata = new EventMetadata(
                source,
                actor,
                trace,
                schema,
                Map.of(
                        "environment", "production",
                        "region", "us-east-1",
                        "version", "v1"
                )
        );

        // Minimal metadata with only required fields
        EventSource minimalSource = new EventSource(
                "order-service",
                null,
                null,
                null,
                null
        );

        EventActor minimalActor = new EventActor(null, null, null);
        EventTrace minimalTrace = new EventTrace(null, null, null);
        EventSchema minimalSchema = EventSchema.of("OrderCreatedEvent", 1);

        this.minimalMetadata = new EventMetadata(
                minimalSource,
                minimalActor,
                minimalTrace,
                minimalSchema,
                Map.of()
        );

        // Payloads
        this.smallPayload = new TestPayload("order-001", "Product A", 1);
        this.largePayload = new TestPayload(
                "order-" + "x".repeat(100),
                "Product " + "B".repeat(50),
                1000
        );

        // Timestamps
        this.occurredAt = Instant.now();
        this.publishedAt = Instant.now();
    }

    /**
     * Baseline benchmark - EventEnvelope creation with full metadata and configurable payload size.
     */
    @Benchmark
    public void createEnvelope(Blackhole blackhole) {
        DomainEventPayload payload = payloadSize == PayloadSize.SMALL ? smallPayload : largePayload;

        EventEnvelope<DomainEventPayload> envelope = EventEnvelope.of(
                eventId,
                eventType,
                EventSeverity.INFO,
                fullMetadata,
                payload,
                occurredAt,
                publishedAt
        );

        blackhole.consume(envelope);
    }

    /**
     * Benchmark with minimal metadata and small payload.
     */
    @Benchmark
    public void createEnvelope_Minimal(Blackhole blackhole) {
        EventEnvelope<DomainEventPayload> envelope = EventEnvelope.of(
                eventId,
                eventType,
                EventSeverity.INFO,
                minimalMetadata,
                smallPayload,
                occurredAt,
                publishedAt
        );

        blackhole.consume(envelope);
    }

    /**
     * Benchmark with full configuration (full metadata, large payload).
     */
    @Benchmark
    public void createEnvelope_Full(Blackhole blackhole) {
        EventEnvelope<DomainEventPayload> envelope = EventEnvelope.of(
                eventId,
                eventType,
                EventSeverity.INFO,
                fullMetadata,
                largePayload,
                occurredAt,
                publishedAt
        );

        blackhole.consume(envelope);
    }

    /**
     * Benchmark with field access to test record accessor performance.
     */
    @Benchmark
    public void createEnvelope_WithFieldAccess(Blackhole blackhole) {
        DomainEventPayload payload = payloadSize == PayloadSize.SMALL ? smallPayload : largePayload;

        // Create envelope
        EventEnvelope<DomainEventPayload> envelope = EventEnvelope.of(
                eventId,
                eventType,
                EventSeverity.INFO,
                fullMetadata,
                payload,
                occurredAt,
                publishedAt
        );

        // Access all fields (tests record accessor performance)
        blackhole.consume(envelope.eventId());
        blackhole.consume(envelope.eventType());
        blackhole.consume(envelope.severity());
        blackhole.consume(envelope.metadata());
        blackhole.consume(envelope.payload());
        blackhole.consume(envelope.occurredAt());
        blackhole.consume(envelope.publishedAt());
        blackhole.consume(envelope);
    }

    /**
     * Payload size enumeration for parameterized tests.
     */
    public enum PayloadSize {
        SMALL,
        LARGE
    }

    /**
     * Test payload implementation with realistic data.
     */
    @Getter
    static class TestPayload implements DomainEventPayload {
        private final String orderId;
        private final String productName;
        private final int quantity;
        private final EventType eventType;

        public TestPayload(String orderId, String productName, int quantity) {
            this.orderId = orderId;
            this.productName = productName;
            this.quantity = quantity;
            this.eventType = () -> "ORDER_CREATED";
        }

        @Override
        public EventType getEventType() {
            return eventType;
        }
    }
}
