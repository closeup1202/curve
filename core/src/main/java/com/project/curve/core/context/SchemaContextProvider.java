package com.project.curve.core.context;

import com.project.curve.core.envelope.EventSchema;
import com.project.curve.core.payload.DomainEventPayload;

/**
 * 이벤트 스키마 정보를 제공하기 위한 인터페이스.
 */
public interface SchemaContextProvider {

    /**
     * 기본 스키마 정보를 반환합니다.
     * 페이로드 정보 없이 호출될 때 사용됩니다.
     */
    EventSchema getSchema();

    /**
     * 페이로드를 기반으로 스키마 정보를 반환합니다.
     * 어노테이션, 클래스 이름 등을 사용하여 동적으로 스키마를 결정할 수 있습니다.
     *
     * @param payload 이벤트 페이로드
     * @return 페이로드에 맞는 스키마 정보
     */
    default EventSchema getSchemaFor(DomainEventPayload payload) {
        return getSchema();
    }
}
