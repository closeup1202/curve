package com.project.curve.autoconfigure.pii;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.spring.pii.crypto.DefaultPiiCryptoProvider;
import com.project.curve.spring.pii.crypto.PiiCryptoProvider;
import com.project.curve.spring.pii.jackson.PiiModule;
import com.project.curve.spring.pii.mask.*;
import com.project.curve.spring.pii.processor.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * PII 처리 자동 설정.
 *
 * @PiiField 어노테이션이 붙은 필드를 Jackson 직렬화 시 자동으로 마스킹/암호화/해싱 처리한다.
 */
@Slf4j
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnProperty(prefix = "curve.pii", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CurvePiiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PiiCryptoProvider piiCryptoProvider(CurveProperties properties) {
        CurveProperties.Pii.Crypto crypto = properties.getPii().getCrypto();
        String defaultKey = resolveEncryptionKey(crypto.getDefaultKey());
        String salt = resolveSalt(crypto.getSalt());

        boolean keyConfigured = defaultKey != null && !defaultKey.isBlank();
        boolean saltConfigured = salt != null && !salt.isBlank();

        if (!keyConfigured) {
            log.warn("PII encryption key not configured. @PiiField(strategy=ENCRYPT) will throw exception. " +
                    "Set PII_ENCRYPTION_KEY env var or curve.pii.crypto.default-key property.");
        }

        if (!saltConfigured) {
            log.debug("PII hash salt not configured. Hashing will proceed without salt. " +
                    "Set PII_HASH_SALT env var or curve.pii.crypto.salt for enhanced security.");
        }

        log.debug("PII crypto provider initialized - encryption: {}, salt: {}",
                keyConfigured ? "enabled" : "disabled",
                saltConfigured ? "configured" : "not configured");

        return new DefaultPiiCryptoProvider(defaultKey, salt);
    }

    /**
     * 암호화 키를 환경변수 또는 설정값에서 해석합니다.
     * 환경변수 PII_ENCRYPTION_KEY가 설정되어 있으면 우선 사용합니다.
     */
    private String resolveEncryptionKey(String configuredKey) {
        // 환경변수 우선
        String envKey = System.getenv("PII_ENCRYPTION_KEY");
        if (envKey != null && !envKey.isBlank()) {
            log.debug("PII 암호화 키를 환경변수 PII_ENCRYPTION_KEY에서 로드했습니다.");
            return envKey;
        }
        return configuredKey;
    }

    /**
     * 솔트를 환경변수 또는 설정값에서 해석합니다.
     * 환경변수 PII_HASH_SALT가 설정되어 있으면 우선 사용합니다.
     */
    private String resolveSalt(String configuredSalt) {
        // 환경변수 우선
        String envSalt = System.getenv("PII_HASH_SALT");
        if (envSalt != null && !envSalt.isBlank()) {
            log.debug("PII 해싱 솔트를 환경변수 PII_HASH_SALT에서 로드했습니다.");
            return envSalt;
        }
        return configuredSalt;
    }

    // 마스커 빈들
    @Bean
    @ConditionalOnMissingBean(name = "emailMasker")
    public PiiMasker emailMasker() {
        return new EmailMasker();
    }

    @Bean
    @ConditionalOnMissingBean(name = "phoneMasker")
    public PiiMasker phoneMasker() {
        return new PhoneMasker();
    }

    @Bean
    @ConditionalOnMissingBean(name = "nameMasker")
    public PiiMasker nameMasker() {
        return new NameMasker();
    }

    @Bean
    @ConditionalOnMissingBean(name = "defaultMasker")
    public PiiMasker defaultMasker() {
        return new DefaultMasker();
    }

    // 프로세서 빈들
    @Bean
    @ConditionalOnMissingBean
    public MaskingPiiProcessor maskingPiiProcessor(List<PiiMasker> maskers) {
        return new MaskingPiiProcessor(maskers);
    }

    @Bean
    @ConditionalOnMissingBean
    public EncryptingPiiProcessor encryptingPiiProcessor(PiiCryptoProvider cryptoProvider) {
        return new EncryptingPiiProcessor(cryptoProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public HashingPiiProcessor hashingPiiProcessor(PiiCryptoProvider cryptoProvider) {
        return new HashingPiiProcessor(cryptoProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public PiiProcessorRegistry piiProcessorRegistry(List<PiiProcessor> processors) {
        return new PiiProcessorRegistry(processors);
    }

    @Bean
    @ConditionalOnMissingBean
    public PiiModule piiModule(PiiProcessorRegistry processorRegistry) {
        return new PiiModule(processorRegistry);
    }

    /**
     * ObjectMapper에 PiiModule을 자동 등록하는 Customizer.
     * Spring Boot의 Jackson 자동 설정과 통합되어 BeanPostProcessor 경고를 방지합니다.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer piiModuleCustomizer(PiiModule piiModule) {
        return builder -> {
            builder.modules(piiModule);
            log.debug("PII Module registered for @PiiField annotation processing");
        };
    }
}
