package com.project.curve.envelope;

import com.project.curve.payload.DomainEventPayload;
import com.project.curve.type.EventSeverity;
import com.project.curve.type.EventType;

import java.time.Instant;

public record EventEnvelope<T extends DomainEventPayload>(
        EventId eventId,
        EventType eventType,
        EventSeverity severity,
        EventMetadata metadata,
        T payload,
        Instant occurredAt,
        Instant publishedAt
) {

    public static <T extends DomainEventPayload> EventEnvelope<T> of(
            EventId eventId,
            EventType eventType,
            EventSeverity severity,
            EventMetadata metadata,
            T payload,
            Instant occurredAt,
            Instant publishedAt
    ) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                severity,
                metadata,
                payload,
                occurredAt,
                publishedAt
        );
    }
}
