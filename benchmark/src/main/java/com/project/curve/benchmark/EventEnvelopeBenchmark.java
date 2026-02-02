package com.project.curve.benchmark;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.envelope.EventId;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import org.openjdk.jmh.annotations.*;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)          // 평균 실행 시간
@OutputTimeUnit(TimeUnit.NANOSECONDS)     // ns 단위
@Warmup(iterations = 3, time = 1)         // 워밍업
@Measurement(iterations = 5, time = 1)    // 측정
@Fork(1)
@State(Scope.Thread)
public class EventEnvelopeBenchmark {

    private EventId eventId;
    private EventType eventType;
    private EventMetadata metadata;
    private DomainEventPayload payload;
    private Instant occurredAt;
    private Instant publishedAt;

    @Setup(Level.Trial)
    public void setup() {
        this.eventId = EventId.of("1234567890");
        this.eventType = () -> "ORDER_CREATED"; // 예: Functional Interface라면
        this.metadata = null;  // 또는 테스트용 메타데이터
        this.payload = new TestPayload();

        this.occurredAt = Instant.now();
        this.publishedAt = Instant.now();
    }

    @Benchmark
    public EventEnvelope<DomainEventPayload> createEnvelope() {
        return EventEnvelope.of(
                eventId,
                eventType,
                EventSeverity.INFO,
                metadata,
                payload,
                occurredAt,
                publishedAt
        );
    }

    /**
     * 테스트용 Payload
     */
    static class TestPayload implements DomainEventPayload {
        @Override
        public EventType getEventType() {
            return null;
        }
    }
}