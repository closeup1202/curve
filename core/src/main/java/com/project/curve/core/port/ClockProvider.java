package com.project.curve.core.port;

import java.time.Instant;

/**
 * 현재 시간을 제공하는 인터페이스.
 * <p>
 * 테스트 시 시간을 고정하거나 조작하기 위해 사용됩니다.
 */
public interface ClockProvider {
    /**
     * 현재 시각을 반환합니다.
     *
     * @return 현재 시각
     */
    Instant now();
}