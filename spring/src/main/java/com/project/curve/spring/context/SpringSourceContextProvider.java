package com.project.curve.spring.context;

import com.project.curve.core.context.SourceContextProvider;
import com.project.curve.core.envelope.EventSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Slf4j
@Component
public class SpringSourceContextProvider implements SourceContextProvider {

    private final String service;
    private final String environment;
    private final String instanceId;
    private final String host;
    private final String version;

    public SpringSourceContextProvider(
            @Value("${spring.application.name:unknown-service}") String service,
            Environment env,
            @Value("${curve.source.version:1.0.0}") String version) {
        this.service = service;
        this.environment = determineEnvironment(env);
        this.instanceId = generateInstanceId();
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

    private String generateInstanceId() {
        // 컨테이너 환경에서는 호스트네임이 인스턴스 ID로 사용될 수 있음
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        // 그 외에는 UUID 생성
        return UUID.randomUUID().toString();
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
