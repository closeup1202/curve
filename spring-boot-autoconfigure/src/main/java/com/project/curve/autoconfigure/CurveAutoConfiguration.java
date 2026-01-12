package com.project.curve.autoconfigure;

import com.project.curve.autoconfigure.kafka.CurveEventProducerAutoConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(CurveProperties.class)
@ConditionalOnProperty(
        name = "curve.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableAsync
@Import({
        CurveEventProducerAutoConfiguration.class,
})
public class CurveAutoConfiguration {

    @PostConstruct
    public void startUp() {
        log.info("👀 Curve가 자동으로 활성화되었습니다!");
        log.info("📋 설정 변경: curve.enabled=false로 비활성화 가능");
    }
}
