package com.project.curve.spring.context;

import com.project.curve.core.context.ActorContextProvider;
import com.project.curve.core.context.SchemaContextProvider;
import com.project.curve.core.context.SourceContextProvider;
import com.project.curve.core.context.TraceContextProvider;
import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;
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
