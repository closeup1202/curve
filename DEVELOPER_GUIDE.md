# Curve Library Internals: The Architect's Guide

이 문서는 Curve 라이브러리의 **기획 의도**가 실제 **코드 레벨**에서 어떻게 구현되었는지, 그리고 그 과정에서 발생한 **기술적 난제**들을 어떻게 해결했는지 설명하는 심층 분석 문서입니다.

단순한 사용법이 아닌, **"Why & How"**에 집중합니다.

---

## 1. Core Philosophy & Design Decisions

### 1.1. Hexagonal Architecture (Ports & Adapters)
*   **의도**: 비즈니스 로직(`core`)이 특정 프레임워크(Spring)나 인프라(Kafka)에 종속되지 않게 하여, 기술 스택 변경에 유연하게 대처하고 테스트 용이성을 확보한다.
*   **구현**:
    *   `core` 모듈은 `java.base` 외에 어떤 의존성도 가지지 않습니다.
    *   `EventProducer` (Port) -> `KafkaEventProducer` (Adapter)
    *   `OutboxEventRepository` (Port) -> `JpaOutboxEventRepositoryAdapter` / `JdbcOutboxEventRepository` (Adapter)
    *   **Dependency Rule**: `spring` -> `core`, `kafka` -> `core` 방향으로만 의존성이 흐릅니다.

### 1.2. EventEnvelope: The Immutable Truth
*   **의도**: 분산 시스템에서 이벤트는 그 자체로 **완전한 문맥(Context)**을 가져야 한다.
*   **구현**:
    *   Java `record`를 사용하여 **불변성(Immutability)**을 강제했습니다.
    *   **Type Erasure 회피**: `EventEnvelope<T>` 제네릭을 사용하여 페이로드 타입을 보존하되, 런타임에는 `DomainEventPayload` 인터페이스를 통해 다형성을 처리합니다.
    *   **Snowflake ID**: `EventId`는 시간순 정렬이 가능하도록 설계되어, 분산 환경에서도 대략적인 순서를 보장합니다.

---

## 2. Deep Dive: Transactional Outbox Pattern

가장 복잡하고 중요한 부분입니다. **"DB 트랜잭션과 메시지 발행의 원자성"**을 어떻게 보장하고 성능을 최적화했는지 설명합니다.

### 2.1. 저장 (Saving) - `OutboxEventSaver`
*   **Transaction Participation**: `OutboxEventSaver.save()`는 호출자의 `@Transactional` 컨텍스트에 참여합니다. 별도의 트랜잭션을 열지 않으므로, 비즈니스 로직이 롤백되면 이벤트 저장도 함께 롤백됩니다.
*   **Payload Serialization**: 저장은 동기적으로 일어나므로, 직렬화 비용이 비즈니스 로직에 포함됩니다. 따라서 `ObjectMapper` 설정이 성능에 중요합니다.

### 2.2. 발행 (Publishing) - `OutboxEventPublisher`
단순한 Polling이 아닙니다. **동시성 제어**와 **부하 분산**이 핵심입니다.

#### A. Concurrency Control: `SKIP LOCKED`
*   **문제**: 여러 서버(인스턴스)가 동시에 뜬 경우, 동일한 `PENDING` 이벤트를 중복해서 가져와 중복 발행할 위험이 있습니다.
*   **해결**: `FOR UPDATE SKIP LOCKED` 구문을 사용합니다.
    *   **JPA**: `OutboxEventJpaRepository`에서 `@Lock(LockModeType.PESSIMISTIC_WRITE)`와 힌트를 조합하여 구현.
    *   **JDBC**: `JdbcOutboxEventRepository`에서 DB 벤더(`MySQL`, `PostgreSQL`, `Oracle`)를 감지하여 쿼리에 `FOR UPDATE SKIP LOCKED`를 동적으로 붙입니다.
    *   **SQL Server**: 힌트 문법이 다르므로(`WITH (READPAST, UPDLOCK)`), 현재는 일반 조회로 fallback 처리되어 있습니다 (추후 개선 포인트).

