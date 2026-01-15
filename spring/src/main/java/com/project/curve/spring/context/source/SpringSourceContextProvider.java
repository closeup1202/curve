package com.project.curve.spring.context.source;

import com.project.curve.core.context.SourceContextProvider;
import com.project.curve.core.envelope.EventSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class SpringSourceContextProvider implements SourceContextProvider {

    private final String service;
    private final String environment;
    private final String instanceId;
    private final String host;
    private final String version;

    public SpringSourceContextProvider(String service, Environment env, String version) {
        this.service = service;
        this.environment = determineEnvironment(env);
        this.instanceId = resolveInstanceId();
        this.host = resolveHost();
        this.version = version;
    }

    @Override
    public EventSource getSource() {
        return new EventSource(service, environment, instanceId, host, version);
    }

    private String determineEnvironment(Environment env) {
        String[] activeProfiles = env.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return String.join(",", activeProfiles);
        }
        return env.getProperty("spring.profiles.default", "default");
    }

    private String resolveInstanceId() {
        return Optional.ofNullable(System.getenv("HOSTNAME"))
                .filter(h -> !h.isBlank())
                .orElseGet(() -> {
                    log.warn("HOSTNAME not set, using UUID for instance ID");
                    return UUID.randomUUID().toString();
                });
    }

    private String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Failed to resolve hostname, using 'unknown': {}", e.getMessage());
            return "unknown";
        }
    }
}
