package com.project.curve.core.context;

import com.project.curve.core.envelope.EventMetadata;
import com.project.curve.core.payload.DomainEventPayload;

/**
 * 현재 실행 컨텍스트에서 이벤트 메타데이터를 제공하는 인터페이스.
 * <p>
 * 이벤트 발행 시점에 현재 스레드의 컨텍스트(사용자 정보, 추적 정보 등)를 수집하여
 * {@link EventMetadata}를 생성하는 역할을 합니다.
 */
public interface EventContextProvider {
    /**
     * 현재 컨텍스트를 기반으로 이벤트 메타데이터를 생성합니다.
     *
     * @param payload 이벤트 페이로드 (메타데이터 생성에 필요한 경우 참조)
     * @return 생성된 이벤트 메타데이터
     */
    EventMetadata currentMetadata(DomainEventPayload payload);
}
