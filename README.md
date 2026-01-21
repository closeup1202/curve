# Curve

> **Spring Boot 기반 이벤트 발행 라이브러리**
> 도메인 이벤트를 Kafka로 표준화하여 수집하는 엔터프라이즈급 공통 라이브러리

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.0+-red.svg)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## 📖 목차

- [개요](#-개요)
- [주요 특징](#-주요-특징)
- [아키텍처](#-아키텍처)
- [빠른 시작](#-빠른-시작)
- [설치](#-설치)
- [사용법](#-사용법)
- [설정](#-설정)
- [고급 기능](#-고급-기능)
- [문서](#-문서)
---

## 🎯 개요

**Curve**는 마이크로서비스 환경에서 발생하는 도메인 이벤트를 **표준화된 형식**으로 Kafka에 발행하는 Spring Boot 라이브러리입니다.

### 해결하는 문제

| 문제 | Curve의 해결책 |
|------|---------------|
| 🔴 **이벤트 구조가 제각각** | ✅ 공통 Event Envelope 제공 |
| 🔴 **비즈니스 코드에 Kafka 로직 침투** | ✅ AOP 기반 선언적 이벤트 발행 |
| 🔴 **감사 로그 추적 어려움** | ✅ traceId, actor, source 자동 추출 |
| 🔴 **이벤트 손실 위험** | ✅ DLQ + Retry + 로컬 백업 |
| 🔴 **분산 환경 ID 충돌** | ✅ Snowflake ID Generator |
| 🔴 **프록시 환경 IP 스푸핑** | ✅ Spring ForwardedHeaderFilter 통합 |

---

## ✨ 주요 특징

### 1. **선언적 이벤트 발행**
```java
@PublishEvent(eventType = "USER_LOGIN", severity = INFO)
public User login(String username) {
    // 비즈니스 로직만 작성
    return userRepository.findByUsername(username);
}
// 이벤트는 자동으로 Kafka에 발행됨 ✨
```

### 2. **표준화된 이벤트 구조**
```json
{
  "eventId": "1234567890123456789",
  "eventType": "USER_LOGIN",
  "severity": "INFO",
  "occurredAt": "2026-01-15T10:30:00Z",
  "publishedAt": "2026-01-15T10:30:00.123Z",
  "metadata": {
    "source": {
      "service": "auth-service",
      "environment": "production",
      "instanceId": "auth-pod-1",
      "host": "ip-10-0-1-42",
      "version": "1.0.0"
    },
    "actor": {
      "userId": "user123",
      "role": "ROLE_USER",
      "ip": "203.0.113.42"
    },
    "trace": {
      "traceId": "a1b2c3d4e5f6",
      "spanId": "span-789",
      "parentSpanId": null
    },
    "schema": {
      "name": "UserLoginPayload",
      "version": 1,
      "schemaId": null
    },
    "tags": {
      "region": "ap-northeast-2",
      "tenant": "company-001"
    }
  },
  "payload": {
    "eventType": "USER_LOGIN",
    "className": "com.example.AuthService",
    "methodName": "login",
    "data": { "username": "user123" }
  }
}
```

### 3. **Hexagonal Architecture (육각형 아키텍처)**
```
┌─────────────────────────────────────────────┐
│  Application (spring-boot-autoconfigure)    │
│  - Auto-Configuration                       │
│  - CurveProperties                          │
└──────────────┬──────────────────────────────┘
               │
    ┌──────────┴──────────┐
    │                     │
┌───▼────┐          ┌────▼─────┐
│ Spring │          │  Kafka   │
│ (AOP)  │          │(Producer)│
└───┬────┘          └────┬─────┘
    │                    │
    └──────────┬─────────┘
               │
        ┌──────▼──────┐
        │    Core     │
        │ (Domain)    │
        └─────────────┘
```

### 4. **강력한 안정성**
- ✅ **DLQ (Dead Letter Queue)**: 실패한 이벤트 자동 백업
- ✅ **Exponential Backoff Retry**: 지수 백오프 재시도
- ✅ **로컬 파일 백업**: DLQ 실패 시 최후의 안전망
- ✅ **동기/비동기 모드**: 성능과 안정성 선택 가능

### 5. **엔터프라이즈 보안**
- ✅ **ForwardedHeaderFilter 통합**: X-Forwarded-For 스푸핑 방지
- ✅ **신뢰할 수 있는 프록시 검증**: 안전한 클라이언트 IP 추출
- ✅ **Spring Security 통합**: 자동 인증 정보 추출

### 6. **Spring Boot Auto-Configuration**
```yaml
curve:
  enabled: true  # 단 한 줄로 활성화
```

---

## 🏗️ 아키텍처

### 모듈 구조

```
curve/
├── core/                          # 순수 도메인 모델 (프레임워크 독립)
│   ├── envelope/                  # EventEnvelope, EventMetadata
│   ├── port/                      # EventProducer, IdGenerator (인터페이스)
│   ├── context/                   # ContextProvider (인터페이스)
│   ├── validation/                # EventValidator
│   └── exception/                 # 도메인 예외
│
├── spring/                        # Spring Framework 어댑터
│   ├── aop/                       # @PublishEvent Aspect
│   ├── context/                   # Spring 기반 Context Provider 구현
│   │   ├── actor/                 # SpringSecurityActorContextProvider
│   │   ├── trace/                 # MdcTraceContextProvider
│   │   ├── source/                # SpringSourceContextProvider
│   │   ├── schema/                # AnnotationBasedSchemaContextProvider
│   │   └── tag/                   # MdcTagsContextProvider
│   ├── factory/                   # EventEnvelopeFactory
│   ├── infrastructure/            # SnowflakeIdGenerator, UtcClockProvider
│   └── publisher/                 # AbstractEventPublisher
│
├── kafka/                         # Kafka 어댑터
│   ├── producer/                  # KafkaEventProducer
│   └── dlq/                       # FailedEventRecord
│
└── spring-boot-autoconfigure/     # Spring Boot 자동 설정
    ├── CurveAutoConfiguration     # 메인 설정
    ├── CurveProperties            # 설정 클래스
    ├── kafka/                     # Kafka 자동 설정
    ├── retry/                     # Retry 자동 설정
    ├── context/                   # Context Provider 자동 설정
    ├── aop/                       # AOP 자동 설정
    └── envelope/                  # Envelope Factory 자동 설정
```

### 핵심 설계 원칙

1. **의존성 역전 원칙 (DIP)**
   - Core 모듈은 프레임워크에 의존하지 않음
   - Port 인터페이스를 통한 외부 의존성 격리

2. **단일 책임 원칙 (SRP)**
   - 각 ContextProvider는 하나의 책임만 수행
   - EventValidator는 검증만, EventProducer는 발행만

3. **개방-폐쇄 원칙 (OCP)**
   - EventProducer 인터페이스로 Kafka 외 다른 브로커 확장 가능
   - ContextProvider 구현 교체 가능

---

## 🚀 빠른 시작

### 1. 의존성 추가

**Gradle (build.gradle)**
```gradle
dependencies {
    implementation 'com.project:curve-spring-boot-starter:0.0.1-SNAPSHOT'
}
```

**Maven (pom.xml)**
```xml
<dependency>
    <groupId>com.project</groupId>
    <artifactId>curve-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. 설정 추가

**application.yml**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9094

curve:
  enabled: true
  id-generator:
    worker-id: 1
  kafka:
    topic: event.audit.v1
    dlq-topic: event.audit.dlq.v1
```

### 3. 이벤트 발행

```java
import com.project.curve.spring.audit.annotation.PublishEvent;
import com.project.curve.core.type.EventSeverity;

@Service
public class UserService {

    @PublishEvent(
        eventType = "USER_CREATED",
        severity = EventSeverity.INFO,
        phase = Phase.AFTER_RETURNING
    )
    public User createUser(CreateUserRequest request) {
        // 비즈니스 로직
        User user = new User(request.getUsername());
        userRepository.save(user);
        return user; // 반환값이 자동으로 이벤트 페이로드로 사용됨
    }
}
```

### 4. 로컬 Kafka 실행

```bash
docker-compose up -d
```

### 5. 확인

- **Kafka UI**: http://localhost:8080
- **Topic**: `event.audit.v1`

---

## 📦 설치

### 요구사항

- ✅ Java 17 이상
- ✅ Spring Boot 3.5.x
- ✅ Apache Kafka 3.0+

### Gradle 멀티 모듈 빌드

```bash
# 전체 빌드
./gradlew clean build

# 특정 모듈만 빌드
./gradlew :core:build
./gradlew :spring:build
./gradlew :kafka:build
./gradlew :spring-boot-autoconfigure:build
```

### 로컬 Maven 저장소 설치

```bash
./gradlew publishToMavenLocal
```

---

## 📚 사용법

### 1. @PublishEvent 어노테이션

#### 기본 사용
```java
@PublishEvent(eventType = "ORDER_CREATED")
public Order createOrder(OrderRequest request) {
    return orderRepository.save(new Order(request));
}
```

#### 고급 옵션
```java
@PublishEvent(
    eventType = "PAYMENT_PROCESSED",
    severity = EventSeverity.CRITICAL,      // 심각도
    payloadIndex = 0,                       // 0번째 파라미터를 페이로드로 사용
    phase = Phase.AFTER_RETURNING,          // 정상 반환 후 발행
    failOnError = false                     // 실패 시 예외 전파 안 함
)
public Receipt processPayment(PaymentRequest request) {
    return paymentService.process(request);
}
```

#### Phase 옵션
- `Phase.BEFORE`: 메서드 실행 전
- `Phase.AFTER_RETURNING`: 정상 반환 후 (기본값)
- `Phase.AFTER`: 실행 후 (예외 무관)

### 2. EventProducer 직접 사용

```java
@Service
@RequiredArgsConstructor
public class CustomEventService {

    private final EventProducer eventProducer;

    public void publishCustomEvent() {
        // 커스텀 페이로드 생성
        MyEventPayload payload = new MyEventPayload(
            EventType.of("CUSTOM_EVENT"),
            "customData"
        );

        // 발행
        eventProducer.publish(payload, EventSeverity.INFO);
    }
}
```

### 3. 커스텀 Payload 정의

```java
import com.project.curve.core.annotation.PayloadSchema;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.type.EventType;

@PayloadSchema(name = "UserEvent", version = 2)
public class UserEventPayload implements DomainEventPayload {

    private final EventType eventType;
    private final String userId;
    private final String action;

    public UserEventPayload(EventType eventType, String userId, String action) {
        this.eventType = eventType;
        this.userId = userId;
        this.action = action;
    }

    @Override
    public EventType getEventType() {
        return eventType;
    }

    // Getters...
}
```

---

## ⚙️ 설정

### 전체 설정 옵션

```yaml
curve:
  # 라이브러리 활성화 여부 (기본값: true)
  enabled: true

  # ID Generator 설정
  id-generator:
    # Snowflake Worker ID (0 ~ 1023, 분산 환경에서 고유해야 함)
    worker-id: ${WORKER_ID:1}
    # MAC 주소 기반 자동 생성 (프로덕션 비권장)
    auto-generate: false

  # Kafka 설정
  kafka:
    # 메인 토픽
    topic: event.audit.v1
    # Dead Letter Queue 토픽
    dlq-topic: event.audit.dlq.v1
    # Producer 재시도 횟수
    retries: 3
    # 재시도 백오프 시간(ms)
    retry-backoff-ms: 1000
    # 요청 타임아웃(ms)
    request-timeout-ms: 30000
    # 비동기 전송 모드 (true: 고성능, false: 고신뢰성)
    async-mode: false
    # 비동기 타임아웃(ms)
    async-timeout-ms: 5000

  # Retry 설정 (Application 레벨)
  retry:
    enabled: true
    max-attempts: 3
    initial-interval: 1000
    multiplier: 2.0
    max-interval: 10000

  # AOP 설정
  aop:
    enabled: true

  # 보안 설정
  security:
    # X-Forwarded-For 헤더 사용 여부 (프록시 환경에서만 true)
    use-forwarded-headers: false

# Spring Boot 설정
server:
  # 프록시 헤더 처리 전략
  forward-headers-strategy: none  # none | native | framework

# Kafka 기본 설정
spring:
  kafka:
    bootstrap-servers: localhost:9094
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

### 환경별 프로파일

#### 개발 환경 (dev)
```yaml
spring:
  config:
    activate:
      on-profile: dev

curve:
  kafka:
    topic: event.audit.dev.v1
    async-mode: true  # 빠른 테스트
```

#### 프로덕션 환경 (prod)
```yaml
spring:
  config:
    activate:
      on-profile: prod

curve:
  id-generator:
    worker-id: ${POD_ORDINAL}  # Kubernetes StatefulSet
  kafka:
    async-mode: false  # 안정성 우선
    retries: 5
```

#### 프록시 환경 (behind-proxy)
```yaml
spring:
  config:
    activate:
      on-profile: behind-proxy

curve:
  security:
    use-forwarded-headers: true

server:
  forward-headers-strategy: framework
  tomcat:
    remoteip:
      internal-proxies: 10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}
      remote-ip-header: X-Forwarded-For
```

> 📖 더 많은 설정 예시는 [`application.example.yml`](application.example.yml)을 참고하세요.

---

## 🔥 고급 기능

### 1. Snowflake ID Generator

분산 환경에서 충돌 없는 64비트 ID 생성

**구조**
```
| 42비트: 타임스탬프 | 10비트: Worker ID | 12비트: Sequence |
```

**특징**
- ✅ 초당 최대 4,096개 ID 생성 (per worker)
- ✅ 시간 기반 정렬 가능
- ✅ 1024개 Worker 지원

**설정**
```yaml
curve:
  id-generator:
    # Kubernetes StatefulSet 예시
    worker-id: ${POD_ORDINAL:1}
```

### 2. Context Provider 커스터마이징

#### 커스텀 ActorContextProvider
```java
@Bean
public ActorContextProvider customActorProvider() {
    return () -> {
        // 커스텀 로직
        String userId = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return new EventActor(userId, "CUSTOM_ROLE", "0.0.0.0");
    };
}
```

#### 커스텀 TagsContextProvider
```java
@Bean
public TagsContextProvider customTagsProvider() {
    return MdcTagsContextProvider.withKeys("region", "tenant", "customKey");
}
```

### 3. DLQ (Dead Letter Queue)

**작동 방식**
```
Main Topic 전송 실패
    ↓
DLQ로 동기 전송
    ↓ (성공)
이벤트 보존
    ↓ (실패)
로컬 파일 백업 (./dlq-backup/{eventId}.json)
```

**DLQ 메시지 구조**
```json
{
  "eventId": "1234567890123456789",
  "originalTopic": "event.audit.v1",
  "originalPayload": "{...}",
  "exceptionType": "org.apache.kafka.common.errors.TimeoutException",
  "exceptionMessage": "Failed to send...",
  "failedAt": 1705300800000
}
```

### 4. Retry 전략

**Exponential Backoff**
```
재시도 1차: 즉시 (0ms)
재시도 2차: 1000ms
재시도 3차: 2000ms (1000ms × 2.0)
재시도 4차: 4000ms (2000ms × 2.0)
...
최대: 10000ms (max-interval)
```

**재시도 예외 타입**
- `RecoverableDataAccessException`
- `TransientDataAccessException`
- `TimeoutException`

### 5. 동기 vs 비동기 모드

| 항목 | 동기 모드 | 비동기 모드 |
|------|-----------|-------------|
| **성능** | ~500 TPS | ~10,000+ TPS |
| **안정성** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **블로킹** | Yes | No |
| **전송 보장** | 즉시 확인 | 콜백 확인 |
| **권장 환경** | 금융, 의료 | 대용량 이벤트 |

**설정**
```yaml
curve:
  kafka:
    async-mode: true  # 비동기
    async-timeout-ms: 5000
```

### 6. MDC 기반 Tags

**요청 시작 시 MDC 설정**
```java
@Component
public class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        try {
            MDC.put("tenant", extractTenant(request));
            MDC.put("region", "ap-northeast-2");
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

**자동으로 이벤트에 포함**
```json
{
  "metadata": {
    "tags": {
      "tenant": "company-001",
      "region": "ap-northeast-2"
    }
  }
}
```

---

## 📖 문서

- [CONFIGURATION.md](docs/CONFIGURATION.md) - 상세 설정 가이드
- [application.example.yml](application.example.yml) - 설정 예시
- [Architecture Decision Records (ADR)](#) - 설계 의도 (작성 예정)
