package com.project.curve.kms.provider;

import com.project.curve.core.key.EnvelopeDataKey;
import com.project.curve.core.key.KeyProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AWS KMS-based KeyProvider with envelope encryption support.
 * <p>
 * Uses KMS {@code GenerateDataKey} to create Data Encryption Keys (DEK)
 * and stores the encrypted DEK alongside ciphertext for later decryption.
 * <p>
 * Includes TTL-based caching to minimize KMS API calls.
 */
public class AwsKmsProvider implements KeyProvider {

    private final KmsClient kmsClient;
    private final long cacheTtlMillis;
    private final int cacheMaxSize;

    // Cache for generated DEKs (keyId -> CachedDataKey)
    private final Map<String, CachedDataKey> generateCache = new ConcurrentHashMap<>();
    // Cache for decrypted DEKs (Base64(encryptedDek) -> CachedPlaintextKey)
    private final Map<String, CachedPlaintextKey> decryptCache = new ConcurrentHashMap<>();

    /**
     * @param kmsClient      AWS KMS client
     * @param cacheTtlMillis TTL for cached DEKs in milliseconds (default: 300000 = 5 minutes)
     * @param cacheMaxSize   Maximum number of cached entries (default: 100)
     */
    public AwsKmsProvider(KmsClient kmsClient, long cacheTtlMillis, int cacheMaxSize) {
        this.kmsClient = kmsClient;
        this.cacheTtlMillis = cacheTtlMillis;
        this.cacheMaxSize = cacheMaxSize;
    }

    public AwsKmsProvider(KmsClient kmsClient) {
        this(kmsClient, 300_000L, 100);
    }

    @Override
    public boolean supportsEnvelopeEncryption() {
        return true;
    }

    @Override
    public String getDataKey(String keyId) {
        EnvelopeDataKey dataKey = generateDataKey(keyId);
        return Base64.getEncoder().encodeToString(dataKey.plaintextKey());
    }

    @Override
    public EnvelopeDataKey generateDataKey(String keyId) {
        CachedDataKey cached = generateCache.get(keyId);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return new EnvelopeDataKey(cached.plaintextKey(), cached.encryptedKey());
        }

        GenerateDataKeyRequest request = GenerateDataKeyRequest.builder()
                .keyId(keyId)
                .keySpec(DataKeySpec.AES_256)
                .build();

        GenerateDataKeyResponse response = kmsClient.generateDataKey(request);

        byte[] plaintextKey = response.plaintext().asByteArray();
        byte[] encryptedKey = response.ciphertextBlob().asByteArray();

        evictIfNeeded(generateCache);
        generateCache.put(keyId, new CachedDataKey(plaintextKey, encryptedKey, System.currentTimeMillis()));

        return new EnvelopeDataKey(plaintextKey, encryptedKey);
    }

    @Override
    public byte[] decryptDataKey(byte[] encryptedDataKey) {
        String cacheKey = Base64.getEncoder().encodeToString(encryptedDataKey);

        CachedPlaintextKey cached = decryptCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            return cached.plaintextKey();
        }

        DecryptRequest request = DecryptRequest.builder()
                .ciphertextBlob(SdkBytes.fromByteArray(encryptedDataKey))
                .build();

        DecryptResponse response = kmsClient.decrypt(request);
        byte[] plaintextKey = response.plaintext().asByteArray();

        evictIfNeeded(decryptCache);
        decryptCache.put(cacheKey, new CachedPlaintextKey(plaintextKey, System.currentTimeMillis()));

        return plaintextKey;
    }

    /**
     * Clears all cached keys. Useful for key rotation events.
     */
    public void invalidateAll() {
        generateCache.clear();
        decryptCache.clear();
    }

    private <V> void evictIfNeeded(Map<String, V> cache) {
        if (cache.size() >= cacheMaxSize) {
            // Remove expired entries first
            cache.entrySet().removeIf(entry -> {
                if (entry.getValue() instanceof CachedDataKey c) {
                    return c.isExpired(cacheTtlMillis);
                } else if (entry.getValue() instanceof CachedPlaintextKey c) {
                    return c.isExpired(cacheTtlMillis);
                }
                return false;
            });
            // If still full, remove the oldest entry by timestamp
            if (cache.size() >= cacheMaxSize) {
                String oldestKey = null;
                long oldestTime = Long.MAX_VALUE;
                for (Map.Entry<String, V> entry : cache.entrySet()) {
                    long createdAt;
                    if (entry.getValue() instanceof CachedDataKey c) {
                        createdAt = c.createdAt();
                    } else if (entry.getValue() instanceof CachedPlaintextKey c) {
                        createdAt = c.createdAt();
                    } else {
                        continue;
                    }
                    if (createdAt < oldestTime) {
                        oldestTime = createdAt;
                        oldestKey = entry.getKey();
                    }
                }
                if (oldestKey != null) {
                    cache.remove(oldestKey);
                }
            }
        }
    }

    record CachedDataKey(byte[] plaintextKey, byte[] encryptedKey, long createdAt) {
        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }
    }

    record CachedPlaintextKey(byte[] plaintextKey, long createdAt) {
        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }
    }
}
