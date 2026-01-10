package com.project.curve.factory;

import com.project.curve.envelope.EventEnvelope;
import com.project.curve.envelope.EventMetadata;
import com.project.curve.payload.DomainEventPayload;
import com.project.curve.port.ClockProvider;
import com.project.curve.port.IdGenerator;
import com.project.curve.type.EventSeverity;
import com.project.curve.type.EventType;

import java.time.Instant;

public record EventEnvelopeFactory(ClockProvider clock, IdGenerator idGenerator) {

    public <T extends DomainEventPayload> EventEnvelope<T> create(
            EventType eventType,
            EventSeverity severity,
            EventMetadata metadata,
            T payload
    ) {
        Instant now = clock.now();
        return EventEnvelope.of(
                idGenerator.generate(),
                eventType,
                severity,
                metadata,
                payload,
                now,
                now
        );
    }
}