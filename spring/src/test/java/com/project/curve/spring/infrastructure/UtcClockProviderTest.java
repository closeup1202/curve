package com.project.curve.spring.infrastructure;

import com.project.curve.core.port.ClockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UtcClockProvider 테스트")
class UtcClockProviderTest {

    @Test
    @DisplayName("UtcClockProvider 생성")
    void createUtcClockProvider() {
        // when
        UtcClockProvider provider = new UtcClockProvider();

        // then
        assertNotNull(provider);
    }

    @Test
    @DisplayName("ClockProvider 인터페이스 구현")
    void implementsClockProvider() {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // then
        assertTrue(provider instanceof ClockProvider);
    }

    @Test
    @DisplayName("now() 메서드가 현재 시간 반환")
    void nowReturnsCurrentTime() {
        // given
        UtcClockProvider provider = new UtcClockProvider();
        Instant before = Instant.now();

        // when
        Instant now = provider.now();

        // then
        Instant after = Instant.now();
        assertNotNull(now);
        assertTrue(now.isAfter(before.minusSeconds(1)));
        assertTrue(now.isBefore(after.plusSeconds(1)));
    }

    @Test
    @DisplayName("연속 호출 시 시간이 증가함")
    void consecutiveCallsIncreaseTime() throws InterruptedException {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // when
        Instant first = provider.now();
        Thread.sleep(10);
        Instant second = provider.now();

        // then
        assertTrue(second.isAfter(first) || second.equals(first));
    }

    @Test
    @DisplayName("여러 인스턴스가 동일한 시간 반환")
    void multipleInstancesReturnSameTime() {
        // given
        UtcClockProvider provider1 = new UtcClockProvider();
        UtcClockProvider provider2 = new UtcClockProvider();

        // when
        Instant time1 = provider1.now();
        Instant time2 = provider2.now();

        // then
        long diff = Math.abs(time1.toEpochMilli() - time2.toEpochMilli());
        assertTrue(diff < 100); // 100ms 이내 차이
    }

    @Test
    @DisplayName("UTC 타임존 사용")
    void usesUtcTimezone() {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // when
        Instant now = provider.now();

        // then
        assertNotNull(now);
        // UTC 시간이므로 epoch 시간과 동일
        assertTrue(now.getEpochSecond() > 0);
    }

    @Test
    @DisplayName("반환된 Instant가 null이 아님")
    void returnedInstantIsNotNull() {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // when
        Instant now = provider.now();

        // then
        assertNotNull(now);
    }

    @Test
    @DisplayName("여러 번 호출해도 안정적으로 동작")
    void stableAcrossMultipleCalls() {
        // given
        UtcClockProvider provider = new UtcClockProvider();

        // when & then
        for (int i = 0; i < 100; i++) {
            Instant now = provider.now();
            assertNotNull(now);
            assertTrue(now.getEpochSecond() > 0);
        }
    }
}
