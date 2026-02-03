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
| 0.0.1 | 3.2.x - 3.5.x | 17, 21 | 3.0+ |
| 0.1.x (planned) | 3.2.x - 3.6.x | 17, 21 | 3.0+ |

### Dependency Compatibility

```gradle
// build.gradle
dependencies {
    // Curve 0.0.1 compatible versions
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

## Migration: 0.0.x to 0.1.x

> **Note:** Version 0.1.x is not yet released. This section will be updated with specific migration steps when available.

### Expected Changes (Tentative)

1. **Configuration Namespace**
   - No changes expected

2. **API Changes**
   - `EventEnvelope` may have additional fields
   - New methods in `EventProducer` interface

3. **Database Schema**
   - Outbox table may require new columns
   - Migration script will be provided

### Preparation Steps

```yaml
# Ensure you're on latest 0.0.x before upgrading
curve:
  version: 0.0.x  # Update to latest patch first
```

---

## Configuration Changes

### Configuration Changelog

#### Version 0.0.1 (Initial)

All configurations introduced. See [CONFIGURATION.md](CONFIGURATION.en.md).

#### Version 0.1.x (Planned)

| Old Property | New Property | Notes |
|--------------|--------------|-------|
| TBD | TBD | Will be documented |

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
   implementation 'com.project:curve-spring-boot-autoconfigure:0.0.1'  // Previous version
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
    implementation 'com.project:curve-spring-boot-autoconfigure:0.0.1'  // Step 2
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
