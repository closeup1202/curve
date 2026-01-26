# 'Curve' 라이브러리 핵심 기술 지식 요약 (면접 대비)

AI의 도움을 받아 구현했더라도, 그 결과물에 담긴 기술적 의사결정과 핵심 원리를 본인의 것으로 소화한다면 면접에서 충분히 방어할 수 있고, 오히려 "학습 능력이 뛰어난 개발자"로 평가받을 수 있습니다.

면접관이 이 프로젝트를 보고 **반드시 물어볼 핵심 질문**과, 그에 대해 **고급 개발자로서 답변하기 위해 꼭 알아야 할 지식**을 5가지 핵심 테마로 정리했습니다.

---

## 1. 데이터 정합성: "Dual-Write 문제"와 "Transactional Outbox 패턴"

이 라이브러리의 존재 이유이자 가장 중요한 핵심입니다.

* **핵심 개념**:
    * **Dual-Write 문제**: DB 저장과 Kafka 발행은 서로 다른 시스템(트랜잭션)이라서, 하나만 성공하고 하나는 실패하는 상황이 발생할 수 있습니다. (예: 주문은 저장됐는데 Kafka 이벤트는
      발행 안 됨 → 배송 시스템이 모름)
    * **해결책 (Outbox 패턴)**: Kafka에 바로 보내지 않고, **"보낼 메시지"를 DB 테이블(Outbox)에 먼저 저장**합니다. 이때 비즈니스 로직(주문 저장)과 Outbox 저장을 **하나의
      DB 트랜잭션(@Transactional)**으로 묶습니다. 이러면 둘 다 성공하거나 둘 다 실패하므로 원자성(Atomicity)이 보장됩니다.

* **면접 예상 질문**:
    * Q: "왜 Kafka에 바로 안 보내고 DB에 저장했다가 보내나요?"
    * A: "DB 트랜잭션과 메시지 발행의 원자성을 보장하기 위해서입니다. Kafka 발행 중에 네트워크 오류가 나면 DB 트랜잭션도 롤백되어야 하는데, 비동기나 외부 시스템 호출은 DB 트랜잭션 범위 밖이라
      제어가 안 됩니다. 그래서 Outbox 패턴을 썼습니다."

* **코드 확인 포인트**:
    * `spring/src/main/java/com/project/curve/spring/audit/aop/OutboxEventSaver.java`
    * `core/src/main/java/com/project/curve/core/outbox/OutboxEvent.java`
    * `sample/src/main/java/com/example/orderservice/service/OrderService.java` (특히 `createOrderWithOutbox` 메서드의
      `@PublishEvent` 설정)

---

## 2. 동시성 제어: "SKIP LOCKED" (고급 개발자 지식)

Outbox 테이블을 폴링(Polling)할 때, 서버가 여러 대라면 **같은 이벤트를 중복해서 가져가서 두 번 발행**할 위험이 있습니다.

* **핵심 개념**:
    * **비관적 락 (Pessimistic Lock)**: 데이터를 읽을 때 락을 걸어서 남들이 못 건드리게 함.
    * **SKIP LOCKED**: "락이 걸린 행은 대기하지 말고 건너뛰고, 락이 안 걸린 다음 행을 가져와라."
    * **효과**: 여러 서버가 동시에 DB를 조회해도, 서로 다른 이벤트를 가져가게 되어 **동시 처리량(Throughput)이 높아지고 중복 발행이 방지**됩니다.

* **면접 예상 질문**:
    * Q: "서버가 10대면 Outbox 테이블 조회할 때 경합(Contention)이 심할 텐데요? 중복 발행 문제는 어떻게 해결했나요?"
    * A: "그래서 `SELECT ... FOR UPDATE SKIP LOCKED` 쿼리를 사용했습니다. 이렇게 하면 이미 다른 인스턴스가 처리 중인 행은 건너뛰고 가져오기 때문에 락 대기 없이 병렬 처리가
      가능하며, 중복 발행 위험을 줄일 수 있습니다."

* **코드 확인 포인트**:
    * `spring/src/main/java/com/project/curve/spring/outbox/persistence/jpa/repository/OutboxEventJpaRepository.java` (
      `findByStatusForUpdateSkipLocked` 메서드)
    * `spring/src/main/java/com/project/curve/spring/outbox/publisher/OutboxEventPublisher.java` (이 메서드에서
      `findPendingForProcessing`을 호출)

---

## 3. 아키텍처: "Hexagonal Architecture" (의존성 역전 원칙)

이 프로젝트의 구조적 특징이자, 확장성과 유지보수성을 높이는 핵심입니다.

* **핵심 개념**:
    * **의존성 규칙**: `Core`는 아무것도 의존하지 않는다. `Spring`, `Kafka` 같은 외부 기술이 `Core`를 의존한다. (안쪽으로 갈수록 추상화, 바깥쪽으로 갈수록 구체화)
    * **Port & Adapter**: `Core`는 인터페이스(Port, 예: `EventProducer`)만 정의하고, 실제 구현(Adapter, 예: `KafkaEventProducer`)은 외부
      모듈에서 주입받는다.

