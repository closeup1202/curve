package com.project.curve.spring.context.correlation;

import com.project.curve.core.context.CorrelationContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * SLF4J MDC 기반 Correlation Context Provider.
 * <p>
 * MDC(Mapped Diagnostic Context)를 사용하여 Correlation ID, Causation ID, Root Event ID를 관리합니다.
 *
 * <h3>MDC 키</h3>
 * <ul>
 *   <li>correlationId: 비즈니스 트랜잭션 ID</li>
 *   <li>causationId: 이 이벤트를 유발한 이벤트 ID</li>
 *   <li>rootEventId: 이벤트 체인의 최초 이벤트 ID</li>
 * </ul>
 *
 * <h3>사용 예시</h3>
 * <pre>
 * // 1. HTTP Filter에서 설정
 * String correlationId = request.getHeader("X-Correlation-ID");
 * if (correlationId == null) {
 *     correlationId = UUID.randomUUID().toString();
 * }
 * MDC.put("correlationId", correlationId);
 *
 * // 2. 이벤트 발행 시 자동으로 포함됨
 * @PublishEvent(eventType = "ORDER_CREATED")
 * public Order createOrder() { ... }
 *
 * // 3. 요청 완료 후 정리
 * MDC.clear();
 * </pre>
 *
 * @see CorrelationContextProvider
 * @see MDC
 */
@Slf4j
@Component
public class MdcCorrelationContextProvider implements CorrelationContextProvider {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CAUSATION_ID_KEY = "causationId";
    private static final String ROOT_EVENT_ID_KEY = "rootEventId";

    @Override
    public String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    @Override
    public String getCausationId() {
        return MDC.get(CAUSATION_ID_KEY);
    }

    @Override
    public String getRootEventId() {
        return MDC.get(ROOT_EVENT_ID_KEY);
    }

    @Override
    public void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
            log.debug("Set correlationId in MDC: {}", correlationId);
        } else {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    @Override
    public void setCausationId(String causationId) {
        if (causationId != null && !causationId.isBlank()) {
            MDC.put(CAUSATION_ID_KEY, causationId);
            log.debug("Set causationId in MDC: {}", causationId);
        } else {
            MDC.remove(CAUSATION_ID_KEY);
        }
    }

    @Override
    public void setRootEventId(String rootEventId) {
        if (rootEventId != null && !rootEventId.isBlank()) {
            MDC.put(ROOT_EVENT_ID_KEY, rootEventId);
            log.debug("Set rootEventId in MDC: {}", rootEventId);
        } else {
            MDC.remove(ROOT_EVENT_ID_KEY);
        }
    }

    @Override
    public void clear() {
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(CAUSATION_ID_KEY);
        MDC.remove(ROOT_EVENT_ID_KEY);
        log.trace("Cleared correlation context from MDC");
    }

    /**
     * 모든 MDC 정리 (디버깅용).
     */
    public void clearAll() {
        MDC.clear();
        log.trace("Cleared all MDC context");
    }
}
