package com.project.curve.envelope;

import com.project.curve.payload.DomainEventPayload;
import com.project.curve.type.EventSeverity;
import com.project.curve.type.EventType;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Getter
@ToString
public final class EventEnvelope<T extends DomainEventPayload> {

    private final String specVersion;
    private final String eventId;
    private final String eventType;
    private final EventSeverity severity;

    private final Instant occurredAt;
    private final Instant publishedAt;

    private final EventSource source;
    private final EventActor actor;
    private final EventTrace trace;
    private final EventSchema schema;

    private final T data;
    private final Map<String, String> tags;

    // 내부 생성자: 팩토리 메서드를 통해서만 생성을 허용
    private EventEnvelope(
            String eventId,
            EventType eventType,
            EventSeverity severity,
            EventSource source,
            EventActor actor,
            EventTrace trace,
            EventSchema schema,
            T data,
            Map<String, String> tags,
            Instant occurredAt,
            Instant publishedAt
    ) {
        this.specVersion = "1.0"; // 기본값 설정
        this.eventId = eventId;
        this.eventType = eventType.getValue();
        this.severity = severity;
        this.source = source;
        this.actor = actor;
        this.trace = trace;
        this.schema = schema;
        this.data = data;
        this.tags = (tags != null) ? Map.copyOf(tags) : Collections.emptyMap();
        this.occurredAt = occurredAt;
        this.publishedAt = publishedAt;
    }

    /**
     * 새로운 이벤트를 생성할 때 사용하는 정적 팩토리 메서드
     */
    public static <T extends DomainEventPayload> EventEnvelope<T> create(
            EventType eventType,
            EventSeverity severity,
            EventSource source,
            EventActor actor,
            EventTrace trace,
            EventSchema schema,
            T data,
            Map<String, String> tags,
            ClockProvider clock
    ) {
        Instant now = clock.now();
        return new EventEnvelope<>(
                IdGenerator.generate(), // ID 자동 생성
                eventType,
                severity,
                source,
                actor,
                trace,
                schema,
                data,
                tags,
                now, // 발생 시점
                now  // 발행 시점 (초기엔 동일)
        );
    }
}
