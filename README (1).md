# Common Event Library

> Kafka에 종속되지 않는 **조직 표준 이벤트 발행 라이브러리**

**MSA 환경에서 이벤트를 '조직 자산'으로 관리하기 위한 공통 이벤트 라이브러리**

---

## 🎯 프로젝트 목표

- 비즈니스 서비스에서 **Kafka 의존성 제거**
- 이벤트 발행 시 **조직 표준 메타데이터 강제**
- 개인정보(PII), 대용량 이벤트를 **사전에 차단**
- 테스트 가능하고, 교체 가능한 이벤트 발행 구조 제공

---

## ❓ 왜 새로 만들었는가?

Spring Kafka, Spring Cloud Stream 등 훌륭한 라이브러리는 이미 존재
하지만 기업 환경에서는 다음과 같은 문제가 반복

- 이벤트마다 메타데이터 형식이 제각각
- 개인정보가 포함된 이벤트가 그대로 Kafka로 유출
- 대용량 payload로 Kafka 장애 발생
- “누가, 언제, 왜 보냈는지” 추적 불가

👉 이 라이브러리는  
**“어떻게 보내는가”가 아니라  
“무엇을, 어떤 규칙으로 보내는가”를 코드로 강제**합니다.

---

## 🧱 전체 구조

```
core/
 ├─ DomainEvent
 ├─ EventMetadata
 ├─ EventPublisher
 ├─ EventPolicy
 └─ EventPolicyValidator
 
spring/
 ├─annotation
 ├─aop
 ├─context
 ├─factory
 ├─payload
 ├─publisher
 ├─serde
 └─type

kafka/
 └─producer
 
 spring-boot-autoconfigure/
 ├─aop
 ├─context
 ├─envelope
 └─kafka
```

- 비즈니스 서비스 → `core`만 의존
- Kafka 관련 코드는 `kafka`에만 존재

---

## 📦 핵심 개념

### 1️⃣ DomainEvent (이벤트 계약)

```java
public interface DomainEvent {
    EventMetadata metadata();

    Object payload();
}
```

모든 이벤트는 반드시:

- 메타데이터
- 비즈니스 payload  
  를 함께 가져야 합니다.

---

### 2️⃣ EventMetadata (조직 표준 메타데이터)

```java
public record EventMetadata(
        String eventId,
        String eventType,
        String producer,
        Instant occurredAt,
        String traceId
) {
}
```

| 필드         | 목적        |
|------------|-----------|
| eventId    | 중복/재처리 추적 |
| eventType  | 이벤트 식별    |
| producer   | 발행 서비스    |
| occurredAt | 감사 로그     |
| traceId    | 분산 추적     |

👉 **메타데이터 없는 이벤트는 발행 불가**

---

### 3️⃣ EventPublisher (Kafka 추상화)

```java
public interface EventPublisher {
    void publish(DomainEvent event);
}
```

- 비즈니스 서비스는 Kafka를 전혀 모름
- 메시징 시스템 교체 가능

---

## 🔐 Event Policy (조직 규칙 강제)

이 라이브러리의 핵심 차별점은  
**이벤트를 보내기 전에 '정책 검증'을 수행한다는 점**입니다.

---

### 1️⃣ 정책 구조

```java
public interface EventPolicy {
    void validate(DomainEvent event);
}
```

```java
public interface EventPolicyValidator {
    void validate(DomainEvent event);
}
```

여러 정책을 조합하는 **Policy Chain 구조**를 사용합니다.

---

### 2️⃣ Payload Size 제한 정책

> 대용량 이벤트로 인한 Kafka 장애 방지

```yaml
event:
  policy:
    payload:
      max-bytes: 1048576 # 1MB
```

- payload 직렬화 후 byte size 검사
- 초과 시 이벤트 발행 차단

---

### 3️⃣ PII(개인정보) 차단 정책

> 개인정보 이벤트의 무분별한 외부 전파 방지

#### 어노테이션 정의

```java
@PII           // 필드 레벨
@ContainsPII   // payload 전체
```

#### 예시

```java
public record UserCreatedPayload(
        String userId,
        @PII String email
) {
}
```

```java

@ContainsPII
public record IdentityPayload(
        String name,
        String residentNumber
) {
}
```

- 기본 설정: **PII 이벤트 차단**
- 설정으로만 허용 가능

---

## 🚀 Kafka 구현체 (Infra Layer)

```java

@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventPolicyValidator policyValidator;

    @Override
    public void publish(DomainEvent event) {
        policyValidator.validate(event);
        kafkaTemplate.send(
                "event-topic",
                event.metadata().eventId(),
                event
        );
    }
}
```

- Kafka는 **정책 검증 이후에만 호출**
- 정책과 전송 로직 완전 분리

---

## 🧪 테스트 전략

- 정책 검증: **Kafka 없는 단위 테스트**
- Kafka 연동: Embedded Kafka / Testcontainers

```java
assertThrows(
        EventPolicyViolationException .class,
    () ->policy.

validate(event)
);
```

👉 **테스트 안정성 + 속도 확보**

---

## 🎤 면접에서 이렇게 설명합니다

> “이벤트를 기술적으로 보내는 것이 아니라  
> 조직 표준과 보안 정책을 코드로 강제하는 라이브러리를 만들었습니다.  
> Kafka 의존성은 완전히 분리했고,  
> 정책 위반 이벤트는 발행 이전에 차단됩니다.”

---

## 🧭 한계와 다음 확장 방향

### 현재 범위 (의도적 제한)

- Consumer / 플랫폼 기능 제외
- 단일 Kafka 구현체만 제공

### 다음 확장 단계

- Audit 대상 이벤트 전용 토픽 라우팅
- Event Versioning (`v1`, `v2`)
- Outbox Pattern 연동
- Schema Registry 연계

---

## 🏁 정리

이 프로젝트는:

- Kafka wrapper ❌
- 조직 표준 이벤트 라이브러리 ⭕

를 목표로 합니다.

**“이벤트 품질을 코드로 보장한다”**  
이것이 이 라이브러리의 존재 이유입니다.
