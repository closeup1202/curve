package com.project.curve.publisher;

import com.project.curve.context.EventContextProvider;
import com.project.curve.envelope.EventEnvelope;
import com.project.curve.factory.EventEnvelopeFactory;
import com.project.curve.payload.DomainEventPayload;
import com.project.curve.port.EventProducer;
import com.project.curve.type.EventSeverity;
import com.project.curve.validation.EventValidator;
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

        // 2. 유효성 검사
        EventValidator.validate(envelope);

        // 3. 실제 전송 (구현체에게 위임)
        send(envelope);
    }

    // 하위 모듈(curve-kafka 등)에서 구현할 실제 전송 메서드
    protected abstract <T extends DomainEventPayload> void send(EventEnvelope<T> envelope);
}
