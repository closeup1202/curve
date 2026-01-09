package com.project.curve.envelope;

public record EventSource(
        String serviceName,
        String environment,
        String instanceId,
        String host,
        String version
) {
}
