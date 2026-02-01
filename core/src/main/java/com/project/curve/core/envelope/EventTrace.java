package com.project.curve.core.envelope;

/**
 * 분산 추적(Distributed Tracing) 정보.
 * <p>
 * 마이크로서비스 간의 요청 흐름을 추적하기 위한 정보입니다.
 * OpenTelemetry 등의 표준과 호환되는 구조를 가집니다.
 *
 * @param traceId       전체 트랜잭션을 식별하는 고유 ID
 * @param spanId        현재 작업 단위를 식별하는 ID
 * @param correlationId 비즈니스 트랜잭션을 그룹화하는 상관 ID (선택사항)
 */
public record EventTrace(
        String traceId,
        String spanId,
        String correlationId
) {
}