#### B. Backoff Strategy (지수 백오프)
*   **로직**: 실패 시 `retry_count`만 늘리는 게 아니라, `next_retry_at`을 미래 시점으로 설정합니다.
*   **쿼리 최적화**: 조회 쿼리에 `AND next_retry_at <= :now` 조건이 포함되어 있어, 백오프 중인 이벤트는 아예 DB에서 조회되지 않습니다. 이는 불필요한 폴링 부하를 줄입니다.

#### C. Circuit Breaker (State Machine)
*   **구현**: `AtomicInteger`와 `volatile` 변수를 사용한 경량 상태 머신입니다.
    *   `CLOSED` (정상) -> 실패 누적 -> `OPEN` (차단) -> 시간 경과 -> `HALF-OPEN` (테스트) -> 성공 -> `CLOSED`
*   **특징**: 외부 라이브러리(Resilience4j 등) 없이 구현하여 의존성을 줄였습니다. `OPEN` 상태에서는 DB 조회조차 하지 않아 DB를 보호합니다.

---

## 3. Deep Dive: PII (개인정보 보호) - Jackson Internals

비즈니스 로직에 침투하지 않고 **투명하게(Transparently)** 개인정보를 처리하기 위해 Jackson의 내부를 해킹했습니다.

### 3.1. `PiiBeanSerializerModifier`
*   **역할**: Jackson이 Java 객체를 JSON으로 변환할 때, 각 필드를 쓰는 `BeanPropertyWriter`를 가로챕니다.
*   **동작**:
    1.  `changeProperties()` 메서드에서 모든 필드를 순회합니다.
    2.  `@PiiField` 어노테이션이 붙은 필드를 찾습니다.
    3.  해당 필드의 Writer를 커스텀 `PiiPropertyWriter`로 **교체(Replace)**합니다.

### 3.2. `PiiPropertyWriter`
*   **역할**: 실제 직렬화 시점에 값을 가로채서 변조합니다.
*   **동작**:
    1.  `serializeAsField()`가 호출될 때 원본 값을 가져옵니다.
    2.  `PiiProcessorRegistry`를 통해 전략(`MASK`, `ENCRYPT` 등)에 맞는 처리기를 찾습니다.
    3.  변조된 값을 JSON Generator에 씁니다.
*   **성능**: 리플렉션 대신 Jackson의 내부 API를 사용하므로 오버헤드를 최소화했습니다.

---

## 4. Deep Dive: Resilience & Safety Nets

Kafka가 죽었을 때 데이터 유실을 막기 위한 **3중 방어선**의 구현 디테일입니다.

### 4.1. Level 1: Retry (In-Memory)
*   `RetryTemplate`을 사용합니다. 이는 스레드를 차단(Blocking)하므로, `asyncMode=true`일 때도 재시도 중에는 해당 작업자 스레드가 묶입니다.

### 4.2. Level 2: DLQ (Dead Letter Queue)
*   **비동기 분리**: Kafka 전송 콜백(`whenComplete`)은 Kafka Producer의 I/O 스레드에서 실행될 수 있습니다. 여기서 다시 Kafka로 동기 전송(`send().get()`)을 하면 데드락이나 성능 저하가 발생할 수 있습니다.
*   **해결**: `curveDlqExecutor`라는 별도의 스레드 풀을 사용하여 DLQ 전송을 격리했습니다.

### 4.3. Level 3: Local File Backup (The Last Resort)
*   **문제**: 파일 시스템에 민감한 데이터(이벤트)를 쓸 때 보안 문제가 발생합니다.
*   **해결**: OS별 파일 권한 API(`java.nio.file.attribute`)를 사용합니다.
    *   **POSIX (Linux/Mac)**: `Files.setPosixFilePermissions(path, "rw-------")` -> 소유자만 읽기/쓰기 가능 (600).
    *   **Windows**: `AclFileAttributeView`를 사용하여 현재 사용자(`UserPrincipal`)에게만 접근 권한을 부여하는 ACL(Access Control List)을 새로 작성합니다.
