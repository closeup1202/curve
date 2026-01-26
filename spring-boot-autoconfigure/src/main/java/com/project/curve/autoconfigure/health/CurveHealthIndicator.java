package com.project.curve.autoconfigure.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Curve 이벤트 발행 시스템의 Health Indicator.
 * <p>
 * Kafka Producer의 상태를 확인하여 이벤트 발행 시스템이 정상 작동 중인지 점검합니다.
 *
 * <h3>Health 상태 기준</h3>
 * <ul>
 *   <li>UP: Kafka Producer가 정상적으로 초기화되고 메트릭 수집 가능</li>
 *   <li>DOWN: Kafka Producer가 null이거나 초기화 실패</li>
 * </ul>
 *
 * <h3>Health 세부 정보</h3>
 * <ul>
 *   <li>kafkaProducerInitialized: Kafka Producer 초기화 여부</li>
 *   <li>producerMetrics: Producer 메트릭 수 (connection 상태 간접 확인)</li>
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

            // Producer 메트릭을 통해 연결 상태 간접 확인
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
