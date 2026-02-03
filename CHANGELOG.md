# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Operations guide documentation (`docs/OPERATIONS.en.md`)
- User Service sample with PII protection examples
- Payment Service sample with error handling and Outbox pattern
- Version management with `gradle.properties`

---

## [0.0.1] - 2024-XX-XX

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

| Version | Date | Description |
|---------|------|-------------|
| 0.0.1 | TBD | Initial release |

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

[Unreleased]: https://github.com/closeup1202/curve/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/closeup1202/curve/releases/tag/v0.0.1
