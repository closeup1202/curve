package com.project.curve.core.validation;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.exception.InvalidEventException;

public final class EventValidator {

    private EventValidator() {}

    public static void validate(EventEnvelope<?> event) {
        if (event == null) {
            throw new InvalidEventException("event must not be null");
        }
        if (event.eventId() == null) {
            throw new InvalidEventException("eventId is required");
        }
        if (event.eventType() == null) {
            throw new InvalidEventException("eventType is required");
        }
        if (event.metadata() == null) {
            throw new InvalidEventException("metadata is required");
        }
        if (event.payload() == null) {
            throw new InvalidEventException("payload is required");
        }
        if (event.occurredAt() == null || event.publishedAt() == null) {
            throw new InvalidEventException("event timestamps are required");
        }
        if (event.occurredAt().isAfter(event.publishedAt())) {
            throw new InvalidEventException("occurredAt must be <= publishedAt");
        }
    }
}