* **면접 예상 질문**:
    * Q: "왜 굳이 모듈을 Core, Spring, Kafka로 나눴나요? 그냥 하나로 짜면 편하잖아요?"
    * A: "비즈니스 로직(이벤트 도메인)을 기술 종속성으로부터 보호하기 위해서입니다. 만약 나중에 Kafka를 RabbitMQ로 바꾸더라도 `Core` 모듈은 수정할 필요 없이 `Adapter`만 갈아 끼우면
      됩니다. 또한, `Core` 모듈은 순수 Java로만 구성되어 있어 단위 테스트가 매우 빠르고 용이합니다."

* **코드 확인 포인트**:
    * `core/build.gradle` (의존성이 거의 없음)
    * `core/src/main/java/com/project/curve/core/port/EventProducer.java` (인터페이스)
    * `kafka/src/main/java/com/project/curve/kafka/producer/KafkaEventProducer.java` (구현체)

---

## 4. 관찰성(Observability): "MDC 컨텍스트 전파"

분산 시스템 및 비동기 프로그래밍에서 로그 추적을 위한 필수 요소입니다.

* **핵심 개념**:
    * **ThreadLocal**: Java에서 요청별 데이터(Trace ID 등)를 저장하는 공간인데, 스레드가 바뀌면(비동기 실행 시) 이 값이 사라집니다.
    * **MDC 전파**: 비동기 작업을 시작하기 직전에 현재 스레드의 MDC 정보를 복사(Copy)해서, 새로운 스레드에 붙여넣기(Paste) 해주는 작업입니다. 이를 통해 비동기 호출 간에도 동일한
      Trace ID로 로그를 추적할 수 있습니다.

* **면접 예상 질문**:
    * Q: "비동기로 이벤트를 발행하면 로그 추적(Trace ID)이 끊길 텐데 어떻게 해결했나요?"
    * A: "`KafkaEventProducer`에서 비동기(`CompletableFuture`)로 넘어가기 전에 `MDC.getCopyOfContextMap()`으로 문맥을 캡처하고, 실행되는 스레드 내부에서
      `MDC.setContextMap()`으로 복원해주는 로직을 직접 구현했습니다. 이를 통해 비동기 작업에서도 일관된 로그 추적을 보장합니다."

* **코드 확인 포인트**:
    * `kafka/src/main/java/com/project/curve/kafka/producer/KafkaEventProducer.java` (`sendAsync` 및 `dispatchToDlq` 메서드
      내부)
    * `spring/src/main/java/com/project/curve/spring/context/correlation/MdcCorrelationContextProvider.java` (MDC를 활용하는
      ContextProvider)

---

## 5. Spring 내부 원리: "AOP와 SpEL"

Spring 기반 라이브러리 개발자라면 반드시 이해해야 할 Spring의 핵심 기능입니다.

* **핵심 개념**:
    * **AOP (Aspect Oriented Programming)**: `@PublishEvent`가 붙은 메서드를 가로채서, 메서드 실행 전후에 이벤트 발행 로직을 끼워 넣는 기술. 비즈니스 로직과 인프라
      로직(이벤트 발행)을 분리하여 코드의 응집도를 높입니다.
    * **SpEL (Spring Expression Language)**: 문자열로 된 표현식(`#result.orderId`)을 런타임에 해석해서 실제 객체의 값을 꺼내오는 기술. 이를 통해 어노테이션만으로
      유연하게 데이터를 추출할 수 있습니다.

* **면접 예상 질문**:
    * Q: "어노테이션만 붙였는데 어떻게 이벤트가 발행되나요? 내부 동작 원리를 설명해 주세요."
    * A: "Spring AOP를 사용했습니다. `PublishEventAspect`가 `@PublishEvent` 어노테이션이 붙은 메서드의 실행을 가로채고, 메서드의 반환값이나 파라미터를 가져와서 이벤트를
      생성합니다. 이때 `SpelExpressionParser`를 활용하여 어노테이션에 정의된 SpEL 표현식을 해석하여 동적으로 페이로드를 추출합니다."
    * Q: "SpEL을 사용한 이유는 무엇인가요?"
    * A: "이벤트 페이로드를 유연하게 구성하기 위해서입니다. 메서드의 반환값 전체를 사용하거나, 특정 파라미터의 특정 필드만 추출하는 등 다양한 요구사항에 대응하기 위해 SpEL을 도입했습니다."

* **코드 확인 포인트**:
    * `spring/src/main/java/com/project/curve/spring/audit/annotation/PublishEvent.java` (어노테이션 정의)
    * `spring/src/main/java/com/project/curve/spring/audit/aop/PublishEventAspect.java` (AOP 로직 및 SpEL 파싱)

---

### 🚀 학습 및 면접 대비 팁

1. **개념 완벽 이해**: 각 키워드의 정의, 왜 필요한지, 어떤 문제를 해결하는지 명확히 이해하세요.
2. **코드와 매칭**: 해당 개념이 실제 코드의 어느 부분에서 어떻게 구현되었는지 직접 찾아보고 분석하세요.
3. **말로 설명 연습**: 각 질문에 대해 막힘없이 설명할 수 있도록 소리 내어 연습하세요. 특히 "왜(Why)"와 "어떻게(How)"에 집중하세요.
4. **한계점과 대안**: 각 기술의 장단점, 그리고 현재 구현의 한계점(예: Polling 방식의 Outbox)과 이를 개선하기 위한 대안(예: CDC)까지 함께 설명할 수 있다면 매우 높은 점수를 받을 수
   있습니다.

---