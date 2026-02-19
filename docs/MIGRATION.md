# Migration Guide

This guide helps you upgrade between Curve versions safely.

## Table of Contents

- [Versioning Strategy](#versioning-strategy)
- [Version Compatibility Matrix](#version-compatibility-matrix)
- [Upgrade Checklist](#upgrade-checklist)
- [Migration: 0.0.x to 0.1.x](#migration-00x-to-01x)
- [Configuration Changes](#configuration-changes)
- [Breaking Changes Log](#breaking-changes-log)
- [Rollback Procedures](#rollback-procedures)

---

## Versioning Strategy

Curve follows [Semantic Versioning 2.0.0](https://semver.org/):

```
MAJOR.MINOR.PATCH

Example: 1.2.3
         │ │ └── Patch: Bug fixes, no API changes
         │ └──── Minor: New features, backward compatible
         └────── Major: Breaking changes
```

### Version Guidelines

| Version Type | Changes | Action Required |
|--------------|---------|-----------------|
| Patch (x.x.1) | Bug fixes only | Safe to upgrade immediately |
| Minor (x.1.x) | New features, deprecations | Review changelog, test in staging |
| Major (1.x.x) | Breaking changes | Follow migration guide carefully |

### Pre-release Versions

- `0.x.x`: Initial development, API may change
- `x.x.x-alpha`: Early testing, unstable
- `x.x.x-beta`: Feature complete, testing phase
- `x.x.x-rc.1`: Release candidate

---

## Version Compatibility Matrix

| Curve Version | Spring Boot | Java | Kafka Client |
|---------------|-------------|------|--------------|
| 0.1.0 | 3.5.x | 17, 21 | 3.8+ |
| 0.0.5 | 3.5.x | 17, 21 | 3.8+ |
| 0.0.1 - 0.0.4 | 3.4.x - 3.5.x | 17, 21 | 3.0+ |

### Dependency Compatibility

```gradle
// build.gradle
dependencies {
    // Curve 0.1.0 compatible versions
    implementation 'org.springframework.boot:spring-boot-starter:3.5.9'
    implementation 'org.springframework.kafka:spring-kafka:3.3.0'
}
```

---

## Upgrade Checklist

Before upgrading to a new version:

### Pre-Upgrade

- [ ] Read the [CHANGELOG](../CHANGELOG.md) for the target version
- [ ] Check for breaking changes
- [ ] Review deprecated features you're using
- [ ] Backup database (especially outbox table)
- [ ] Test upgrade in staging environment

### During Upgrade

- [ ] Update dependency version
- [ ] Update configuration if required
- [ ] Update code for any API changes
- [ ] Run tests

### Post-Upgrade

- [ ] Verify application starts successfully
- [ ] Check `/actuator/health/curve` endpoint
- [ ] Monitor metrics for anomalies
- [ ] Verify events are being published
- [ ] Check outbox table processing (if enabled)

---

## Migration: 0.0.x to 0.1.0

Version 0.1.0 includes important security enhancements and performance optimizations. Most changes are backward compatible, but please review the following:

### Changes in 0.1.0

1. **Security Improvements (Non-Breaking)**
   - **AES Key Validation**: Keys must be exactly 32 bytes (Base64-encoded)
     - Previous: Keys < 32 bytes were padded with zeros (insecure)
     - Now: Keys must be exactly 32 bytes; throws `IllegalArgumentException` otherwise
     - **Action**: Regenerate keys with `openssl rand -base64 32`

   - **HMAC Salt Warning**: Warns if PII HMAC salt not configured
     - **Action**: Set `curve.pii.crypto.salt` for production

   - **Vault Path Traversal Protection**: Added validation for Vault keyId
     - Only alphanumeric, underscores, and hyphens allowed
     - **Action**: No action needed unless using invalid keyId patterns

2. **Performance Improvements (Transparent)**
   - Outbox query caching (5-second TTL)
   - Regex pre-compilation in PhoneMasker
   - Circuit breaker thread safety
   - All improvements are automatic

3. **API Changes**
   - No breaking API changes
   - All existing code continues to work

### Upgrade Steps

```gradle
// Update dependency version
dependencies {
    implementation 'io.github.closeup1202:curve:0.1.1'
}
```

```yaml
# Recommended: Configure HMAC salt (if not already set)
curve:
  pii:
    crypto:
      salt: ${PII_HASH_SALT}  # Generate with: openssl rand -base64 32
```

---

## Configuration Changes

### Configuration Changelog

#### Version 0.0.1 (Initial)

All configurations introduced. See [CONFIGURATION.md](CONFIGURATION.md).

#### Version 0.0.5

- Added `curve.async.*` properties (enabled, core-pool-size, max-pool-size, queue-capacity)
- Added `curve.kafka.backup.s3-*` properties for S3 backup
- Added `curve.kafka.sync-timeout-seconds`
- Added `curve.outbox.send-timeout-seconds`
- Added KMS properties (`curve.pii.kms.*`)

#### Unreleased (Security Improvements)

- **PII HASH strategy**: Changed from SHA-256 to HMAC-SHA256. Existing hashed values will differ after upgrade.
- **AES encryption key**: Must be exactly 32 bytes (Base64-encoded). Keys of incorrect length are now rejected.
- **Health check**: Now uses `AdminClient.describeCluster()` instead of `KafkaTemplate.metrics()`. Health response format changed (`clusterId` and `nodeCount` instead of `producerMetrics`).
- **Async executor**: `@EnableAsync` is no longer automatically applied. Set `curve.async.enabled=true` to register the `curveAsyncExecutor` bean.

#### Version 0.1.0

No configuration property changes. All changes are backward compatible.

### Deprecated Properties

Currently, no properties are deprecated.

When properties are deprecated:
```yaml
# Deprecated in 0.2.0, removed in 1.0.0
curve:
  kafka:
    old-property: value  # DEPRECATED: Use 'new-property' instead
    new-property: value
```

---

## Breaking Changes Log

### Version 0.1.0

No breaking changes. All improvements are backward compatible with proper warnings.

### Version 0.0.1

Initial release - no breaking changes (baseline).

### Future Versions

Breaking changes will be documented here with:
- What changed
- Why it changed
- How to migrate

---

## Rollback Procedures

### Quick Rollback

1. **Revert dependency version**
   ```gradle
   // build.gradle
   implementation 'io.github.closeup1202:curve:0.0.5'  // Previous version
   ```

2. **Revert configuration changes** (if any)

3. **Redeploy application**

### Database Rollback (Outbox)

If schema changed, you may need to rollback database:

```sql
-- Example rollback script (adjust based on actual changes)
-- WARNING: This may cause data loss

-- Backup first
CREATE TABLE curve_outbox_events_backup AS
SELECT * FROM curve_outbox_events;

-- Rollback schema (example)
ALTER TABLE curve_outbox_events DROP COLUMN IF EXISTS new_column;
```

### Rollback Checklist

- [ ] Revert application version
- [ ] Revert configuration
- [ ] Revert database schema (if changed)
- [ ] Clear any cached data
- [ ] Restart all instances
- [ ] Verify health checks
- [ ] Monitor for issues

---

## Upgrading Dependencies

### Spring Boot Upgrade

When upgrading Spring Boot:

1. Check Curve compatibility in the matrix above
2. Upgrade Spring Boot first
3. Test thoroughly
4. Then upgrade Curve if needed

```gradle
// Upgrade order
plugins {
    id 'org.springframework.boot' version '3.5.9'  // Step 1
}

dependencies {
    implementation 'io.github.closeup1202:curve:0.1.1'  // Step 2
}
```

### Kafka Client Upgrade

Curve is compatible with Kafka Client 3.0+. When upgrading:

1. Check Spring Kafka compatibility
2. Test producer functionality
3. Verify serialization works correctly

---

## Getting Help

If you encounter issues during migration:

1. Check [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
2. Search [existing issues](https://github.com/closeup1202/curve/issues)
3. Open a new issue with:
   - Source version
   - Target version
   - Error messages
   - Configuration (sanitized)

---

## Version History

See [CHANGELOG.md](../CHANGELOG.md) for complete version history.
