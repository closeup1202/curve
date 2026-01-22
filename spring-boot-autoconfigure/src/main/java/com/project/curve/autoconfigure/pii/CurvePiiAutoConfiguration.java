package com.project.curve.autoconfigure.pii;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.spring.pii.crypto.DefaultPiiCryptoProvider;
import com.project.curve.spring.pii.crypto.PiiCryptoProvider;
import com.project.curve.spring.pii.jackson.PiiModule;
import com.project.curve.spring.pii.mask.*;
import com.project.curve.spring.pii.processor.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.NonNull;

import java.util.List;

/**
 * PII 처리 자동 설정.
 *
 * @PiiField 어노테이션이 붙은 필드를 Jackson 직렬화 시 자동으로 마스킹/암호화/해싱 처리한다.
 */
@Slf4j
@AutoConfiguration
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
            log.error("============================================================");
            log.error("PII 암호화 키가 설정되지 않았습니다!");
            log.error("@PiiField(strategy = PiiStrategy.ENCRYPT) 사용 시 예외가 발생합니다.");
            log.error("");
            log.error("[필수 설정 방법]");
            log.error("  1. 환경변수 설정 (권장):");
            log.error("     export PII_ENCRYPTION_KEY=your-base64-encoded-32-byte-key");
            log.error("");
            log.error("  2. application.yml 설정:");
            log.error("     curve:");
            log.error("       pii:");
            log.error("         crypto:");
            log.error("           default-key: ${{PII_ENCRYPTION_KEY}}");
            log.error("");
            log.error("[키 생성 방법]");
            log.error("  openssl rand -base64 32");
            log.error("============================================================");
        }

        if (!saltConfigured) {
            log.warn("PII 해싱 솔트가 설정되지 않았습니다. 솔트 없이 해싱됩니다.");
            log.warn("보안 강화를 위해 환경변수 PII_HASH_SALT 또는 curve.pii.crypto.salt 설정을 권장합니다.");
        }

        log.info("PII 암호화 제공자 초기화 - 암호화: {}, 솔트: {}",
                keyConfigured ? "활성화" : "비활성화",
                saltConfigured ? "설정됨" : "미설정");

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
     * ObjectMapper에 PiiModule을 자동 등록하는 BeanPostProcessor
     */
    @Bean
    public BeanPostProcessor piiModuleRegistrar(PiiModule piiModule) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
                if (bean instanceof ObjectMapper objectMapper) {
                    objectMapper.registerModule(piiModule);
                    log.info("PII Module 등록 완료 - @PiiField 어노테이션 자동 처리 활성화");
                }
                return bean;
            }
        };
    }
}
