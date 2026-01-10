package com.project.curve.envelope;

public record EventSource(
        String service,
        String environment,
        String instanceId,
        String host,
        String version
) {
    public EventSource {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service is required");
        }
    }
}
