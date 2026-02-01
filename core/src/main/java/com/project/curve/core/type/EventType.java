package com.project.curve.core.type;

/**
 * 이벤트 타입을 정의하는 인터페이스.
 * <p>
 * 모든 이벤트는 고유한 타입을 가져야 하며, 이를 통해 구독자(Consumer)가 이벤트를 식별하고 처리합니다.
 */
public interface EventType {
    /**
     * 이벤트 타입의 문자열 값을 반환합니다.
     * 예: "ORDER_CREATED", "USER_UPDATED"
     *
     * @return 이벤트 타입 문자열
     */
    String getValue();
}
