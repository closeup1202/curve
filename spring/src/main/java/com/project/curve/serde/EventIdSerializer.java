package com.project.curve.serde;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.project.curve.envelope.EventId;

import java.io.IOException;

public final class EventIdSerializer extends JsonSerializer<EventId> {
    @Override
    public void serialize(EventId value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeString(value.value());
    }
}
