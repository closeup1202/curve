package com.project.curve.core.envelope;

/**
 * 이벤트의 출처 정보.
 * <p>
 * Event Chain Tracking을 지원하여 마이크로서비스 환경에서 이벤트 흐름을 추적할 수 있습니다.
 *
 * <h3>Event Chain Tracking</h3>
 * <ul>
 *   <li>correlationId: 같은 비즈니스 트랜잭션의 모든 이벤트를 그룹화</li>
 *   <li>causationId: 이 이벤트를 유발한 이벤트 ID (부모 이벤트)</li>
 *   <li>rootEventId: 이벤트 체인의 최초 이벤트 ID</li>
 * </ul>
 *
 * <h3>예시: 주문 흐름</h3>
 * <pre>
 * 1. ORDER_CREATED
 *    - eventId: "evt-001"
 *    - correlationId: "txn-123"
 *    - causationId: null (최초 이벤트)
 *    - rootEventId: "evt-001"
 *
 * 2. PAYMENT_PROCESSED (ORDER_CREATED에 의해 발생)
 *    - eventId: "evt-002"
 *    - correlationId: "txn-123" (같은 트랜잭션)
 *    - causationId: "evt-001" (ORDER_CREATED가 유발)
 *    - rootEventId: "evt-001"
 *
 * 3. INVENTORY_RESERVED (PAYMENT_PROCESSED에 의해 발생)
 *    - eventId: "evt-003"
 *    - correlationId: "txn-123"
 *    - causationId: "evt-002" (PAYMENT_PROCESSED가 유발)
 *    - rootEventId: "evt-001"
 * </pre>
 *
 * @param service       서비스 이름 (예: "order-service")
 * @param environment   환경 (예: "prod", "dev")
 * @param instanceId    인스턴스 ID
 * @param host          호스트 정보
 * @param version       서비스 버전
 * @param correlationId Correlation ID (비즈니스 트랜잭션 그룹화)
 * @param causationId   Causation ID (이 이벤트를 유발한 이벤트 ID)
 * @param rootEventId   Root Event ID (이벤트 체인의 최초 이벤트)
 */
public record EventSource(
        String service,
        String environment,
        String instanceId,
        String host,
        String version,
        String correlationId,
        String causationId,
        String rootEventId
) {
    public EventSource {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service is required");
        }
    }

    /**
     * Event Chain Tracking 없이 생성 (하위 호환).
     */
    public EventSource(
            String service,
            String environment,
            String instanceId,
            String host,
            String version
    ) {
        this(service, environment, instanceId, host, version, null, null, null);
    }

    /**
     * Event Chain이 있는지 확인.
     *
     * @return correlationId가 존재하면 true
     */
    public boolean hasEventChain() {
        return correlationId != null && !correlationId.isBlank();
    }

    /**
     * 최초 이벤트인지 확인 (causationId가 없음).
     *
     * @return causationId가 없으면 true
     */
    public boolean isRootEvent() {
        return causationId == null || causationId.isBlank();
    }

    /**
     * 이벤트 체인 깊이 계산 (대략적, 실제로는 DB 조회 필요).
     * <p>
     * rootEventId가 있으면 최소 1 이상
     *
     * @return 이벤트 체인 깊이 (추정값)
     */
    public int estimateChainDepth() {
        if (!hasEventChain()) {
            return 0;
        }
        if (isRootEvent()) {
            return 1; // 최초 이벤트
        }
        // causationId가 있으면 최소 2 이상 (실제로는 DB 조회 필요)
        return 2;
    }
}
