# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Security
- **PII Hashing**: Changed from raw SHA-256 to HMAC-SHA256 with salt-based keyed hashing for stronger PII protection
- **AES Key Validation**: Encryption key must be exactly 32 bytes (Base64-encoded); keys of incorrect length are rejected at startup with `Arrays.fill` cleanup
- **Health Check**: Replaced `KafkaTemplate.metrics().size()` with `AdminClient.describeCluster()` for real broker connectivity verification (returns `clusterId` and `nodeCount`)
- **MDC Context**: Changed from `MDC.clear()` to restoring previous MDC context to prevent context leakage in shared thread pools

### Changed
- **Thread Safety**: `KafkaEventProducer.initialized` changed from `boolean` to `AtomicBoolean` for thread-safe initialization tracking
- **Conditional Async Executor**: Removed unconditional `@EnableAsync`; async executor bean is now conditional on `curve.async.enabled=true`
- **Cross-Validation**: Added `@AssertTrue` validators for S3 backup (`s3-bucket` required when `s3-enabled=true`) and AVRO serde (`schema-registry-url` required when `type=AVRO`)
- **Documentation**: Comprehensive documentation update across all files to reflect current implementation

---

## [0.0.5] - 2026-02-05

### Added
- **KMS Module**: New `kms` module for external key management service integration
    - `KeyProvider` interface in core with envelope encryption support (`generateDataKey`, `decryptDataKey`, `supportsEnvelopeEncryption`)
    - `EnvelopeDataKey` record for carrying both plaintext and encrypted DEK
    - `KeyProviderException` domain exception for KMS-related errors
- **AWS KMS Provider**: `AwsKmsProvider` with full envelope encryption
    - Generates DEK via KMS `GenerateDataKey` and stores encrypted DEK alongside ciphertext
    - TTL-based dual cache (generate + decrypt) with configurable max size and oldest-entry eviction
    - `invalidateAll()` for key rotation support
- **HashiCorp Vault Provider**: `VaultKeyProvider` for Vault K/V secret engine
    - Fetches pre-existing encryption keys from Vault paths
- **KMS PII Crypto Provider**: `KmsPiiCryptoProvider` supporting both modes
    - Envelope encryption mode (AWS KMS): ciphertext format `Base64([2-byte encDEK len][encDEK][IV + AES-GCM ciphertext])`
    - Static key mode (Vault K/V): fetches key and encrypts locally
- **KMS Auto-Configuration**: `CurveKmsAutoConfiguration` with conditional beans
    - `AwsKmsConfiguration` (`@ConditionalOnClass(KmsClient.class)`, `@ConditionalOnProperty(type=aws)`)
    - `VaultConfiguration` (`@ConditionalOnClass(VaultTemplate.class)`, `@ConditionalOnProperty(type=vault)`)
    - `KmsProperties` with separate `Aws` and `Vault` configuration sections
- **AES Utility**: Extracted `AesUtil` from `DefaultPiiCryptoProvider` for shared AES-256-GCM logic
    - `encryptWithKey`/`decryptWithKey` methods accepting `SecretKey` directly (eliminates Base64 roundtrip)
    - `encryptToBytes`/`decryptFromBytes` for envelope encryption byte packing
- **Comprehensive Tests**: Full test coverage for all new components
    - `AwsKmsProviderTest` (9 tests): envelope encryption, caching, TTL expiry, eviction, invalidation
    - `KmsPiiCryptoProviderTest` (12 tests): static key mode, envelope mode, hash operations
    - `AesUtilTest` (21 tests): round-trip, null safety, Unicode/emoji, key handling
    - `KeyProviderTest`, `KeyProviderExceptionTest`, `EnvelopeDataKeyTest` in core

