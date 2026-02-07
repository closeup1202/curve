package com.project.curve.spring.factory;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.port.ClockProvider;
import com.project.curve.core.port.IdGenerator;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.type.EventType;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public record EventEnvelopeFactory(ClockProvider clock, IdGenerator idGenerator) {

    public <T extends DomainEventPayload> EventEnvelope<T> create(
            EventType eventType,
            EventSeverity severity,
            EventMetadata metadata,
            T payload
    ) {
        Instant now = clock.now();
        var eventId = idGenerator.generate();

        if (log.isDebugEnabled()) {
            log.debug("Creating event envelope: eventId={}, eventType={}, severity={}, actor={}",
                    eventId, eventType, severity, metadata.actor());
        }

        return EventEnvelope.of(
                eventId,
                eventType,
                severity,
                metadata,
                payload,
                now,       // occurredAt: when the domain event occurred
                now // publishedAt: when the envelope is being published
        );
    }
}
