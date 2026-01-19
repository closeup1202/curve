package com.project.curve.core.exception;

import lombok.Getter;

/**
 * 시스템 시간이 역행한 경우 발생하는 예외
 * Snowflake ID 생성 시 시간 기반 ID의 유일성을 보장하기 위해
 * 시간이 역행하면 ID 충돌 가능성이 있어 예외를 발생시킴
 */
@Getter
public class ClockMovedBackwardsException extends RuntimeException {

    private final long lastTimestamp;
    private final long currentTimestamp;

    public ClockMovedBackwardsException(long lastTimestamp, long currentTimestamp) {
        super(String.format(
                "Clock moved backwards. Refusing to generate ID. lastTimestamp=%d, currentTimestamp=%d, diff=%dms",
                lastTimestamp, currentTimestamp, lastTimestamp - currentTimestamp));
        this.lastTimestamp = lastTimestamp;
        this.currentTimestamp = currentTimestamp;
    }

    public ClockMovedBackwardsException(String message) {
        super(message);
        this.lastTimestamp = -1;
        this.currentTimestamp = -1;
    }

    public ClockMovedBackwardsException(String message, Throwable cause) {
        super(message, cause);
        this.lastTimestamp = -1;
        this.currentTimestamp = -1;
    }

    public long getDifferenceMs() {
        return lastTimestamp - currentTimestamp;
    }
}
