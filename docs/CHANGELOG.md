# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

---

## [0.1.0] - 2026-02-07

### Security
- **AES Key Validation Enhancement**: Enforce exactly 32-byte keys; reject shorter keys instead of zero-padding to prevent weak encryption
    - Previous behavior: Keys < 32 bytes were silently padded with zeros (security risk)
    - New behavior: Keys must be exactly 32 bytes; throws `IllegalArgumentException` otherwise
    - Migration: Generate proper key with `openssl rand -base64 32`
- **HMAC Salt Security Warning**: Add prominent warning when PII HMAC salt is not configured
    - Non-breaking: Uses default salt with clear security warning in logs
    - Production deployments should configure `curve.pii.crypto.salt`
- **Envelope Encryption Boundary Validation**: Add minimum IV length validation (12 bytes) in envelope decryption
    - Prevents `IndexOutOfBoundsException` with corrupted ciphertext
    - Provides clear error messages for invalid envelope format
- **SpEL Parameter Safety**: Handle missing parameter names gracefully in SpEL expressions
    - Provides fallback parameter names (`p0`, `p1`, ...) when debug info unavailable
    - Prevents `NullPointerException` in builds without parameter name retention
- **Vault Path Traversal Protection**: Add regex validation for Vault keyId to prevent path traversal attacks
    - Only allows alphanumeric characters, underscores, and hyphens
    - Blocks malicious keyIds like `../../admin-key`

### Performance
- **Outbox Query Caching**: Cache pending event count with 5-second TTL
    - Reduces database queries from 86,400/day to 17,280/day (80% reduction)
    - Significantly decreases DB load in high-throughput scenarios
- **Regex Pre-compilation**: Pre-compile regex patterns in `PhoneMasker` for ~30% performance improvement
    - Eliminates per-call compilation overhead
    - Improves PII masking throughput
- **Circuit Breaker Thread Safety**: Synchronize circuit breaker state transitions
    - Prevents race conditions in multi-threaded environments
    - Ensures reliable circuit breaker operation under concurrent load
- **Enhanced Debug Logging**: Add debug logging to `EventEnvelopeFactory`
    - Improves event creation traceability
    - Aids in debugging event publication issues

### Changed
- **Code Quality**: Refactored `SchemaRegistry.findMigrationPath()` to improve maintainability
    - Split 111-instruction method into 6 smaller, focused methods
    - Added 5 edge case tests for migration path scenarios
- **Documentation Structure**: Consolidated `CHANGELOG.md` to single source in `docs/`
    - Removed duplicate from project root
    - Updated README references

### Fixed
- **Test Compatibility**: Updated `AesUtilTest` to expect key length validation
    - Test now validates security improvement (key rejection)

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
