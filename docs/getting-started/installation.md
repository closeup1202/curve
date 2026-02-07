---
title: Installation Guide - Curve for Spring Boot
description: Complete installation guide for Curve event publishing library. Learn about dependencies, compatibility, and setup options.
keywords: curve installation, spring boot setup, kafka library installation, gradle maven setup
---

# Installation

## System Requirements

| Component | Version |
|-----------|---------|
| Java | 17 or higher |
| Spring Boot | 3.0+ |
| Apache Kafka | 2.8+ (3.0+ recommended) |
| Build Tool | Gradle 7+ or Maven 3.6+ |

## Dependency Installation

### Gradle

Add to your `build.gradle`:

```gradle title="build.gradle"
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.closeup1202:curve:0.1.0'
}
```

### Maven

Add to your `pom.xml`:

```xml title="pom.xml"
<dependencies>
    <dependency>
        <groupId>io.github.closeup1202</groupId>
        <artifactId>curve</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

## Optional Dependencies

### Avro Serialization

If you want to use Avro serialization (`serde.type: AVRO`), add:

=== "Gradle"

    ```gradle title="build.gradle"
    repositories {
        mavenCentral()
        maven { url 'https://packages.confluent.io/maven/' }
    }

    dependencies {
        implementation 'org.apache.avro:avro:1.11.4'
        implementation 'io.confluent:kafka-avro-serializer:8.1.1'
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <repositories>
        <repository>
            <id>confluent</id>
            <url>https://packages.confluent.io/maven/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.apache.avro</groupId>
            <artifactId>avro</artifactId>
            <version>1.11.4</version>
        </dependency>
        <dependency>
            <groupId>io.confluent</groupId>
            <artifactId>kafka-avro-serializer</artifactId>
            <version>7.5.0</version>
        </dependency>
    </dependencies>
    ```

!!! note "JSON by Default"
    Curve uses JSON serialization by default, which requires no additional dependencies.

### Database for Transactional Outbox

If using the transactional outbox pattern, ensure you have a JPA-compatible database:

```gradle
// Example: PostgreSQL
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'org.postgresql:postgresql'
```

## Version Compatibility

| Curve Version | Spring Boot | Kafka Client | Java |
|---------------|-------------|--------------|------|
| 0.1.0         | 3.5.x | 3.8.x | 17+ |
| 0.0.5         | 3.5.x | 3.8.x | 17+ |
| 0.0.2         | 3.5.x | 3.8.x | 17+ |
| 0.0.1         | 3.4.x | 3.7.x | 17+ |

## Verify Installation

After adding the dependency, verify Curve is correctly installed:

### 1. Build Your Project

=== "Gradle"

    ```bash
    ./gradlew clean build
    ```

=== "Maven"

    ```bash
    ./mvnw clean install
    ```

### 2. Check Auto-Configuration

Enable debug logging to see if Curve auto-configuration is loaded:

```yaml title="application.yml"
logging:
  level:
    io.github.closeup1202.curve: DEBUG
```

Start your application and look for:

```
CurveAutoConfiguration matched:
   - @ConditionalOnProperty (curve.enabled=true) matched
```

### 3. Test Health Endpoint

```bash
curl http://localhost:8080/actuator/health/curve
```

Expected response:

```json
{
  "status": "UP",
  "details": {
    "kafkaProducerInitialized": true,
    "clusterId": "lkc-abc123",
    "nodeCount": 3,
    "topic": "event.audit.v1",
    "dlqTopic": "event.audit.dlq.v1"
  }
}
```

## Troubleshooting

### Dependency Resolution Fails

!!! failure "Error: Could not find io.github.closeup1202:curve:0.1.0"

    **Solution**: Ensure Maven Central is in your repositories:

    === "Gradle"

        ```gradle
        repositories {
            mavenCentral()
        }
        ```

    === "Maven"

        Maven includes Central by default. Try:

        ```bash
        ./mvnw dependency:purge-local-repository
        ```

### Auto-Configuration Not Loading

!!! failure "Curve features not working"

    **Check**:

    1. Spring Boot version is 3.0+
    2. `curve.enabled=true` in application.yml
    3. No conflicting auto-configurations

### Kafka Client Version Conflict

!!! failure "ClassNotFoundException or MethodNotFoundException"

    **Solution**: Align Kafka client versions:

    ```gradle
    dependencies {
        implementation('io.github.closeup1202:curve:0.1.0') {
            exclude group: 'org.apache.kafka'
        }
        implementation 'org.apache.kafka:kafka-clients:3.8.0'
    }
    ```

## Next Steps

Once installed, proceed to:

1. [Quick Start Guide](quick-start.md) - Create your first event
2. [Configuration](../CONFIGURATION.md) - Set up production settings
3. [First Event Tutorial](first-event.md) - Detailed walkthrough

---

!!! tip "Need Help?"
    If you encounter issues, check the [Troubleshooting Guide](../TROUBLESHOOTING.md) or [open an issue](https://github.com/closeup1202/curve/issues).