*   **Fail-Safe**: 운영 환경(`isProduction=true`)에서는 권한 설정 실패 시 예외를 던져 저장을 막고(보안 우선), 개발 환경에서는 경고만 남깁니다.

---

## 5. Deep Dive: AOP & SpEL

`@PublishEvent`가 동작하는 방식입니다.

### 5.1. SpEL Context Binding
*   **변수 바인딩**: `StandardEvaluationContext`에 다음 변수들을 바인딩합니다.
    *   `#result`: 메서드 반환값.
    *   `#args`: 인자 배열.
    *   `#p0`, `#p1`...: 인자 인덱스.
    *   `parameterNames`: 컴파일 시 `-parameters` 옵션이 켜져 있다면 실제 변수명으로도 접근 가능.
*   **성능**: `SpelExpressionParser`는 스레드 안전하므로 재사용하지만, `Expression` 파싱 비용이 있습니다. (현재 캐싱은 적용되지 않음 -> 추후 최적화 포인트)

### 5.2. Transaction Context
*   `@PublishEvent`는 트랜잭션 관리를 직접 하지 않습니다.
*   `outbox=true`일 때, 호출하는 메서드에 이미 트랜잭션이 걸려 있어야(`@Transactional`) 같은 트랜잭션에 묶입니다. 만약 트랜잭션이 없다면 `OutboxEventRepository`의 `save` 메서드가 독자적인 트랜잭션을 엽니다(Repository 구현체에 `@Transactional`이 걸려 있음). 이 경우 원자성이 깨질 수 있으므로 주의가 필요합니다.

---

## 6. Infrastructure: Graceful Shutdown

애플리케이션이 종료될 때 데이터 유실을 막기 위한 장치입니다.

### 6.1. `GracefulExecutorService`
*   **문제**: Spring Context가 닫힐 때, DLQ 전송을 위해 대기 중이거나 실행 중인 스레드가 강제 종료되면 이벤트가 유실됩니다.
*   **해결**: `ExecutorService`를 래핑한 데코레이터를 만들었습니다.
    *   `shutdown()` 훅에서 `executor.shutdown()`을 호출하여 새 작업 수락을 거부합니다.
    *   `executor.awaitTermination()`으로 기존 작업이 끝날 때까지 설정된 시간(`dlqExecutorShutdownTimeoutSeconds`)만큼 대기합니다.
    *   이 로직은 `CurveKafkaAutoConfiguration`의 빈 소멸 메서드(`destroyMethod`)에 등록되어 있습니다.

---

## 7. Summary of Key Classes

| 클래스 | 역할 및 핵심 구현 |
| :--- | :--- |
| `EventEnvelope` | 불변 이벤트 래퍼. 제네릭 타입 소거를 고려한 설계. |
| `KafkaEventProducer` | 3단계 방어 로직(Retry->DLQ->File) 및 OS별 파일 보안 처리 구현. |
| `OutboxEventPublisher` | `SKIP LOCKED`를 이용한 동시성 제어, 지수 백오프, 서킷 브레이커 구현. |
| `PiiBeanSerializerModifier` | Jackson 내부를 해킹하여 직렬화 시점에 필드를 가로채고 변조. |
| `PublishEventAspect` | AOP 및 SpEL 파싱을 통해 비즈니스 로직과 이벤트 발행을 분리. |
| `GracefulExecutorService` | 애플리케이션 종료 시 작업 유실 방지를 위한 스레드 풀 래퍼. |

이 문서는 Curve 라이브러리의 **"설계도"**이자 **"해설서"**입니다. 코드를 수정하거나 확장할 때 이 문서에 담긴 의도(Context)를 참고하시기 바랍니다.
