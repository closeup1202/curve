package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventSource test")
class EventSourceTest {

    @Test
    @DisplayName("Create EventSource with all valid fields")
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
    @DisplayName("EventSource creation fails when service is null")
    void createEventSourceWithNullService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource(null, "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventSource creation fails when service is empty string")
    void createEventSourceWithEmptyService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource("", "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    @DisplayName("EventSource creation fails when service is blank string")
    void createEventSourceWithBlankService_shouldThrowException() {
        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new EventSource("   ", "prod", "inst-1", "host", "1.0.0")
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    @DisplayName("Create EventSource with Event Chain")
    void createEventSourceWithEventChain() {
        // given
        String service = "order-service";
        String environment = "prod";
        String instanceId = "instance-1";
        String host = "localhost";
        String version = "1.0.0";
        String correlationId = "corr-123";
        String causationId = "evt-000";
        String rootEventId = "evt-root";

        // when
        EventSource eventSource = new EventSource(
                service, environment, instanceId, host, version,
                correlationId, causationId, rootEventId
        );

        // then
        assertNotNull(eventSource);
        assertEquals(correlationId, eventSource.correlationId());
        assertEquals(causationId, eventSource.causationId());
        assertEquals(rootEventId, eventSource.rootEventId());
    }

    @Test
    @DisplayName("hasEventChain test - Event Chain exists")
    void testHasEventChain_true() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "evt-000", "evt-root"
        );

        // when
        boolean hasChain = eventSource.hasEventChain();

        // then
        assertTrue(hasChain);
    }

    @Test
    @DisplayName("hasEventChain test - Event Chain is absent (null)")
    void testHasEventChain_false_null() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0"
        );

        // when
        boolean hasChain = eventSource.hasEventChain();

        // then
        assertFalse(hasChain);
    }

    @Test
    @DisplayName("hasEventChain test - Event Chain is absent (empty string)")
    void testHasEventChain_false_empty() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "", null, null
        );

        // when
        boolean hasChain = eventSource.hasEventChain();

        // then
        assertFalse(hasChain);
    }

    @Test
    @DisplayName("hasEventChain test - Event Chain is absent (blank)")
    void testHasEventChain_false_blank() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "   ", null, null
        );

        // when
        boolean hasChain = eventSource.hasEventChain();

        // then
        assertFalse(hasChain);
    }

    @Test
    @DisplayName("isRootEvent test - Root Event (causationId is null)")
    void testIsRootEvent_true_nullCausation() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", null, "evt-root"
        );

        // when
        boolean isRoot = eventSource.isRootEvent();

        // then
        assertTrue(isRoot);
    }

    @Test
    @DisplayName("isRootEvent test - Root Event (causationId is empty string)")
    void testIsRootEvent_true_emptyCausation() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "", "evt-root"
        );

        // when
        boolean isRoot = eventSource.isRootEvent();

        // then
        assertTrue(isRoot);
    }

    @Test
    @DisplayName("isRootEvent test - Root Event (causationId is blank)")
    void testIsRootEvent_true_blankCausation() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "   ", "evt-root"
        );

        // when
        boolean isRoot = eventSource.isRootEvent();

        // then
        assertTrue(isRoot);
    }

    @Test
    @DisplayName("isRootEvent test - not a Root Event")
    void testIsRootEvent_false() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "evt-000", "evt-root"
        );

        // when
        boolean isRoot = eventSource.isRootEvent();

        // then
        assertFalse(isRoot);
    }

    @Test
    @DisplayName("estimateChainDepth test - no Event Chain")
    void testEstimateChainDepth_noChain() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0"
        );

        // when
        int depth = eventSource.estimateChainDepth();

        // then
        assertEquals(0, depth);
    }

    @Test
    @DisplayName("estimateChainDepth test - Root Event")
    void testEstimateChainDepth_rootEvent() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", null, "evt-root"
        );

        // when
        int depth = eventSource.estimateChainDepth();

        // then
        assertEquals(1, depth);
    }

    @Test
    @DisplayName("estimateChainDepth test - Child Event")
    void testEstimateChainDepth_childEvent() {
        // given
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0",
                "corr-123", "evt-000", "evt-root"
        );

        // when
        int depth = eventSource.estimateChainDepth();

        // then
        assertEquals(2, depth);
    }

    @Test
    @DisplayName("EventSource 5-parameter constructor test")
    void testFiveParameterConstructor() {
        // given & when
        EventSource eventSource = new EventSource(
                "order-service", "prod", "inst-1", "host", "1.0.0"
        );

        // then
        assertEquals("order-service", eventSource.service());
        assertEquals("prod", eventSource.environment());
        assertEquals("inst-1", eventSource.instanceId());
        assertEquals("host", eventSource.host());
        assertEquals("1.0.0", eventSource.version());
        assertNull(eventSource.correlationId());
        assertNull(eventSource.causationId());
        assertNull(eventSource.rootEventId());
    }
}
