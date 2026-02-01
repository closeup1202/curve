package com.project.curve.core.context;

import com.project.curve.core.envelope.EventTrace;

/**
 * 현재 실행 컨텍스트에서 분산 추적(Trace) 정보를 제공하는 인터페이스.
 * <p>
 * 예: MDC(Mapped Diagnostic Context) 또는 OpenTelemetry Context에서 TraceId, SpanId 추출.
 */
public interface TraceContextProvider {
    /**
     * 현재 추적(Trace) 정보를 반환합니다.
     *
     * @return 이벤트 추적 정보
     */
    EventTrace getTrace();
}
