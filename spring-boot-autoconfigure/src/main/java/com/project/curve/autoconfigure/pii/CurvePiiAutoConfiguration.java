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
        String defaultKey = crypto.getDefaultKey();
        String salt = crypto.getSalt();

        boolean keyConfigured = defaultKey != null && !defaultKey.isBlank();
        boolean saltConfigured = salt != null && !salt.isBlank();

        if (!keyConfigured) {
            log.warn("============================================================");
            log.warn("PII 암호화 키가 설정되지 않았습니다!");
            log.warn("@PiiField(strategy = PiiStrategy.ENCRYPT) 사용 시 예외가 발생합니다.");
            log.warn("설정 방법: curve.pii.crypto.default-key 또는 환경변수 PII_ENCRYPTION_KEY");
            log.warn("============================================================");
        }

        if (!saltConfigured) {
            log.info("PII 해싱 솔트가 설정되지 않았습니다. 솔트 없이 해싱됩니다. " +
                    "보안 강화를 위해 curve.pii.crypto.salt 설정을 권장합니다.");
        }

        log.debug("PII 암호화 제공자 초기화 - 암호화: {}, 솔트: {}",
                keyConfigured ? "활성화" : "비활성화",
                saltConfigured ? "설정됨" : "미설정");

        return new DefaultPiiCryptoProvider(defaultKey, salt);
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
