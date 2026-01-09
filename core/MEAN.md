## 🎯 event-core 설계 목표 

### event-core는 반드시 다음 조건을 만족

- Kafka / Spring / Boot에 전혀 의존하지 않음
- 이벤트 “표준 + 불변성”을 강제
- 테스트·확장·교체가 쉬움
- 나중에 Kafka 말고 다른 전송수단도 가능