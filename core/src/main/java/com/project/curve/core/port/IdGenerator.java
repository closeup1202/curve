package com.project.curve.core.port;

import com.project.curve.core.envelope.EventId;

/**
 * 이벤트 ID 생성기 인터페이스.
 * <p>
 * 고유한 이벤트 ID를 생성하는 역할을 합니다.
 * 일반적으로 Snowflake ID 알고리즘 등을 사용하여 시간 순서 정렬이 가능한 ID를 생성합니다.
 */
public interface IdGenerator {
    /**
     * 새로운 고유 이벤트 ID를 생성합니다.
     *
     * @return 생성된 이벤트 ID
     */
    EventId generate();
}
