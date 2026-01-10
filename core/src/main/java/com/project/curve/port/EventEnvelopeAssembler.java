package com.project.curve.port;

import com.project.curve.envelope.EventEnvelope;
import com.project.curve.payload.DomainEventPayload;
import com.project.curve.type.EventSeverity;

public interface EventEnvelopeAssembler {
    <T extends DomainEventPayload> EventEnvelope<T> assemble(T payload, EventSeverity severity);
}