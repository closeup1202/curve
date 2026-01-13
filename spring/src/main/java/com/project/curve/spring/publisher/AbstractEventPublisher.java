package com.project.curve.spring.publisher;

import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.validation.EventValidator;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractEventPublisher implements EventProducer {

    private final EventEnvelopeFactory envelopeFactory;
    private final EventContextProvider eventContextProvider;

    @Override
    public <T extends DomainEventPayload> void publish(T payload) {
        publish(payload, EventSeverity.INFO);
    }

    @Override
    public <T extends DomainEventPayload> void publish(T payload, EventSeverity severity) {
        EventEnvelope<T> envelope = envelopeFactory.create(
                payload.getEventType(),
                severity,
                eventContextProvider.currentMetadata(payload),
                payload
        );

        EventValidator.validate(envelope);
        send(envelope);
    }

    protected abstract <T extends DomainEventPayload> void send(EventEnvelope<T> envelope);
}
