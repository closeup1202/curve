package com.project.curve.port;

import com.project.curve.envelope.*;
import com.project.curve.payload.DomainEventPayload;
import com.project.curve.support.ActorContextProvider;
import com.project.curve.support.TraceContextProvider;
import com.project.curve.type.EventSeverity;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;

@RequiredArgsConstructor
public abstract class AbstractEventProducer implements EventProducer {

    private final IdGenerator idGenerator;
    private final ClockProvider clockProvider;
    private final EventSource source; // 현재 서비스 정보
    private final TraceContextProvider traceProvider;
    private final ActorContextProvider actorProvider;

    @Override
    public <T extends DomainEventPayload> void publish(T payload) {
        publish(payload, EventSeverity.INFO);
    }

    @Override
    public <T extends DomainEventPayload> void publish(T payload, EventSeverity severity) {
        // 1. 모든 정보 수집 및 Envelope 생성
        EventEnvelope<T> envelope = EventEnvelope.create(
                payload.getEventType(),
                severity,
                source,
                actorProvider.getActor(),
                traceProvider.getTrace(),
                payload.getSchema(),
                payload,
                new HashMap<>(), // 추가 태그가 필요하면 여기서 확장
                clockProvider,
                idGenerator
        );

        // 2. 유효성 검사 (필요 시)
        // EventValidator.validate(envelope);

        // 3. 실제 전송 (구현체에게 위임)
        send(envelope);
    }

    // 하위 모듈(curve-kafka 등)에서 구현할 실제 전송 메서드
    protected abstract <T extends DomainEventPayload> void send(EventEnvelope<T> envelope);
}
