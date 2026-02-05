package com.project.curve.kms.provider;

import com.project.curve.core.key.EnvelopeDataKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyRequest;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AwsKmsProvider Test")
@ExtendWith(MockitoExtension.class)
class AwsKmsProviderTest {

    @Mock
    KmsClient kmsClient;

    private static final String KEY_ID = "arn:aws:kms:us-east-1:123456789012:key/test-key-id";

    private byte[] plaintextKey;
    private byte[] encryptedKey;

    @BeforeEach
    void setUp() {
        plaintextKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            plaintextKey[i] = (byte) i;
        }
        encryptedKey = "encrypted-key-blob".getBytes();
    }

    private void stubGenerateDataKey() {
        GenerateDataKeyResponse response = GenerateDataKeyResponse.builder()
                .plaintext(SdkBytes.fromByteArray(plaintextKey))
                .ciphertextBlob(SdkBytes.fromByteArray(encryptedKey))
                .build();
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(response);
    }

    private void stubDecrypt() {
        DecryptResponse decryptResponse = DecryptResponse.builder()
                .plaintext(SdkBytes.fromByteArray(plaintextKey))
                .build();
        when(kmsClient.decrypt(any(DecryptRequest.class))).thenReturn(decryptResponse);
    }

    @Test
    @DisplayName("generateDataKey calls KMS and returns EnvelopeDataKey with both plaintext and encrypted keys")
    void generateDataKey_callsKmsAndReturnsEnvelopeDataKey() {
        // given
        stubGenerateDataKey();
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, 300_000L, 100);

        // when
        EnvelopeDataKey dataKey = provider.generateDataKey(KEY_ID);

        // then
        assertThat(dataKey).isNotNull();
        assertThat(dataKey.plaintextKey()).isEqualTo(plaintextKey);
        assertThat(dataKey.encryptedKey()).isEqualTo(encryptedKey);
        verify(kmsClient, times(1)).generateDataKey(any(GenerateDataKeyRequest.class));
    }

    @Test
    @DisplayName("generateDataKey caches result - second call does not hit KMS")
    void generateDataKey_cachesResult_secondCallDoesNotHitKms() {
        // given
        stubGenerateDataKey();
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, 300_000L, 100);

        // when
        EnvelopeDataKey first = provider.generateDataKey(KEY_ID);
        EnvelopeDataKey second = provider.generateDataKey(KEY_ID);

        // then
        assertThat(first.plaintextKey()).isEqualTo(second.plaintextKey());
        assertThat(first.encryptedKey()).isEqualTo(second.encryptedKey());
        verify(kmsClient, times(1)).generateDataKey(any(GenerateDataKeyRequest.class));
    }

    @Test
    @DisplayName("generateDataKey cache expires after TTL - next call hits KMS again")
    void generateDataKey_cacheExpiresAfterTtl() throws InterruptedException {
        // given
        stubGenerateDataKey();
        long shortTtl = 100L;
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, shortTtl, 100);

        // when
        provider.generateDataKey(KEY_ID);
        Thread.sleep(150L);
        provider.generateDataKey(KEY_ID);

        // then
        verify(kmsClient, times(2)).generateDataKey(any(GenerateDataKeyRequest.class));
    }

    @Test
    @DisplayName("decryptDataKey calls KMS and returns plaintext key")
    void decryptDataKey_callsKmsAndReturnsPlaintextKey() {
        // given
        stubDecrypt();
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, 300_000L, 100);

        // when
        byte[] result = provider.decryptDataKey(encryptedKey);

        // then
        assertThat(result).isEqualTo(plaintextKey);
        verify(kmsClient, times(1)).decrypt(any(DecryptRequest.class));
    }

    @Test
    @DisplayName("decryptDataKey caches result - second call does not hit KMS")
    void decryptDataKey_cachesResult_secondCallDoesNotHitKms() {
        // given
        stubDecrypt();
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, 300_000L, 100);

        // when
        byte[] first = provider.decryptDataKey(encryptedKey);
        byte[] second = provider.decryptDataKey(encryptedKey);

        // then
        assertThat(first).isEqualTo(second);
        verify(kmsClient, times(1)).decrypt(any(DecryptRequest.class));
    }

    @Test
    @DisplayName("invalidateAll clears both caches - next calls hit KMS again")
    void invalidateAll_clearsCaches_nextCallsHitKms() {
        // given
        stubGenerateDataKey();
        stubDecrypt();
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, 300_000L, 100);

        provider.generateDataKey(KEY_ID);
        provider.decryptDataKey(encryptedKey);

        // when
        provider.invalidateAll();
        provider.generateDataKey(KEY_ID);
        provider.decryptDataKey(encryptedKey);

        // then
        verify(kmsClient, times(2)).generateDataKey(any(GenerateDataKeyRequest.class));
        verify(kmsClient, times(2)).decrypt(any(DecryptRequest.class));
    }

    @Test
    @DisplayName("getDataKey returns Base64-encoded plaintext key")
    void getDataKey_returnsBase64EncodedPlaintextKey() {
        // given
        stubGenerateDataKey();
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, 300_000L, 100);

        // when
        String result = provider.getDataKey(KEY_ID);

        // then
        String expected = Base64.getEncoder().encodeToString(plaintextKey);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("supportsEnvelopeEncryption returns true")
    void supportsEnvelopeEncryption_returnsTrue() {
        // given
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, 300_000L, 100);

        // when & then
        assertThat(provider.supportsEnvelopeEncryption()).isTrue();
    }

    @Test
    @DisplayName("Cache evicts entries when max size is reached")
    void generateDataKey_evictsWhenCacheMaxSizeReached() throws InterruptedException {
        // given
        int cacheMaxSize = 1; // Force eviction on every new key
        AwsKmsProvider provider = new AwsKmsProvider(kmsClient, 300_000L, cacheMaxSize);

        GenerateDataKeyResponse response = GenerateDataKeyResponse.builder()
                .plaintext(SdkBytes.fromByteArray(plaintextKey))
                .ciphertextBlob(SdkBytes.fromByteArray(encryptedKey))
                .build();
        when(kmsClient.generateDataKey(any(GenerateDataKeyRequest.class))).thenReturn(response);

        // when
        provider.generateDataKey("key-0"); // 1st call (Miss)
        provider.generateDataKey("key-1"); // 2nd call (Miss, key-0 evicted)
        
        // Caffeine eviction is async, give it a tiny moment if needed, 
        // but with size 1, the next put usually forces eviction logic.
        // However, to be safe and deterministic, we just check that we called KMS twice so far.
        
        provider.generateDataKey("key-0"); // 3rd call (Miss because key-0 was evicted)

        // then
        verify(kmsClient, times(3)).generateDataKey(any(GenerateDataKeyRequest.class));
    }
}
