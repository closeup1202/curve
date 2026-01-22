package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSource 테스트")
class EventSourceTest {

    @Test
    @DisplayName("정상적인 EventSource 생성 - 모든 필드 유효")
    void createValidEventSource() {
        // given
        String service = "order-service";
        String environment = "production";
        String instanceId = "instance-1";
        String host = "192.168.1.1";
        String version = "1.0.0";

        // when
        EventSource eventSource = new EventSource(service, environment, instanceId, host, version);

        // then
        assertNotNull(eventSource);
        assertEquals(service, eventSource.service());
        assertEquals(environment, eventSource.environment());
        assertEquals(instanceId, eventSource.instanceId());
        assertEquals(host, eventSource.host());
        assertEquals(version, eventSource.version());
    }

    @Test
    @DisplayName("EventSource 생성 실패 - service가 null")
    void createEventSourceWithNullService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource(null, "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventSource 생성 실패 - service가 빈 문자열")
    void createEventSourceWithEmptyService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource("", "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventSource 생성 실패 - service가 공백만 있는 문자열")
    void createEventSourceWithBlankService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource("   ", "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }
}
