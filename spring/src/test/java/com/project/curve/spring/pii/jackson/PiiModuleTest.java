package com.project.curve.spring.pii.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.spring.pii.annotation.PiiField;
import com.project.curve.spring.pii.crypto.DefaultPiiCryptoProvider;
import com.project.curve.spring.pii.mask.DefaultMasker;
import com.project.curve.spring.pii.mask.EmailMasker;
import com.project.curve.spring.pii.mask.NameMasker;
import com.project.curve.spring.pii.mask.PhoneMasker;
import com.project.curve.spring.pii.processor.*;
import com.project.curve.spring.pii.strategy.PiiStrategy;
import com.project.curve.spring.pii.type.MaskingLevel;
import com.project.curve.spring.pii.type.PiiType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PiiModule Test")
class PiiModuleTest {

    private ObjectMapper objectMapper;
    private PiiProcessorRegistry processorRegistry;
    private String encryptionKey;

    @BeforeEach
    void setUp() {
        // Generate 32-byte encryption key (Base64 encoded)
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) i;
        }
        encryptionKey = Base64.getEncoder().encodeToString(keyBytes);

        // Setup PII processors
        DefaultPiiCryptoProvider cryptoProvider = new DefaultPiiCryptoProvider(
                encryptionKey,
                "test-salt"
        );

        List<com.project.curve.spring.pii.mask.PiiMasker> maskers = List.of(
                new EmailMasker(),
                new PhoneMasker(),
                new NameMasker(),
                new DefaultMasker()
        );

        List<PiiProcessor> processors = List.of(
                new MaskingPiiProcessor(maskers),
                new EncryptingPiiProcessor(cryptoProvider),
                new HashingPiiProcessor(cryptoProvider)
        );

        processorRegistry = new PiiProcessorRegistry(processors);

        // Register PiiModule with ObjectMapper
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new PiiModule(processorRegistry));
    }

    @Nested
    @DisplayName("MASK Strategy")
    class MaskStrategy {

        @Test
        @DisplayName("Should mask email field")
        void serialize_emailWithMask_shouldMaskEmail() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            payload.email = "test@example.com";
            payload.plainField = "plain-value";

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).contains("\"email\":");
            assertThat(json).doesNotContain("test@example.com");
            assertThat(json).contains("*"); // Masked
            assertThat(json).contains("plain-value"); // Plain field unchanged
        }

        @Test
        @DisplayName("Should mask phone field")
        void serialize_phoneWithMask_shouldMaskPhone() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            payload.phone = "010-1234-5678";

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).contains("\"phone\":");
            assertThat(json).doesNotContain("010-1234-5678");
            assertThat(json).contains("*"); // Masked
        }

        @Test
        @DisplayName("Should mask name field")
        void serialize_nameWithMask_shouldMaskName() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            payload.name = "홍길동";

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).contains("\"name\":");
            assertThat(json).doesNotContain("홍길동");
            assertThat(json).contains("*"); // Masked
        }
    }

    @Nested
    @DisplayName("ENCRYPT Strategy")
    class EncryptStrategy {

        @Test
        @DisplayName("Should encrypt sensitive field")
        void serialize_fieldWithEncrypt_shouldEncrypt() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            payload.ssn = "123456-1234567";

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).contains("\"ssn\":");
            assertThat(json).doesNotContain("123456-1234567");
            assertThat(json).contains("ENC("); // Encrypted with ENC() wrapper
        }

        @Test
        @DisplayName("Should handle null encrypted field")
        void serialize_nullEncryptedField_shouldSerializeNull() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            payload.ssn = null;

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).contains("\"ssn\":null");
        }
    }

    @Nested
    @DisplayName("HASH Strategy")
    class HashStrategy {

        @Test
        @DisplayName("Should hash field deterministically")
        void serialize_fieldWithHash_shouldHash() throws JsonProcessingException {
            // Given
            TestPayload payload1 = new TestPayload();
            payload1.accountNumber = "1234567890";

            TestPayload payload2 = new TestPayload();
            payload2.accountNumber = "1234567890";

            // When
            String json1 = objectMapper.writeValueAsString(payload1);
            String json2 = objectMapper.writeValueAsString(payload2);

            // Then
            assertThat(json1).contains("\"accountNumber\":");
            assertThat(json1).doesNotContain("1234567890");
            assertThat(json1).contains("HASH("); // Hashed with HASH() wrapper

            // Same input should produce same hash
            assertThat(json1).isEqualTo(json2);
        }

        @Test
        @DisplayName("Should produce different hashes for different values")
        void serialize_differentValues_shouldProduceDifferentHashes() throws JsonProcessingException {
            // Given
            TestPayload payload1 = new TestPayload();
            payload1.accountNumber = "1234567890";

            TestPayload payload2 = new TestPayload();
            payload2.accountNumber = "0987654321";

            // When
            String json1 = objectMapper.writeValueAsString(payload1);
            String json2 = objectMapper.writeValueAsString(payload2);

            // Then
            assertThat(json1).isNotEqualTo(json2);
        }
    }

    @Nested
    @DisplayName("EXCLUDE Strategy")
    class ExcludeStrategy {

        @Test
        @DisplayName("Should exclude field from serialization")
        void serialize_fieldWithExclude_shouldNotInclude() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            payload.password = "super-secret";
            payload.plainField = "plain-value";

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).doesNotContain("password");
            assertThat(json).doesNotContain("super-secret");
            assertThat(json).contains("plain-value"); // Other fields should be present
        }
    }

    @Nested
    @DisplayName("Mixed Strategies")
    class MixedStrategies {

        @Test
        @DisplayName("Should handle multiple PII strategies in same object")
        void serialize_mixedStrategies_shouldApplyAll() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            payload.email = "test@example.com";
            payload.ssn = "123456-1234567";
            payload.password = "secret";
            payload.accountNumber = "1234567890";
            payload.plainField = "plain";

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).contains("\"email\":"); // Masked
            assertThat(json).contains("*"); // Has masking
            assertThat(json).contains("\"ssn\":"); // Encrypted
            assertThat(json).doesNotContain("password"); // Excluded
            assertThat(json).contains("\"accountNumber\":"); // Hashed
            assertThat(json).contains("\"plainField\":\"plain\""); // Plain
        }
    }

    @Nested
    @DisplayName("Null Handling")
    class NullHandling {

        @Test
        @DisplayName("Should handle null values correctly")
        void serialize_nullValues_shouldSerializeNull() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            // All fields are null

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).contains("\"email\":null");
            assertThat(json).contains("\"phone\":null");
            assertThat(json).contains("\"ssn\":null");
            assertThat(json).doesNotContain("password"); // EXCLUDE strategy omits null too
        }
    }

    @Nested
    @DisplayName("Module Registration")
    class ModuleRegistration {

        @Test
        @DisplayName("Should work without PiiModule registration")
        void serialize_withoutModule_shouldSerializePlain() throws JsonProcessingException {
            // Given
            ObjectMapper plainMapper = new ObjectMapper();
            TestPayload payload = new TestPayload();
            payload.email = "test@example.com";

            // When
            String json = plainMapper.writeValueAsString(payload);

            // Then - Should serialize as-is without processing
            assertThat(json).contains("test@example.com");
        }

        @Test
        @DisplayName("Should process after PiiModule registration")
        void serialize_withModule_shouldProcessPii() throws JsonProcessingException {
            // Given
            TestPayload payload = new TestPayload();
            payload.email = "test@example.com";

            // When
            String json = objectMapper.writeValueAsString(payload); // uses PiiModule

            // Then - Should be masked
            assertThat(json).doesNotContain("test@example.com");
            assertThat(json).contains("*");
        }
    }

    @Nested
    @DisplayName("Nested Objects")
    class NestedObjects {

        @Test
        @DisplayName("Should process PII in nested objects")
        void serialize_nestedObject_shouldProcessPii() throws JsonProcessingException {
            // Given
            NestedPayload payload = new NestedPayload();
            payload.user = new TestPayload();
            payload.user.email = "nested@example.com";
            payload.user.plainField = "plain";

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then
            assertThat(json).contains("\"user\":");
            assertThat(json).doesNotContain("nested@example.com");
            assertThat(json).contains("*"); // Masked
            assertThat(json).contains("plain"); // Plain field preserved
        }
    }

    @Nested
    @DisplayName("Non-String Fields")
    class NonStringFields {

        @Test
        @DisplayName("Should not process non-string fields")
        void serialize_nonStringField_shouldSerializeAsIs() throws JsonProcessingException {
            // Given
            NonStringPayload payload = new NonStringPayload();
            payload.age = 30;
            payload.active = true;

            // When
            String json = objectMapper.writeValueAsString(payload);

            // Then - Non-string fields should not be processed
            assertThat(json).contains("\"age\":30");
            assertThat(json).contains("\"active\":true");
        }
    }

    // Test data classes
    static class TestPayload {
        @PiiField(strategy = PiiStrategy.MASK, type = PiiType.EMAIL, level = MaskingLevel.NORMAL)
        public String email;

        @PiiField(strategy = PiiStrategy.MASK, type = PiiType.PHONE, level = MaskingLevel.NORMAL)
        public String phone;

        @PiiField(strategy = PiiStrategy.MASK, type = PiiType.NAME, level = MaskingLevel.NORMAL)
        public String name;

        @PiiField(strategy = PiiStrategy.ENCRYPT)
        public String ssn;

        @PiiField(strategy = PiiStrategy.HASH)
        public String accountNumber;

        @PiiField(strategy = PiiStrategy.EXCLUDE)
        public String password;

        public String plainField;
    }

    static class NestedPayload {
        public TestPayload user;
    }

    static class NonStringPayload {
        @PiiField(strategy = PiiStrategy.MASK)
        public Integer age;

        @PiiField(strategy = PiiStrategy.MASK)
        public Boolean active;
    }
}
