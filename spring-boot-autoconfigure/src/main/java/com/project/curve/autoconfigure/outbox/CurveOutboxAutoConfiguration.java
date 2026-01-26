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
 * curve.outbox.enabled=true일 때만 활성화됩니다.
 *
 * <h3>활성화 조건</h3>
 * <ul>
 *   <li>curve.outbox.enabled=true</li>
 *   <li>KafkaTemplate 빈이 존재</li>
 * </ul>
 *
 * <h3>등록되는 빈</h3>
 * <ul>
 *   <li>OutboxEventRepository (JPA 또는 JDBC 구현체)</li>
 *   <li>OutboxEventPublisher - 주기적 발행 스케줄러 (curve.outbox.publisher-enabled=true일 때)</li>
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
                        "pollIntervalMs={}, batchSize={}, maxRetries={}, sendTimeoutSeconds={}, topic={}, cleanupEnabled={}, retentionDays={}",
                outboxConfig.getPollIntervalMs(),
                outboxConfig.getBatchSize(),
                outboxConfig.getMaxRetries(),
                outboxConfig.getSendTimeoutSeconds(),
                topic,
                outboxConfig.isCleanupEnabled(),
                outboxConfig.getRetentionDays()
        );

        return new OutboxEventPublisher(
                outboxRepository,
                kafkaTemplate,
                topic,
                outboxConfig.getBatchSize(),
                outboxConfig.getMaxRetries(),
                outboxConfig.getSendTimeoutSeconds(),
                outboxConfig.isCleanupEnabled(),
                outboxConfig.getRetentionDays()
        );
    }

    /**
     * JPA가 클래스패스에 있을 때 활성화되는 설정.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
    @AutoConfigurationPackage(basePackageClasses = OutboxEventJpaEntity.class)
    @Import(OutboxJpaRepositoryConfig.class)
    static class JpaOutboxConfiguration {
        // OutboxJpaRepositoryConfig에서 OutboxEventRepository 빈을 등록함
    }

    /**
     * JPA가 없을 때 활성화되는 JDBC 설정.
     */
    @Bean
    @ConditionalOnMissingClass("org.springframework.data.jpa.repository.JpaRepository")
    @ConditionalOnMissingBean(OutboxEventRepository.class)
    public OutboxEventRepository jdbcOutboxEventRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        log.info("Registering OutboxEventRepository (JDBC implementation)");
        return new JdbcOutboxEventRepository(jdbcTemplate, dataSource);
    }
}
