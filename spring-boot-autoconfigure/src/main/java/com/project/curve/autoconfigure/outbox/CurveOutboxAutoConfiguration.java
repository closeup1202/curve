package com.project.curve.autoconfigure.outbox;

import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.spring.outbox.config.OutboxJpaRepositoryConfig;
import com.project.curve.spring.outbox.persistence.OutboxEventJpaEntity;
import com.project.curve.spring.outbox.publisher.OutboxEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
 *   <li>Spring Data JPA가 클래스패스에 존재</li>
 *   <li>KafkaTemplate 빈이 존재</li>
 * </ul>
 *
 * <h3>등록되는 빈</h3>
 * <ul>
 *   <li>OutboxEventJpaRepository - Spring Data JPA 리포지토리</li>
 *   <li>JpaOutboxEventRepositoryAdapter - 포트 구현체</li>
 *   <li>OutboxEventPublisher - 주기적 발행 스케줄러</li>
 * </ul>
 *
 * <h3>설정 예시</h3>
 * <pre>
 * curve:
 *   outbox:
 *     enabled: true
 *     poll-interval-ms: 1000
 *     batch-size: 100
 *     max-retries: 3
 * </pre>
 *
 * @see OutboxEventPublisher
 * @see OutboxEventRepository
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = {
        "jakarta.persistence.Entity",
        "org.springframework.data.jpa.repository.JpaRepository"
})
@ConditionalOnProperty(name = "curve.outbox.enabled", havingValue = "true")
@EnableConfigurationProperties(CurveProperties.class)
@AutoConfigurationPackage(basePackageClasses = OutboxEventJpaEntity.class)
@Import(OutboxJpaRepositoryConfig.class)
@EnableScheduling
public class CurveOutboxAutoConfiguration {

    @Bean
    public OutboxSchemaInitializer outboxSchemaInitializer(DataSource dataSource, CurveProperties properties) {
        return new OutboxSchemaInitializer(dataSource, properties.getOutbox().getInitializeSchema());
    }

    @Bean
    public OutboxEventPublisher outboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            CurveProperties properties
    ) {
        CurveProperties.Outbox outboxConfig = properties.getOutbox();
        String topic = properties.getKafka().getTopic();

        log.info("Registering OutboxEventPublisher: " +
                        "pollIntervalMs={}, batchSize={}, maxRetries={}, topic={}",
                outboxConfig.getPollIntervalMs(),
                outboxConfig.getBatchSize(),
                outboxConfig.getMaxRetries(),
                topic
        );

        return new OutboxEventPublisher(
                outboxRepository,
                kafkaTemplate,
                topic,
                outboxConfig.getBatchSize(),
                outboxConfig.getMaxRetries()
        );
    }
}
