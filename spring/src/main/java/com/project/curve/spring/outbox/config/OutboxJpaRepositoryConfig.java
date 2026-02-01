package com.project.curve.spring.outbox.config;

import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.spring.outbox.persistence.jpa.adapter.JpaOutboxEventRepositoryAdapter;
import com.project.curve.spring.outbox.persistence.jpa.repository.OutboxEventJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Outbox JPA Repository 활성화 및 어댑터 빈 등록을 위한 설정.
 * <p>
 * Spring Data JPA 리포지토리 프록시 생성을 처리하기 위해
 * {@link OutboxEventJpaRepository}와 동일한 모듈에 위치합니다.
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@EnableJpaRepositories(basePackageClasses = OutboxEventJpaRepository.class)
public class OutboxJpaRepositoryConfig {

    @Bean
    public OutboxEventRepository outboxEventRepository(OutboxEventJpaRepository jpaRepository) {
        log.info("Registering OutboxEventRepository (JPA implementation)");
        return new JpaOutboxEventRepositoryAdapter(jpaRepository);
    }
}
