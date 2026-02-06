package com.project.curve.autoconfigure.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Health indicator for the Curve event publishing system.
 * <p>
 * Checks the status of the Kafka Producer to verify if the event publishing system is operating normally.
 *
 * <h3>Health Status Criteria</h3>
 * <ul>
 *   <li>UP: Kafka Producer is properly initialized and the broker is reachable</li>
 *   <li>DOWN: Kafka Producer is null, initialization failed, or broker is unreachable</li>
 * </ul>
 *
 * <h3>Health Details</h3>
 * <ul>
 *   <li>kafkaProducerInitialized: Whether Kafka Producer is initialized</li>
 *   <li>clusterId: Kafka cluster ID (verifies actual broker connectivity)</li>
 * </ul>
 *
 * @see HealthIndicator
 * @see KafkaTemplate
 */
public record CurveHealthIndicator(
        KafkaTemplate<String, Object> kafkaTemplate,
        String topic,
        String dlqTopic) implements HealthIndicator {

    private static final long HEALTH_CHECK_TIMEOUT_SECONDS = 5;

    @Override
    public Health health() {
        try {
            if (kafkaTemplate == null) {
                return Health.down()
                        .withDetail("error", "KafkaTemplate is not initialized")
                        .build();
            }

            // Verify actual broker connectivity via AdminClient
            try (AdminClient adminClient = AdminClient.create(
                    kafkaTemplate.getProducerFactory().getConfigurationProperties())) {
                DescribeClusterResult cluster = adminClient.describeCluster();
                String clusterId = cluster.clusterId().get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                int nodeCount = cluster.nodes().get(HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS).size();

                return Health.up()
                        .withDetail("kafkaProducerInitialized", true)
                        .withDetail("clusterId", clusterId)
                        .withDetail("nodeCount", nodeCount)
                        .withDetail("topic", topic)
                        .withDetail("dlqTopic", dlqTopic != null ? dlqTopic : "disabled")
                        .build();
            }

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", "Kafka broker unreachable: " + e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}
