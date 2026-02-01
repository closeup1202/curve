package com.project.curve.core.payload;

import com.project.curve.core.type.EventType;

/**
 * 모든 도메인 이벤트 페이로드(DTO)가 구현해야 하는 마커 인터페이스.
 * <p>
 * 이 인터페이스를 구현함으로써 해당 객체가 도메인 이벤트임을 명시하고,
 * 자신의 이벤트 타입을 스스로 제공하도록 강제합니다.
 */
public interface DomainEventPayload {
    /**
     * 이 페이로드의 이벤트 타입을 반환합니다.
     *
     * @return 이벤트 타입
     */
    EventType getEventType();
}
