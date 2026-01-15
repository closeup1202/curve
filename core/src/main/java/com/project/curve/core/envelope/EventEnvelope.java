package com.project.curve.core.envelope;

import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import lombok.NonNull;

import java.time.Instant;

public record EventEnvelope<T extends DomainEventPayload>(
        @NonNull EventId eventId,
        @NonNull EventType eventType,
        @NonNull EventSeverity severity,
        @NonNull EventMetadata metadata,
        @NonNull T payload,
        @NonNull Instant occurredAt,
        @NonNull Instant publishedAt
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
