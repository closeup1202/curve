package com.project.curve.autoconfigure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.spring.audit.aop.OutboxEventSaver;
import com.project.curve.spring.outbox.config.OutboxJpaRepositoryConfig;
import com.project.curve.spring.outbox.persistence.jdbc.JdbcOutboxEventRepository;
import com.project.curve.spring.outbox.persistence.jpa.entity.OutboxEventJpaEntity;
import com.project.curve.spring.outbox.publisher.OutboxEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Transactional Outbox Pattern Auto-Configuration.
 * <p>
 * Activated only when curve.outbox.enabled=true.
 *
 * <h3>Activation Conditions</h3>
 * <ul>
 *   <li>curve.outbox.enabled=true</li>
 *   <li>KafkaTemplate bean exists</li>
 * </ul>
 *
 * <h3>Registered Beans</h3>
 * <ul>
 *   <li>OutboxEventRepository (JPA or JDBC implementation)</li>
 *   <li>OutboxEventPublisher - Periodic publishing scheduler (when curve.outbox.publisher-enabled=true)</li>
 * </ul>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "curve.outbox.enabled", havingValue = "true")
@EnableConfigurationProperties(CurveProperties.class)
@EnableScheduling
public class CurveOutboxAutoConfiguration {

    @Bean
    public OutboxSchemaInitializer outboxSchemaInitializer(DataSource dataSource, CurveProperties properties) {
        return new OutboxSchemaInitializer(dataSource, properties.getOutbox().getInitializeSchema());
    }

    @Bean
    public OutboxEventSaver outboxEventSaver(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        return new OutboxEventSaver(outboxEventRepository, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "curve.outbox.publisher-enabled", havingValue = "true", matchIfMissing = true)
    public OutboxEventPublisher outboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            CurveProperties properties
    ) {
        CurveProperties.Outbox outboxConfig = properties.getOutbox();
        String topic = properties.getKafka().getTopic();

        log.info("Registering OutboxEventPublisher: " +
                        "pollIntervalMs={}, batchSize={}, maxRetries={}, sendTimeoutSeconds={}, topic={}, " +
                        "cleanupEnabled={}, retentionDays={}, dynamicBatching={}, circuitBreaker={}",
                outboxConfig.getPollIntervalMs(),
                outboxConfig.getBatchSize(),
                outboxConfig.getMaxRetries(),
                outboxConfig.getSendTimeoutSeconds(),
                topic,
                outboxConfig.isCleanupEnabled(),
                outboxConfig.getRetentionDays(),
                outboxConfig.isDynamicBatchingEnabled(),
                outboxConfig.isCircuitBreakerEnabled()
        );

        return new OutboxEventPublisher(
                outboxRepository,
                kafkaTemplate,
                topic,
                outboxConfig.getBatchSize(),
                outboxConfig.getMaxRetries(),
                outboxConfig.getSendTimeoutSeconds(),
                outboxConfig.isCleanupEnabled(),
                outboxConfig.getRetentionDays(),
                outboxConfig.isDynamicBatchingEnabled(),
                outboxConfig.isCircuitBreakerEnabled()
        );
    }

    /**
     * Configuration activated when JPA is on the classpath.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
    @AutoConfigurationPackage(basePackageClasses = OutboxEventJpaEntity.class)
    @Import(OutboxJpaRepositoryConfig.class)
    static class JpaOutboxConfiguration {
        // OutboxEventRepository bean is registered in OutboxJpaRepositoryConfig
    }

    /**
     * JDBC configuration activated when JPA is not available.
     */
    @Bean
    @ConditionalOnMissingClass("org.springframework.data.jpa.repository.JpaRepository")
    @ConditionalOnMissingBean(OutboxEventRepository.class)
    public OutboxEventRepository jdbcOutboxEventRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        log.info("Registering OutboxEventRepository (JDBC implementation)");
        return new JdbcOutboxEventRepository(jdbcTemplate, dataSource);
    }
}
