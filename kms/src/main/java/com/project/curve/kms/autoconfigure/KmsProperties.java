package com.project.curve.kms.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for KMS integration.
 */
@Data
@ConfigurationProperties(prefix = "curve.pii.kms")
public class KmsProperties {

    /**
     * Whether to enable KMS for PII encryption keys (default: false).
     */
    private boolean enabled = false;

    /**
     * KMS type: "aws" or "vault".
     */
    private String type;

    /**
     * AWS KMS configuration.
     */
    private final Aws aws = new Aws();

    /**
     * HashiCorp Vault configuration.
     */
    private final Vault vault = new Vault();

    @Data
    public static class Aws {
        /**
         * AWS region for KMS client (e.g., us-east-1).
         * If not set, uses SDK default region resolution.
         */
        private String region;

        /**
         * Custom KMS endpoint (for LocalStack or VPC endpoints).
         */
        private String endpoint;

        /**
         * Default KMS key ARN or alias.
         */
        private String defaultKeyArn;

        /**
         * DEK cache TTL in milliseconds (default: 300000 = 5 minutes).
         */
        private long dekCacheTtlMs = 300_000L;

        /**
         * Maximum number of cached DEKs (default: 100).
         */
        private int dekCacheMaxSize = 100;
    }

    @Data
    public static class Vault {
        /**
         * Vault secret engine mount path (default: secret).
         */
        private String mountPath = "secret";

        /**
         * Default key path in Vault.
         */
        private String defaultKeyPath;
    }
}
