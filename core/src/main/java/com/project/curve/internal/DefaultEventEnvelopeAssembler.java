package com.project.curve.internal;

import com.project.curve.envelope.EventEnvelope;
import com.project.curve.envelope.EventSchema;
import com.project.curve.envelope.EventSource;
import com.project.curve.payload.DomainEventPayload;
import com.project.curve.port.ClockProvider;
import com.project.curve.port.EventEnvelopeAssembler;
import com.project.curve.port.IdGenerator;
import com.project.curve.support.ActorContextProvider;
import com.project.curve.support.TraceContextProvider;
import com.project.curve.type.EventSeverity;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DefaultEventEnvelopeAssembler implements EventEnvelopeAssembler {

    private final ClockProvider clock;
    private final EventSource source;
    private final TraceContextProvider traceProvider;
    private final ActorContextProvider actorProvider;
    private final IdGenerator idGenerator;

    @Override
    public <T extends DomainEventPayload> EventEnvelope<T> assemble(T payload, EventSeverity severity) {
        return EventEnvelope.create(
                payload.getEventType(),
                severity,
                source,
                actorProvider.getActor(),
                traceProvider.getTrace(),
                payload.getSchema(),
                payload,
                null,
                clock,
                idGenerator
        );
    }
}
