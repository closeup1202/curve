package com.project.curve.validation;

import com.project.curve.envelope.EventEnvelope;
import com.project.curve.exception.InvalidEventException;

public class EventValidator {

    public static void validate(EventEnvelope<?> envelope) {
        if (envelope == null) {
            throw new InvalidEventException("Envelope cannot be null");
        }
        if (envelope.getEventId() == null || envelope.getEventId().isBlank()) {
            throw new InvalidEventException("Event ID is mandatory");
        }
        if (envelope.getSource() == null) {
            throw new InvalidEventException("Source cannot be null");
        }
    }
}