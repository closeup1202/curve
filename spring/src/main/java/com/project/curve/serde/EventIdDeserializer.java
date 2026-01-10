package com.project.curve.serde;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.project.curve.envelope.EventId;

import java.io.IOException;

public final class EventIdDeserializer extends JsonDeserializer<EventId> {
    @Override
    public EventId deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        return EventId.of(p.getValueAsString());
    }
}