### Changed
- **deps**: Bump SonarQube Gradle plugin from 4.4.1.3373 to 7.2.2.6593 (#14)
    - Resolves deprecation warning for compile task dependency
    - Adds Gradle 9 and configuration-cache support
- **deps**: Unify AWS SDK BOM to 2.41.21 across all modules (#2)
    - Fixes version mismatch between `kafka`, `kms`, and `spring-boot-autoconfigure` modules
    - Includes 20+ months of security patches and performance improvements
- **deps**: Bump Confluent Kafka Avro Serializer from 7.5.0 to 8.1.1 (#11)
- **deps**: Bump TestContainers from 1.20.4 to 1.21.4 (#6)
- **deps**: Bump Release Plugin from 3.0.2 to 3.1.0 (#10)
- **deps**: Bump Spring Cloud from 2023.0.0 to 2024.0.1 (Spring Boot 3.5.x compatibility)
- **DefaultPiiCryptoProvider**: Refactored to use `AesUtil` with direct `SecretKey` methods, eliminating `SecretKey → Base64 → SecretKey` conversion overhead
- **CurveProperties**: Simplified `Pii.Kms` to only `enabled` and `type` fields; provider-specific settings moved to `KmsProperties` in kms module
- **Build**: Added `kms` module to `publishableProjects`, SonarQube coverage paths, and JaCoCo configuration
- **Tests**: Translated all test `@DisplayName` annotations from Korean to English across core, kafka, and autoconfigure modules

### Security
- Replace `StandardEvaluationContext` with `SimpleEvaluationContext` in SpEL evaluation
    - Blocks type references, constructor calls, and static method access
    - Applies least-privilege principle with `forReadOnlyDataBinding().withInstanceMethods()`

### CI
- Bump actions/checkout from 4 to 6 (#5)
- Bump actions/upload-artifact from 4 to 6 (#3)
- Bump codecov/codecov-action from 4 to 5 (#4)
- Bump softprops/action-gh-release from 1 to 2 (#7)
- Bump stefanzweifel/git-auto-commit-action from 5 to 7 (#1)

---

## [0.0.4] - 2026-02-04

### Fixed
- **ci**: Fix git-cliff installation path

### Ci
- Add workflow_dispatch to release-docs for manual execution

---

## [0.0.3] - 2026-02-04

### Added
- **S3 Backup Strategy**: Added support for backing up failed events to AWS S3 or MinIO.
    - Ideal for Kubernetes/Cloud environments where local storage is ephemeral.
    - Configurable via `curve.kafka.backup.s3-enabled=true`.
- **Composite Backup Strategy**: Support for multiple backup strategies (e.g., try S3 first, then fallback to local file).

### Changed
- **Refactored Backup Logic**: Extracted backup logic into `EventBackupStrategy` interface for better extensibility.
- **Documentation Update**:
    - Updated all documentation to use standard Kafka port `9092` instead of `9094`.
    - Added detailed configuration guide for S3 backup.
    - Translated Javadoc and test display names to English for better internationalization.

---

## [0.0.2] - 2026-02-03

### Changed
- Avro dependencies (`avro`, `kafka-avro-serializer`) are now optional
    - JSON serialization works out of the box without additional repositories
    - Users who need Avro must explicitly add Confluent repository and dependencies

### Fixed
- Fixed GPG signing configuration for Maven Central publishing
- Fixed Sonatype Central Portal URL compatibility

---

## [0.0.1] - 2026-02-03

### Added

#### Core Features
- Declarative event publishing with `@PublishEvent` annotation
- Hexagonal architecture with framework-independent core module
- Automatic PII protection with `@PiiField` annotation
    - MASK strategy for pattern-based masking
    - ENCRYPT strategy using AES-256-GCM
    - HASH strategy using SHA-256 (later upgraded to HMAC-SHA256 in Unreleased)

#### Event Infrastructure
- Snowflake ID generator for distributed unique IDs (1024 workers, 4096 IDs/ms/worker)
- 3-tier failure recovery system (Main Topic → DLQ → Local File Backup)
- Transactional Outbox Pattern support with circuit breaker and exponential backoff
- Dynamic batch sizing based on queue depth
- Automatic cleanup job for old outbox events

#### Spring Integration
- Spring Boot auto-configuration
- AOP-based event publishing aspect
- Context providers for automatic metadata extraction
    - Actor context (Spring Security integration)
    - Trace context (MDC-based distributed tracing)
    - Correlation ID propagation
    - Client IP extraction

#### Kafka Integration
- KafkaEventProducer with sync/async modes
- Dead Letter Queue (DLQ) support
- Configurable retry with exponential backoff
- Local file backup for complete Kafka failure scenarios

#### Observability
- Health indicator (`/actuator/health/curve`)
- Custom metrics endpoint (`/actuator/curve-metrics`)
- Micrometer integration for metrics collection

#### Serialization
- JSON serialization (default)
- Apache Avro serialization support
- Schema Registry integration

#### Documentation
- README in English and Korean
- Comprehensive configuration guide
- Quick start guide for sample application

### Security
- Local backup files created with restricted permissions (600 on POSIX, ACL on Windows)
- PII encryption key required via environment variable
- Sensitive data automatically masked/encrypted in events

### Infrastructure
- JaCoCo coverage verification (70% minimum threshold)
- GitHub Actions CI/CD pipeline
- Release workflow for tag-based releases
- Multi-module Gradle project structure

---

## Version History

| Version | Date       | Description |
|---------|------------|-------------|
| 0.0.5   | 2026-02-05 | Add KMS module (AWS KMS / HashiCorp Vault) with envelope encryption |
| 0.0.4   | 2026-02-04 | Add workflow_dispatch to release-docs for manual execution |
| 0.0.3   | 2026-02-04 | Add S3 Backup Strategy & Doc Updates |
| 0.0.2   | 2026-02-03 | Make Avro dependencies optional |
| 0.0.1   | 2026-02-03 | Initial release |

---

## Upgrade Guide

### From 0.0.x to 0.1.x (Future)

_No breaking changes documented yet._

---

## Contributing

When contributing, please update this changelog:

1. Add your changes under `[Unreleased]` section
2. Use appropriate category:
    - **Added** for new features
    - **Changed** for changes in existing functionality
    - **Deprecated** for soon-to-be removed features
    - **Removed** for now removed features
    - **Fixed** for any bug fixes
    - **Security** for vulnerability fixes
3. Include issue/PR references where applicable

---

[Unreleased]: https://github.com/closeup1202/curve/compare/v0.0.5...HEAD
[0.0.5]: https://github.com/closeup1202/curve/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/closeup1202/curve/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/closeup1202/curve/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/closeup1202/curve/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/closeup1202/curve/releases/tag/v0.0.1
