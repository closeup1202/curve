Spring 기반 마이크로서비스에서 발생하는 도메인 이벤트를 Kafka로 표준화 수집하여 

보안 감사 로그·이력 추적·알림을 일관되게 처리하는 공통 이벤트 라이브러리 중심 사이드 프로젝트

## 1. pain point 

|문제|	해결|
|-|-|
| 이벤트 구조 제각각|	✅ 공통 Event Schema|
|비즈니스 코드 오염|	✅ AOP / Publisher 추상화|
|감사 로그 사후 대응|	✅ 중앙 수집 + 불변 이벤트|
|Kafka 남용|	✅ 규칙 있는 Kafka 사용|

## 2. MVP 범위 정의 (라이브러리 중심)
### 1️⃣ MVP의 핵심 원칙

#### ❌ “다 되는 플랫폼” 만들지 않는다
#### ✅ “회사에 바로 붙일 수 있는 공통 라이브러리”를 만든다

즉, 이벤트를 ‘올바르게 발행’하는 데만 집중
- UI ❌
- 대시보드 ❌
- 알림 시스템 풀세트 ❌

### 2️⃣ MVP에 포함할 것 / 제외할 것

✅ 포함 

| 구분       | 내용                                |
| -------- | --------------------------------- |
| 이벤트 표준   | 공통 Event Envelope                 |
| 발행 방식    | Annotation / Publisher 기반         |
| Kafka 연동 | Producer 추상화                      |
| 메타데이터    | traceId, actor, source, timestamp |
| 직렬화      | JSON (schema version 포함)          |
| 실패 처리    | retry / DLQ 기본 전략                 |
| 확장 포인트   | Consumer/Storage/Notifier 분리      |

❌ 제외

| 항목         | 이유         |
| ---------- | ---------- |
| UI 대시보드    | 플랫폼 단계     |
| 이벤트 조회 API | 컨슈머 서비스 역할 |
| 실시간 알림     | 후속 확장      |
| Stream 처리  | 오버스펙       |
| 멀티 브로커     | MVP에 불필요   |

### 3️⃣ MVP 기능 상세 정의
🔹 1. Event Envelope (핵심 중의 핵심)

```json
{
  "eventId": "uuid",
  "eventType": "USER_LOGIN_SUCCESS",
  "occurredAt": "2026-01-04T15:30:00Z",
  "source": {
    "service": "auth-service",
    "instanceId": "auth-1"
  },
  "actor": {
    "userId": "12345",
    "authType": "GPKI"
  },
  "data": {
    "ip": "10.0.0.1"
  },
  "trace": {
    "traceId": "abc",
    "spanId": "def"
  },
  "version": 1
}

```
🔹 2. 이벤트 발행 API (비즈니스 코드 보호)

- Annotation 방식

```java
@AuditEvent(type = USER_LOGIN_SUCCESS)
public void login(...) {
// 비즈니스 로직만
}
```
- ✔ 비즈니스 코드가 Kafka를 모른다
- ✔ 테스트 가능
- ✔ 정책 변경에 강함

🔹 3. Producer 추상화
```java
interface EventProducer {
    void send(EventEnvelope event);
}
```

🔹 4. 설정 방식 (실무 친화)
```yaml
event:
  enabled: true
  kafka:
    topic-prefix: security
    acks: all
    retries: 3
```

### 4️⃣ MVP 산출물 목록

📦 코드

- event-core
- event-kafka
- event-spring-boot-starter

📄 문서

- README.md
- Event Schema 문서
- “왜 이렇게 설계했는지” 설계 의도

🧪 예제

- sample-auth-service
- sample-business-service
