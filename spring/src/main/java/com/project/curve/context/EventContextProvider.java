package com.project.curve.context;

import com.project.curve.envelope.EventMetadata;
import com.project.curve.payload.DomainEventPayload;

public interface EventContextProvider {
    EventMetadata currentMetadata(DomainEventPayload payload);
}
