package com.project.curve.core.envelope;

/**
 * 이벤트 발생처(Source) 정보.
 * <p>
 * 마이크로서비스 환경에서 이벤트 흐름을 추적하기 위한 Event Chain Tracking을 지원합니다.
 *
 * <h3>이벤트 체인 추적 (Event Chain Tracking)</h3>
 * <ul>
 *   <li>correlationId: 동일한 비즈니스 트랜잭션의 모든 이벤트를 그룹화</li>
 *   <li>causationId: 이 이벤트를 유발한 원인 이벤트의 ID (부모 이벤트)</li>
 *   <li>rootEventId: 이벤트 체인의 최초 시작 이벤트 ID</li>
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
 * 2. PAYMENT_PROCESSED (ORDER_CREATED에 의해 유발됨)
 *    - eventId: "evt-002"
 *    - correlationId: "txn-123" (동일 트랜잭션)
 *    - causationId: "evt-001" (ORDER_CREATED가 원인)
 *    - rootEventId: "evt-001"
 *
 * 3. INVENTORY_RESERVED (PAYMENT_PROCESSED에 의해 유발됨)
 *    - eventId: "evt-003"
 *    - correlationId: "txn-123"
 *    - causationId: "evt-002" (PAYMENT_PROCESSED가 원인)
 *    - rootEventId: "evt-001"
 * </pre>
 *
 * @param service       서비스명 (예: "order-service")
 * @param environment   환경 (예: "prod", "dev")
 * @param instanceId    인스턴스 ID
 * @param host          호스트 정보
 * @param version       서비스 버전
 * @param correlationId 상관 ID (비즈니스 트랜잭션 그룹화)
 * @param causationId   원인 ID (이 이벤트를 유발한 이벤트 ID)
 * @param rootEventId   루트 이벤트 ID (체인의 첫 번째 이벤트)
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
     * Event Chain Tracking 없이 생성 (하위 호환성).
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
     * 이벤트 체인이 존재하는지 확인합니다.
     *
     * @return correlationId가 존재하면 true
     */
    public boolean hasEventChain() {
        return correlationId != null && !correlationId.isBlank();
    }

    /**
     * 루트 이벤트인지 확인합니다 (causationId 없음).
     *
     * @return causationId가 없으면 true
     */
    public boolean isRootEvent() {
        return causationId == null || causationId.isBlank();
    }

    /**
     * 이벤트 체인 깊이를 계산합니다 (추정치, 정확한 깊이는 DB 조회 필요).
     * <p>
     * rootEventId가 존재하면 최소 1
     *
     * @return 이벤트 체인 깊이 (추정)
     */
    public int estimateChainDepth() {
        if (!hasEventChain()) {
            return 0;
        }
        if (isRootEvent()) {
            return 1; // 최초 이벤트
        }
        // causationId가 있으면 최소 2 (실제 깊이는 DB 조회 필요)
        return 2;
    }
}
