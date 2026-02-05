package com.project.curve.kms.autoconfigure;

import com.project.curve.core.key.KeyProvider;
import com.project.curve.kms.provider.AwsKmsProvider;
import com.project.curve.kms.provider.VaultKeyProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * Auto-configuration for KMS-based KeyProvider.
 * <p>
 * Activates when {@code curve.pii.kms.enabled=true} and creates the appropriate
 * KeyProvider bean based on the configured type (aws or vault).
 */
@AutoConfiguration(beforeName = "com.project.curve.autoconfigure.pii.CurvePiiAutoConfiguration")
@ConditionalOnProperty(prefix = "curve.pii.kms", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(KmsProperties.class)
public class CurveKmsAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KmsClient.class)
    @ConditionalOnProperty(prefix = "curve.pii.kms", name = "type", havingValue = "aws")
    static class AwsKmsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public KmsClient kmsClient(KmsProperties properties) {
            var builder = KmsClient.builder();
            if (properties.getAws().getRegion() != null && !properties.getAws().getRegion().isBlank()) {
                builder.region(software.amazon.awssdk.regions.Region.of(properties.getAws().getRegion()));
            }
            if (properties.getAws().getEndpoint() != null && !properties.getAws().getEndpoint().isBlank()) {
                builder.endpointOverride(java.net.URI.create(properties.getAws().getEndpoint()));
            }
            return builder.build();
        }

        @Bean
        @ConditionalOnMissingBean(KeyProvider.class)
        public KeyProvider awsKmsKeyProvider(KmsClient kmsClient, KmsProperties properties) {
            return new AwsKmsProvider(
                    kmsClient,
                    properties.getAws().getDekCacheTtlMs(),
                    properties.getAws().getDekCacheMaxSize()
            );
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.vault.core.VaultTemplate")
    @ConditionalOnProperty(prefix = "curve.pii.kms", name = "type", havingValue = "vault")
    static class VaultConfiguration {

        @Bean
        @ConditionalOnMissingBean(KeyProvider.class)
        @ConditionalOnBean(type = "org.springframework.vault.core.VaultTemplate")
        public KeyProvider vaultKeyProvider(
                org.springframework.vault.core.VaultTemplate vaultTemplate,
                KmsProperties properties) {
            return new VaultKeyProvider(vaultTemplate, properties.getVault().getMountPath());
        }
    }
}
