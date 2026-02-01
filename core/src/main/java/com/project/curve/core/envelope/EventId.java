package com.project.curve.core.envelope;

/**
 * 이벤트 고유 식별자.
 * <p>
 * 분산 환경에서 유일성을 보장하는 식별자입니다.
 * 일반적으로 Snowflake ID와 같이 시간 순서 정렬이 가능한 ID를 사용합니다.
 *
 * @param value 식별자 값
 */
public record EventId(String value) {

    public EventId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
    }

    public static EventId of(String value) {
        return new EventId(value);
    }
}
