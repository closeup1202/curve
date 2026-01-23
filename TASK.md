# Curve 라이브러리 개선 로드맵

Curve 라이브러리의 안정성, 범용성, 성능을 개선하기 위한 단계별 작업 목록입니다.

---

## 🚀 Step 1. SpEL 런타임 안정성 개선 (난이도: 하)

가장 적은 노력으로 사용자 경험(DX)을 개선할 수 있는 작업입니다. 아키텍처 변경 없이 `PublishEventAspect` 내부 로직만 수정하면 됩니다.

### 📋 작업 내용
- [ ] **SpEL 예외 처리 강화**
    - `PublishEventAspect` 내 `extractPayload` 메서드에 `try-catch` 블록 추가
    - SpEL 파싱/실행 실패 시 `EventPublishException`을 던지지 않고 Fallback 전략 구현
    - Fallback 예시: `null` 반환, 혹은 전체 인자 맵 반환, 경고 로그 출력
- [ ] **(선택) 시작 시점 유효성 검사**
    - 애플리케이션 시작 시점(`@PostConstruct`)에 `@PublishEvent`가 붙은 메서드들을 스캔
    - SpEL 문법 오류를 미리 체크하는 `Validator` 빈 추가

### ⏱ 예상 소요 시간
- 2~4시간

### 💡 기대 효과
- 런타임에 SpEL 오타 등으로 인해 비즈니스 로직이 중단되는 것을 방지
- 개발 단계에서 설정 오류를 빠르게 파악 가능

---

## 🚀 Step 2. JPA 의존성 분리 및 JDBC 구현 (난이도: 중)

현재 `curve-spring-boot-starter`가 JPA를 강제하는 구조를 개선하는 작업입니다. 구조적인 리팩토링이 필요합니다.

### 📋 작업 내용
- [ ] **모듈 분리**
    - `spring` 모듈 내 `spring/src/main/java/com/project/curve/spring/outbox/persistence` (JPA 엔티티/리포지토리)를 별도 패키지/모듈로 격리
- [ ] **JDBC 구현체 추가**
    - `JdbcTemplate`을 사용하는 `JdbcOutboxEventRepository` 구현
    - JPA 없이 동작 가능하도록 SQL 쿼리 직접 작성
- [ ] **AutoConfiguration 수정**
    - `ConditionalOnClass`를 사용하여 JPA가 있으면 JPA 구현체를, 없으면 JDBC 구현체를 등록하도록 설정 변경

### ⏱ 예상 소요 시간
- 1~2일

### 💡 기대 효과
- MyBatis, jOOQ 등을 사용하는 팀에서도 라이브러리 도입 가능 (범용성 확대)
- 라이브러리 의존성 경량화

---

## 🚀 Step 3. Schema Registry 연동 (난이도: 상)

대규모 트래픽 처리를 위한 직렬화 최적화 작업입니다. 외부 시스템(Confluent Schema Registry)과의 통신 및 Avro/Protobuf 변환 로직이 필요합니다.

### 📋 작업 내용
- [ ] **의존성 추가**
    - Avro, Confluent Serializer 등 관련 라이브러리 추가
- [ ] **Serializer 구현**
    - `AvroEventSerializer` 구현
    - **난관:** `EventEnvelope<T>`는 제네릭 타입이므로 Avro 스키마 매핑 전략 수립 필요
        - 방법 1: 제네릭 객체를 Avro Record로 동적 매핑
        - 방법 2: Envelope용 공통 Avro 스키마(.avsc) 정의
- [ ] **테스트 환경 구축**
    - Testcontainers를 이용해 Schema Registry 컨테이너를 띄워 통합 테스트 작성

### ⏱ 예상 소요 시간
- 3~5일 이상

### 💡 기대 효과
- JSON 대비 메시지 크기 감소 및 처리량 증대
- 스키마 호환성 강제 (Producer/Consumer 간 계약 보장)
