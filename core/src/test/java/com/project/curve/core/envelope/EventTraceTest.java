package com.project.curve.core.envelope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EventTrace test")
class EventTraceTest {

    @Test
    @DisplayName("Create EventTrace with valid parameters")
    void createValidEventTrace() {
        // given
        String traceId = "trace-abc-123";
        String spanId = "span-def-456";
        String correlationId = "correlation-ghi-789";

        // when
        EventTrace trace = new EventTrace(traceId, spanId, correlationId);

        // then
        assertNotNull(trace);
        assertEquals(traceId, trace.traceId());
        assertEquals(spanId, trace.spanId());
        assertEquals(correlationId, trace.correlationId());
    }

    @Test
    @DisplayName("EventTrace - can be created with null values (no validation)")
    void createEventTraceWithNullValues() {
        // when
        EventTrace trace = new EventTrace(null, null, null);

        // then - creation succeeds because there is no validation
        assertNotNull(trace);
        assertNull(trace.traceId());
        assertNull(trace.spanId());
        assertNull(trace.correlationId());
    }
}
