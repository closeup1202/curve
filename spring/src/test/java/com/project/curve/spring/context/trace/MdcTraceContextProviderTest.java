package com.project.curve.spring.context.trace;

import com.project.curve.core.envelope.EventTrace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MdcTraceContextProvider 테스트")
class MdcTraceContextProviderTest {

    private MdcTraceContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MdcTraceContextProvider();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC에 traceId와 spanId가 설정되어 있으면 해당 값을 반환한다")
    void getTrace_withMdcValues_shouldReturnValues() {
        // Given
        MDC.put("traceId", "abc123");
        MDC.put("spanId", "span456");

        // When
        EventTrace trace = provider.getTrace();

        // Then
        assertThat(trace.traceId()).isEqualTo("abc123");
        assertThat(trace.spanId()).isEqualTo("span456");
        assertThat(trace.parentSpanId()).isNull();
    }

    @Test
    @DisplayName("MDC에 값이 없으면 'unknown'을 반환한다")
    void getTrace_withNoMdcValues_shouldReturnUnknown() {
        // Given - MDC is empty

        // When
        EventTrace trace = provider.getTrace();

        // Then
        assertThat(trace.traceId()).isEqualTo("unknown");
        assertThat(trace.spanId()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("traceId만 설정되어 있으면 traceId는 반환하고 spanId는 'unknown'을 반환한다")
    void getTrace_withOnlyTraceId_shouldReturnPartialValues() {
        // Given
        MDC.put("traceId", "trace-only");

        // When
        EventTrace trace = provider.getTrace();

        // Then
        assertThat(trace.traceId()).isEqualTo("trace-only");
        assertThat(trace.spanId()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("spanId만 설정되어 있으면 spanId는 반환하고 traceId는 'unknown'을 반환한다")
    void getTrace_withOnlySpanId_shouldReturnPartialValues() {
        // Given
        MDC.put("spanId", "span-only");

        // When
        EventTrace trace = provider.getTrace();

        // Then
        assertThat(trace.traceId()).isEqualTo("unknown");
        assertThat(trace.spanId()).isEqualTo("span-only");
    }

    @Test
    @DisplayName("여러 번 호출해도 일관된 결과를 반환한다")
    void getTrace_calledMultipleTimes_shouldReturnConsistentResults() {
        // Given
        MDC.put("traceId", "consistent-trace");
        MDC.put("spanId", "consistent-span");

        // When
        EventTrace trace1 = provider.getTrace();
        EventTrace trace2 = provider.getTrace();

        // Then
        assertThat(trace1.traceId()).isEqualTo(trace2.traceId());
        assertThat(trace1.spanId()).isEqualTo(trace2.spanId());
    }
}
