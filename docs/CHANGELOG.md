# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **deps**: Bump SonarQube Gradle plugin from 4.4.1.3373 to 7.2.2.6593 (#14)
    - Resolves deprecation warning for compile task dependency
    - Adds Gradle 9 and configuration-cache support
- **deps**: Bump AWS SDK BOM from 2.21.1 to 2.41.21 (#2)
    - Fixes version mismatch between `kafka` and `spring-boot-autoconfigure` modules
    - Includes 20+ months of security patches and performance improvements
- **deps**: Bump Confluent Kafka Avro Serializer from 7.5.0 to 8.1.1 (#11)
- **deps**: Bump TestContainers from 1.20.4 to 1.21.4 (#6)
- **deps**: Bump Release Plugin from 3.0.2 to 3.1.0 (#10)

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
    - HASH strategy using SHA-256 (irreversible)

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

[Unreleased]: https://github.com/closeup1202/curve/compare/v0.0.3...HEAD
[0.0.4]: https://github.com/closeup1202/curve/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/closeup1202/curve/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/closeup1202/curve/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/closeup1202/curve/releases/tag/v0.0.1
