# Dependency Management

This document describes how dependencies are managed in the Curve project.

## Automated Dependency Updates

### Dependabot Configuration

Curve uses [GitHub Dependabot](https://docs.github.com/en/code-security/dependabot) to automatically keep dependencies up-to-date and secure.

**Configuration File**: `.github/dependabot.yml`

### Update Schedule

Dependabot runs **weekly on Mondays at 09:00 KST** and checks for updates in:

1. **Gradle Dependencies** (`/`)
   - Spring Framework & Spring Boot
   - AWS SDK
   - Apache Kafka
   - Testing libraries (JUnit, Mockito, TestContainers)
   - All other Gradle dependencies

2. **GitHub Actions** (`/`)
   - Workflow action versions
   - Runner images

3. **Docker** (`/`)
   - Base images in `docker-compose.yaml`
   - Dockerfile base images

### Dependency Grouping

To reduce noise, Dependabot groups related dependencies together:

| Group | Packages | Update Types |
|-------|----------|--------------|
| `spring-framework` | `org.springframework*`, `org.springframework.boot*` | minor, patch |
| `aws-sdk` | `software.amazon.awssdk*` | minor, patch |
| `kafka` | `org.apache.kafka*`, `org.springframework.kafka*` | minor, patch |
| `testing` | `org.junit*`, `org.mockito*`, `org.assertj*`, `org.testcontainers*` | minor, patch |

**Major version updates** are created as **individual PRs** for careful review.

### Pull Request Management

- **Maximum PRs**: 10 for Gradle, 5 for GitHub Actions/Docker
- **Labels**: Automatically tagged with `dependencies` and ecosystem-specific labels
- **Commit Message Format**: `chore(deps): <description>` or `ci(deps): <description>`
- **Reviewers**: Automatically assigns `@closeup1202`

### Security Updates

Dependabot automatically creates PRs for:
- **Security vulnerabilities** (CVEs)
- **Outdated dependencies** with known exploits

These PRs are prioritized and should be reviewed promptly.

---

## Current Dependency Versions

### Core Libraries (as of 2026-02-05)

| Library | Version | Notes |
|---------|---------|-------|
| Java | 17 (LTS) | Also supports Java 21 |
| Spring Boot | 3.5.9 | Latest stable |
| Apache Kafka | 3.0+ | Client library via Spring Kafka |
| AWS SDK for Java | 2.41.21 | Latest stable (updated from 2.21.1) |
| Jackson | 2.x | Managed by Spring Boot BOM |
| Micrometer | 1.x | Managed by Spring Boot BOM |

### Testing Libraries

| Library | Version | Notes |
|---------|---------|-------|
| JUnit 5 | 5.x | Managed by Spring Boot BOM |
| Mockito | 5.x | Managed by Spring Boot BOM |
| AssertJ | 3.x | Managed by Spring Boot BOM |
| TestContainers | 1.20.4 | For embedded Kafka tests |

### Build Tools

| Tool | Version | Notes |
|------|---------|-------|
| Gradle | 8.14.3 | Via wrapper |
| JaCoCo | 0.8.12 | Test coverage |
| SonarQube Plugin | 4.4.1.3373 | Code quality |

---

## Recent Dependency Updates

### 2026-02-05: AWS SDK Major Update

**Changed**: `software.amazon.awssdk:bom` from `2.21.1` → `2.41.21`

**Reason**:
- Security patches for 20+ months of updates
- Performance improvements in S3 client
- Enhanced error handling and retry logic
- Reduced memory footprint

**Verification**:
```bash
# Verify BOM version
./gradlew :kafka:dependencies --configuration runtimeClasspath | grep awssdk:bom

# Run tests
./gradlew :kafka:test --tests "*UnitTest"
```

**Breaking Changes**: None - backward compatible

**Affected Modules**: `kafka` module (S3 backup strategy)

---

## Manual Dependency Management

### Checking for Updates

```bash
# Check all dependencies for updates
./gradlew dependencyUpdates

# Check specific module
./gradlew :kafka:dependencyUpdates
```

### Updating a Specific Dependency

1. **Edit the build file**: Modify version in `build.gradle` or module-specific `build.gradle`
2. **Verify compatibility**: Check changelog/migration guide
3. **Run tests**: `./gradlew test`
4. **Check coverage**: `./gradlew jacocoTestReport`
5. **Update CHANGELOG**: Document the change in `docs/CHANGELOG.md`

### Version Constraints

Some dependencies are intentionally pinned:

| Dependency | Constraint | Reason |
|------------|-----------|--------|
| Java | 17 (LTS) | Major version updates require compatibility testing |
| Spring Boot | 3.x | Major version updates may require code changes |

---

## Dependency Security

### Vulnerability Scanning

The project uses multiple security scanning tools:

1. **Trivy** (CI/CD)
   - Scans filesystem for known vulnerabilities
   - Runs on every PR and push to main
   - Results uploaded to GitHub Security tab

2. **SonarQube** (CI/CD)
   - Analyzes code quality and security hotspots
   - Detects vulnerable dependency usage patterns

3. **Dependabot Security Alerts**
   - Automatic detection of vulnerable dependencies
   - Priority PRs for critical security issues

### Responding to Security Alerts

1. **Review the alert**: Check CVE details and affected versions
2. **Update dependency**: Accept Dependabot PR or manually update
3. **Test thoroughly**: Ensure no breaking changes
4. **Deploy quickly**: Security patches should be expedited

---

## Best Practices

### For Contributors

1. **Don't manually update dependencies** without checking Dependabot first
2. **Group related updates** (e.g., all Spring Framework libraries together)
3. **Test extensively** after major version updates
4. **Document breaking changes** in CHANGELOG and migration guide

### For Maintainers

1. **Review Dependabot PRs weekly**
2. **Merge minor/patch updates promptly** (they're usually safe)
3. **Carefully review major updates** (may require code changes)
4. **Monitor CI/CD results** before merging

---

## Troubleshooting

### Dependabot PR Failed CI

1. Check the CI logs for the specific failure
2. If compilation fails, review breaking changes in dependency changelog
3. If tests fail, update test code to match new API
4. Re-run CI after fixes

### Dependency Conflicts

```bash
# View dependency tree
./gradlew :module:dependencies

# Identify conflict
./gradlew :module:dependencyInsight --dependency <dependency-name>
```

**Resolution strategies**:
- Use `exclude` in Gradle to exclude transitive dependencies
- Use `force` to force a specific version
- Align versions using Spring Boot BOM or platform dependencies

---

## References

- [Dependabot Documentation](https://docs.github.com/en/code-security/dependabot)
- [Gradle Dependency Management](https://docs.gradle.org/current/userguide/dependency_management.html)
- [Spring Boot Dependency Management](https://docs.spring.io/spring-boot/docs/current/reference/html/dependency-versions.html)
- [AWS SDK for Java Changelog](https://github.com/aws/aws-sdk-java-v2/blob/master/CHANGELOG.md)
