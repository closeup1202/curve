package com.project.curve.spring.publisher;

import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import com.project.curve.core.validation.DefaultEventValidator;
import com.project.curve.core.validation.EventValidator;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AbstractEventPublisher implements EventProducer {

    private final EventEnvelopeFactory envelopeFactory;
    private final EventContextProvider eventContextProvider;
    private final EventValidator eventValidator;

    // 하위 호환성을 위한 생성자 (EventValidator가 없는 경우 기본값 사용)
    public AbstractEventPublisher(EventEnvelopeFactory envelopeFactory, EventContextProvider eventContextProvider) {
        this(envelopeFactory, eventContextProvider, new DefaultEventValidator());
    }

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

        eventValidator.validate(envelope);
        send(envelope);
    }

    protected abstract <T extends DomainEventPayload> void send(EventEnvelope<T> envelope);
}
