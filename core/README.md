## 🎯 core 모듈 설계 목표 

### core는 반드시 다음 조건을 만족

- Kafka / Spring / Boot에 전혀 의존하지 않음
- 이벤트 “표준 + 불변성”을 강제
- 테스트·확장·교체가 쉬움
- 나중에 Kafka 말고 다른 전송수단도 가능

## 🧠 Core Design Principles

### 1. Core is Pure

core 모듈은 프레임워크 독립적이어야 한다.

- ❌ Spring
- ❌ Kafka
- ❌ MDC / SecurityContext
- ❌ 시간/ID의 구체 구현

Core는 오직 다음만 포함한다:

- 이벤트 도메인 모델
- 불변 값 객체
- 최소한의 구조 검증
- 외부와의 계약(port 인터페이스)

이를 통해 core는 어떤 실행 환경에서도 재사용 가능하다.

### 2. Event is a Domain Object

- 이벤트는 단순한 메시지가 아니라 불변 도메인 객체로 취급한다.
- 모든 이벤트는 EventEnvelope로 표현된다.
- 생성 이후 절대 변경되지 않는다.
- 이벤트의 구조는 코드로 강제된다.

EventEnvelope<T extends DomainEventPayload>

### 3. Strong Typing over String

- Core는 가능한 한 문자열 대신 타입을 사용한다.
- EventType → interface / enum
- EventSchema → name + version
- EventId → value object

이는:

오타 방지
IDE 자동 완성
장기 유지보수성

### 4. Stable Envelope, Evolving Payload

이벤트 구조는 두 계층으로 나뉜다.

Stable

specVersion

eventId

eventType

occurredAt / publishedAt

metadata

Evolving

payload

schema.version

Envelope는 가능한 한 변경되지 않으며,
비즈니스 데이터는 schema version을 통해 진화한다.

### 5. Explicit Creation Rules

이벤트 생성 규칙은 Factory를 통해서만 허용된다.

ID 생성

시간 결정

기본 규칙 적용

EventEnvelopeFactory.create(...)


이를 통해:

생성 규칙이 분산되지 않고

테스트와 환경별 제어가 가능하다.

### 6. Minimal Validation in Core

Core의 검증은 **형태(structure)**만 검증

(1) 검증 대상:

- 필수 필드 존재 여부
- 시간 순서 일관성

(2) 검증 제외:

- 비즈니스 정책
- 환경별 요구사항
- 보안/감사 규칙

이런 검증은 event-spring 또는 consumer의 책임

### 7. Metadata is Context, Not Logic

EventMetadata는 문맥(Context) 정보일 뿐,
core 로직에서 해석하지 않음

- source
- actor
- trace
- tags

이는 확장성과 책임 분리를 위한 설계

✨ core는 이벤트를 표준화된 불변 도메인 객체로 정의하고, 환경과 전송 방식의 변화에도 흔들리지 않는 기반을 제공한다.