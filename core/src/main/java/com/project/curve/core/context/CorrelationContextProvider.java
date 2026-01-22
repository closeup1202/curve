package com.project.curve.core.context;

/**
 * Correlation ID 및 Event Chain 정보를 제공하는 포트 인터페이스.
 * <p>
 * 마이크로서비스 환경에서 이벤트 체인을 추적하기 위해 사용됩니다.
 *
 * <h3>구현체 예시</h3>
 * <ul>
 *   <li>MdcCorrelationContextProvider - SLF4J MDC 기반</li>
 *   <li>HttpHeaderCorrelationContextProvider - HTTP 헤더 기반</li>
 *   <li>KafkaHeaderCorrelationContextProvider - Kafka 헤더 기반</li>
 * </ul>
 *
 * <h3>사용 패턴</h3>
 * <pre>
 * // 1. HTTP 요청에서 Correlation ID 추출
 * String correlationId = request.getHeader("X-Correlation-ID");
 * MDC.put("correlationId", correlationId);
 *
 * // 2. 이벤트 발행 시 자동으로 포함
 * @PublishEvent(eventType = "ORDER_CREATED")
 * public Order createOrder() { ... }
 *
 * // 3. 다음 서비스로 전파 (Kafka 헤더)
 * headers.add("X-Correlation-ID", correlationId);
 * headers.add("X-Causation-ID", eventId);
 * </pre>
 */
public interface CorrelationContextProvider {

    /**
     * Correlation ID 조회.
     * <p>
     * 같은 비즈니스 트랜잭션의 모든 이벤트를 그룹화하는 ID입니다.
     *
     * @return Correlation ID (없으면 null)
     */
    String getCorrelationId();

    /**
     * Causation ID 조회.
     * <p>
     * 현재 이벤트를 유발한 부모 이벤트의 ID입니다.
     *
     * @return Causation ID (없으면 null)
     */
    String getCausationId();

    /**
     * Root Event ID 조회.
     * <p>
     * 이벤트 체인의 최초 이벤트 ID입니다.
     *
     * @return Root Event ID (없으면 null)
     */
    String getRootEventId();

    /**
     * Correlation ID 설정.
     * <p>
     * 새로운 비즈니스 트랜잭션 시작 시 호출됩니다.
     *
     * @param correlationId 설정할 Correlation ID
     */
    void setCorrelationId(String correlationId);

    /**
     * Causation ID 설정.
     * <p>
     * 이벤트 처리 시 다음 이벤트의 causationId로 사용할 eventId를 설정합니다.
     *
     * @param causationId 설정할 Causation ID
     */
    void setCausationId(String causationId);

    /**
     * Root Event ID 설정.
     *
     * @param rootEventId 설정할 Root Event ID
     */
    void setRootEventId(String rootEventId);

    /**
     * 모든 컨텍스트 정리.
     * <p>
     * 요청 처리 완료 후 메모리 누수 방지를 위해 호출합니다.
     */
    void clear();
}
