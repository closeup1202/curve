package com.project.curve.autoconfigure;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "curve")
public class CurveProperties {

    private final boolean enabled = true;

    private final Kafka kafka = new Kafka();

    @Data
    public static class Kafka {
        private String topic = "event.audit.v1";
    }
}