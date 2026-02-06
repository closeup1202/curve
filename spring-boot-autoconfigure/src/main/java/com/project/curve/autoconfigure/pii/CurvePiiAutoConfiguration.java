package com.project.curve.autoconfigure.pii;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.key.KeyProvider;
import com.project.curve.spring.pii.crypto.DefaultPiiCryptoProvider;
import com.project.curve.spring.pii.crypto.KmsPiiCryptoProvider;
import com.project.curve.spring.pii.crypto.PiiCryptoProvider;
import com.project.curve.spring.pii.jackson.PiiModule;
import com.project.curve.spring.pii.mask.*;
import com.project.curve.spring.pii.processor.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * PII (Personally Identifiable Information) processing auto-configuration.
 *
 * Automatically masks/encrypts/hashes fields annotated with @PiiField during Jackson serialization.
 */
@Slf4j
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(ObjectMapper.class)
@ConditionalOnProperty(prefix = "curve.pii", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CurvePiiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PiiCryptoProvider piiCryptoProvider(
            CurveProperties properties,
            ObjectProvider<KeyProvider> keyProvider,
            Environment environment
    ) {
        CurveProperties.Pii.Crypto crypto = properties.getPii().getCrypto();
        String salt = resolveSalt(crypto.getSalt());

        // Check if KMS is enabled via property
        boolean kmsEnabled = environment.getProperty("curve.pii.kms.enabled", Boolean.class, false);

        // Check if KMS is enabled and KeyProvider bean is available
        if (kmsEnabled && keyProvider.getIfAvailable() != null) {
            log.info("KMS mode enabled for PII encryption.");
            return new KmsPiiCryptoProvider(keyProvider.getIfAvailable(), salt);
        }

        // Fallback to local key mode
        String defaultKey = resolveEncryptionKey(crypto.getDefaultKey());
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

        log.debug("PII crypto provider initialized (Local Mode) - encryption: {}, salt: {}",
                keyConfigured ? "enabled" : "disabled",
                saltConfigured ? "configured" : "not configured");

        return new DefaultPiiCryptoProvider(defaultKey, salt);
    }

    /**
     * Resolves the encryption key from environment variables or configuration.
     * Prioritizes the PII_ENCRYPTION_KEY environment variable if set.
     */
    private String resolveEncryptionKey(String configuredKey) {
        // Environment variable takes priority
        String envKey = System.getenv("PII_ENCRYPTION_KEY");
        if (envKey != null && !envKey.isBlank()) {
            log.debug("PII encryption key loaded from PII_ENCRYPTION_KEY environment variable.");
            return envKey;
        }
        return configuredKey;
    }

    /**
     * Resolves the salt from environment variables or configuration.
     * Prioritizes the PII_HASH_SALT environment variable if set.
     */
    private String resolveSalt(String configuredSalt) {
        // Environment variable takes priority
        String envSalt = System.getenv("PII_HASH_SALT");
        if (envSalt != null && !envSalt.isBlank()) {
            log.debug("PII hashing salt loaded from PII_HASH_SALT environment variable.");
            return envSalt;
        }
        return configuredSalt;
    }

    // Masker beans
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

    // Processor beans
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
     * Customizer that automatically registers PiiModule to ObjectMapper.
     * Integrates with Spring Boot's Jackson auto-configuration to prevent BeanPostProcessor warnings.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer piiModuleCustomizer(PiiModule piiModule) {
        return builder -> {
            builder.modules(piiModule);
            log.debug("PII Module registered for @PiiField annotation processing");
        };
    }
}
