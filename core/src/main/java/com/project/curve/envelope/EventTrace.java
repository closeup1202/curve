package com.project.curve.envelope;

public record EventTrace(
        String traceId,
        String spanId,
        String correlationId
) {
}
