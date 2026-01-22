package com.project.curve.core.validation;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.InvalidEventException;

public final class EventValidator {

    private EventValidator() {
    }

    public static void validate(EventEnvelope<?> event) {
        if (event == null) {
            throw new InvalidEventException("event must not be null");
        }
        if (event.occurredAt().isAfter(event.publishedAt())) {
            throw new InvalidEventException("occurredAt must be <= publishedAt");
        }
    }
}