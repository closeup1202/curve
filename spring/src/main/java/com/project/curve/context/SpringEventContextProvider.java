package com.project.curve.context;

import com.project.curve.envelope.EventMetadata;
import com.project.curve.payload.DomainEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringEventContextProvider implements EventContextProvider {

    private final ActorContextProvider actorProvider;
    private final TraceContextProvider traceProvider;
    private final SourceContextProvider sourceProvider;
    private final SchemaContextProvider schemaProvider;

    @Override
    public EventMetadata currentMetadata(DomainEventPayload payload) {
        return new EventMetadata(
                sourceProvider.getSource(),
                actorProvider.getActor(),
                traceProvider.getTrace(),
                schemaProvider.getSchema(),
                null
        );
    }
}
