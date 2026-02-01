package com.project.curve.autoconfigure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Health indicator for the Curve event publishing system.
 * <p>
 * Checks the status of the Kafka Producer to verify if the event publishing system is operating normally.
 *
 * <h3>Health Status Criteria</h3>
 * <ul>
 *   <li>UP: Kafka Producer is properly initialized and metrics are collectible</li>
 *   <li>DOWN: Kafka Producer is null or initialization failed</li>
 * </ul>
 *
 * <h3>Health Details</h3>
 * <ul>
 *   <li>kafkaProducerInitialized: Whether Kafka Producer is initialized</li>
 *   <li>producerMetrics: Number of Producer metrics (indirect verification of connection status)</li>
 * </ul>
 *
 * @see HealthIndicator
 * @see KafkaTemplate
 */
public record CurveHealthIndicator(
        KafkaTemplate<String, Object> kafkaTemplate,
        String topic,
        String dlqTopic) implements HealthIndicator {

    @Override
    public Health health() {
        try {
            if (kafkaTemplate == null) {
                return Health.down()
                        .withDetail("error", "KafkaTemplate is not initialized")
                        .build();
            }

            // Indirectly verify connection status through Producer metrics
            int metricsCount = kafkaTemplate.metrics().size();

            return Health.up()
                    .withDetail("kafkaProducerInitialized", true)
                    .withDetail("producerMetrics", metricsCount)
                    .withDetail("topic", topic)
                    .withDetail("dlqTopic", dlqTopic != null ? dlqTopic : "disabled")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}
