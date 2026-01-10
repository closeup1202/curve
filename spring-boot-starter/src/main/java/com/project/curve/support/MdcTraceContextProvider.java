//package com.project.curve.support;
//
//import lombok.extern.slf4j.Slf4j;
//
//@Slf4j
//public class MdcTraceContextProvider implements TraceContextProvider {
//
//    // 보통 Sleuth/Micrometer가 사용하는 표준 키값들
//    private static final String TRACE_ID_KEY = "traceId";
//    private static final String SPAN_ID_KEY = "spanId";
//
//    @Override
//    public EventTrace getTrace() {
//        String traceId = MDC.get(TRACE_ID_KEY);
//        String spanId = MDC.get(SPAN_ID_KEY);
//
//        // 만약 TraceID가 없다면 추적 불가능한 상태이므로 빈 값을 넣거나 새로 생성
//        return new EventTrace(
//                traceId != null ? traceId : "unknown",
//                spanId != null ? spanId : "unknown",
//                null // parentSpanId는 필요 시 추가 추출
//        );
//    }
//}
