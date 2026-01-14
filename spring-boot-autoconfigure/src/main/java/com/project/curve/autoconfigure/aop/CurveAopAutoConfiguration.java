package com.project.curve.autoconfigure.aop;

import com.project.curve.core.port.EventProducer;
import com.project.curve.spring.aop.AuditableAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Slf4j
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@ConditionalOnProperty(
        name = "curve.aop.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CurveAopAutoConfiguration {

    @Bean
    public AuditableAspect auditableAspect(EventProducer eventProducer) {
        log.info("Initializing AuditableAspect for @Auditable annotation support");
        return new AuditableAspect(eventProducer);
    }
}
