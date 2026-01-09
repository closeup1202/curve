package com.project.curve.envelope;

import com.project.curve.type.EventSeverity;

import java.time.Instant;

public record EventMetadata(
        String eventType,
        String version,
        EventSeverity severity,
        Instant timestamp
) {
}