package com.project.curve.spring.test;

import com.project.curve.core.envelope.EventEnvelope;
import com.project.curve.core.payload.DomainEventPayload;
import com.project.curve.core.port.EventProducer;
import com.project.curve.core.type.EventSeverity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트용 EventProducer 구현체.
 * <p>
 * 실제 Kafka로 발행하지 않고 메모리에 이벤트를 저장하여 검증을 돕습니다.
 *
 * <h3>사용 예시</h3>
 * <pre>
 * @TestConfiguration
 * public class TestConfig {
 *     @Bean
 *     @Primary
 *     public EventProducer eventProducer() {
 *         return new MockEventProducer();
 *     }
 * }
 *
 * @Autowired
 * private MockEventProducer mockProducer;
 *
 * @Test
 * void test() {
 *     // ...
 *     assertThat(mockProducer.getEvents()).hasSize(1);
 * }
 * </pre>
 */
public class MockEventProducer implements EventProducer {

    private final List<Object> payloads = new CopyOnWriteArrayList<>();
    private final List<EventSeverity> severities = new CopyOnWriteArrayList<>();

    @Override
    public <T extends DomainEventPayload> void publish(T payload) {
        publish(payload, EventSeverity.INFO);
    }

    @Override
    public <T extends DomainEventPayload> void publish(T payload, EventSeverity severity) {
        payloads.add(payload);
        severities.add(severity);
    }

    public List<Object> getPayloads() {
        return Collections.unmodifiableList(payloads);
    }

    public List<EventSeverity> getSeverities() {
        return Collections.unmodifiableList(severities);
    }

    public void clear() {
        payloads.clear();
        severities.clear();
    }
}
