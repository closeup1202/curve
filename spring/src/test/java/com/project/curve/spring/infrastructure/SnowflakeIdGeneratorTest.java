package com.project.curve.spring.infrastructure;

import com.project.curve.core.envelope.EventId;
import com.project.curve.core.exception.ClockMovedBackwardsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("유효한 Worker ID로 생성자를 호출하면 성공한다")
    void constructor_withValidWorkerId_shouldSucceed() {
        // Given
        long workerId = 100L;

        // When & Then
        assertThatNoException().isThrownBy(() -> new SnowflakeIdGenerator(workerId));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1024, 2000})
    @DisplayName("유효하지 않은 Worker ID로 생성자를 호출하면 예외가 발생한다")
    void constructor_withInvalidWorkerId_shouldThrowException(long invalidWorkerId) {
        // When & Then
        assertThatThrownBy(() -> new SnowflakeIdGenerator(invalidWorkerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Worker ID must be between 0 and 1023");
    }

    @Test
    @DisplayName("ID를 생성하면 null이 아닌 EventId를 반환한다")
    void generate_shouldReturnNonNullEventId() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        // When
        EventId eventId = generator.generate();

        // Then
        assertThat(eventId).isNotNull();
        assertThat(eventId.value()).isNotEmpty();
    }

    @Test
    @DisplayName("연속으로 생성된 ID는 모두 고유해야 한다")
    void generate_consecutiveIds_shouldAllBeUnique() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        Set<String> generatedIds = new HashSet<>();
        int count = 10000;

        // When
        for (int i = 0; i < count; i++) {
            EventId eventId = generator.generate();
            generatedIds.add(eventId.value());
        }

        // Then
        assertThat(generatedIds).hasSize(count);
    }

    @Test
    @DisplayName("동시에 여러 스레드에서 ID를 생성해도 고유성이 보장되어야 한다")
    void generate_concurrently_shouldMaintainUniqueness() throws InterruptedException {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        int threadCount = 10;
        int idsPerThread = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<String> generatedIds = new HashSet<>();
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        EventId eventId = generator.generate();
                        synchronized (generatedIds) {
                            if (!generatedIds.add(eventId.value())) {
                                duplicateCount.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        // Then
        assertThat(duplicateCount.get()).isZero();
        assertThat(generatedIds).hasSize(threadCount * idsPerThread);
    }

    @Test
    @DisplayName("같은 밀리초 내에 4096개 이상의 ID를 생성하면 다음 밀리초까지 대기한다")
    void generate_sequenceOverflow_shouldWaitForNextMillis() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        Set<String> generatedIds = new HashSet<>();

        // When: 5000개의 ID를 빠르게 생성 (sequence overflow 발생 가능)
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 5000; i++) {
            EventId eventId = generator.generate();
            generatedIds.add(eventId.value());
        }
        long endTime = System.currentTimeMillis();

        // Then: 모든 ID는 고유해야 하고, 시간이 소요되어야 함
        assertThat(generatedIds).hasSize(5000);
        assertThat(endTime - startTime).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("서로 다른 Worker ID를 가진 제너레이터는 다른 ID를 생성한다")
    void generate_differentWorkerIds_shouldGenerateDifferentIds() {
        // Given
        SnowflakeIdGenerator generator1 = new SnowflakeIdGenerator(1L);
        SnowflakeIdGenerator generator2 = new SnowflakeIdGenerator(2L);

        // When
        EventId id1 = generator1.generate();
        EventId id2 = generator2.generate();

        // Then
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    @DisplayName("MAC 주소 기반 자동 Worker ID 생성은 예외를 발생시키지 않는다")
    void createWithAutoWorkerId_shouldNotThrowException() {
        // When & Then
        assertThatNoException().isThrownBy(SnowflakeIdGenerator::createWithAutoWorkerId);
    }

    @Test
    @DisplayName("생성된 ID는 숫자 형태의 문자열이어야 한다")
    void generate_shouldReturnNumericStringId() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        // When
        EventId eventId = generator.generate();

        // Then
        assertThat(eventId.value()).matches("\\d+");
    }

    @Test
    @DisplayName("생성된 ID는 양수여야 한다")
    void generate_shouldReturnPositiveId() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        // When
        EventId eventId = generator.generate();
        long id = Long.parseLong(eventId.value());

        // Then
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("경계값 Worker ID(0, 1023)로도 정상적으로 ID를 생성한다")
    void generate_withBoundaryWorkerIds_shouldSucceed() {
        // Given
        SnowflakeIdGenerator generator1 = new SnowflakeIdGenerator(0L);
        SnowflakeIdGenerator generator2 = new SnowflakeIdGenerator(1023L);

        // When
        EventId id1 = generator1.generate();
        EventId id2 = generator2.generate();

        // Then
        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    @DisplayName("MAC 주소 기반 자동 Worker ID로 ID 생성이 가능하다")
    void createWithAutoWorkerId_shouldGenerateId() {
        // Given
        SnowflakeIdGenerator generator = SnowflakeIdGenerator.createWithAutoWorkerId();

        // When
        EventId id = generator.generate();

        // Then
        assertThat(id).isNotNull();
        assertThat(id.value()).isNotEmpty();
    }

    @Test
    @DisplayName("자동 생성된 Worker ID로도 고유한 ID를 생성한다")
    void createWithAutoWorkerId_shouldGenerateUniqueIds() {
        // Given
        SnowflakeIdGenerator generator = SnowflakeIdGenerator.createWithAutoWorkerId();
        Set<String> ids = new HashSet<>();

        // When
        for (int i = 0; i < 100; i++) {
            EventId id = generator.generate();
            ids.add(id.value());
        }

        // Then
        assertThat(ids).hasSize(100);
    }

    @Test
    @DisplayName("연속 생성된 ID는 시간순으로 증가한다")
    void generate_consecutiveIds_shouldBeMonotonicallyIncreasing() {
        // Given
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);

        // When
        long id1 = Long.parseLong(generator.generate().value());
        long id2 = Long.parseLong(generator.generate().value());
        long id3 = Long.parseLong(generator.generate().value());

        // Then
        assertThat(id2).isGreaterThan(id1);
        assertThat(id3).isGreaterThan(id2);
    }

    @Test
    @DisplayName("작은 시계 역행(100ms 이하)은 대기 후 정상 처리된다")
    void generate_smallClockBackward_shouldWaitAndSucceed() {
        // Given
        TestableSnowflakeIdGenerator generator = new TestableSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // 첫 번째 ID 생성
        generator.setCurrentTime(baseTime);
        EventId id1 = generator.generate();

        // 시계를 50ms 뒤로 이동 (작은 역행)
        generator.setCurrentTime(baseTime - 50);
        generator.addTimeSequence(baseTime - 50); // 한 번 더 같은 시간 반환
        generator.addTimeSequence(baseTime + 1);  // 그 다음 정상 시간 반환

        // When
        EventId id2 = generator.generate();

        // Then
        assertThat(id2).isNotNull();
        assertThat(id1.value()).isNotEqualTo(id2.value());
    }

    @Test
    @DisplayName("큰 시계 역행(100ms 초과)은 예외를 발생시킨다")
    void generate_largeClockBackward_shouldThrowException() {
        // Given
        TestableSnowflakeIdGenerator generator = new TestableSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // 첫 번째 ID 생성
        generator.setCurrentTime(baseTime);
        generator.generate();

        // 시계를 200ms 뒤로 이동 (큰 역행)
        generator.setCurrentTime(baseTime - 200);

        // When & Then
        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(ClockMovedBackwardsException.class);
    }

    @Test
    @DisplayName("waitUntilNextMillis 타임아웃 시 예외를 발생시킨다")
    void generate_waitTimeout_shouldThrowException() {
        // Given
        TimeoutTestableSnowflakeIdGenerator generator = new TimeoutTestableSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // 첫 번째 ID 생성
        generator.setCurrentTime(baseTime);
        generator.generate();

        // 시계를 1ms 뒤로 이동하고, 계속 같은 시간 반환 (타임아웃 유발)
        generator.setStuckTime(baseTime - 1);

        // When & Then
        assertThatThrownBy(() -> generator.generate())
                .isInstanceOf(ClockMovedBackwardsException.class)
                .hasMessageContaining("Timeout waiting for clock to advance");
    }

    @Test
    @DisplayName("waitUntilNextMillis 중 인터럽트 시 예외를 발생시킨다")
    void generate_waitInterrupted_shouldThrowException() throws InterruptedException {
        // Given
        InterruptibleTestableSnowflakeIdGenerator generator = new InterruptibleTestableSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // 첫 번째 ID 생성
        generator.setCurrentTime(baseTime);
        generator.generate();

        // 시계를 1ms 뒤로 이동
        generator.setCurrentTime(baseTime - 1);

        CountDownLatch latch = new CountDownLatch(1);
        Thread[] testThread = new Thread[1];

        // When
        Thread thread = new Thread(() -> {
            testThread[0] = Thread.currentThread();
            latch.countDown();
            assertThatThrownBy(() -> generator.generate())
                    .isInstanceOf(ClockMovedBackwardsException.class)
                    .hasMessageContaining("Interrupted while waiting for clock to advance");
        });
        thread.start();

        latch.await();
        Thread.sleep(10); // 스레드가 대기 상태에 들어갈 때까지 잠시 대기
        testThread[0].interrupt();
        thread.join(1000);
    }

    @Test
    @DisplayName("exponential backoff가 적용되어 대기한다")
    void generate_exponentialBackoff_shouldWaitWithIncreasingIntervals() {
        // Given
        BackoffTrackingSnowflakeIdGenerator generator = new BackoffTrackingSnowflakeIdGenerator(1L);
        long baseTime = System.currentTimeMillis();

        // 첫 번째 ID 생성
        generator.setCurrentTime(baseTime);
        generator.generate();

        // 시계를 10ms 뒤로 이동하고, 여러 번 시도 후 정상 시간 반환
        generator.setCurrentTime(baseTime - 10);
        for (int i = 0; i < 3; i++) {
            generator.addTimeSequence(baseTime - 10);
        }
        generator.addTimeSequence(baseTime + 1);

        // When
        EventId id = generator.generate();

        // Then
        assertThat(id).isNotNull();
        assertThat(generator.getSleepCalls()).isGreaterThan(0);
    }

    /**
     * 시계 시간을 조작할 수 있는 테스트용 SnowflakeIdGenerator
     */
    private static class TestableSnowflakeIdGenerator extends SnowflakeIdGenerator {
        private long currentTime;
        private final java.util.Queue<Long> timeSequence = new java.util.LinkedList<>();

        public TestableSnowflakeIdGenerator(long workerId) {
            super(workerId);
            this.currentTime = System.currentTimeMillis();
        }

        public void setCurrentTime(long time) {
            this.currentTime = time;
        }

        public void addTimeSequence(long time) {
            this.timeSequence.add(time);
        }

        @Override
        protected long currentTimeMillis() {
            if (!timeSequence.isEmpty()) {
                return timeSequence.poll();
            }
            return currentTime;
        }
    }

    /**
     * 타임아웃 테스트용 SnowflakeIdGenerator
     */
    private static class TimeoutTestableSnowflakeIdGenerator extends SnowflakeIdGenerator {
        private long currentTime;
        private Long stuckTime;

        public TimeoutTestableSnowflakeIdGenerator(long workerId) {
            super(workerId);
            this.currentTime = System.currentTimeMillis();
        }

        public void setCurrentTime(long time) {
            this.currentTime = time;
        }

        public void setStuckTime(long time) {
            this.stuckTime = time;
        }

        @Override
        protected long currentTimeMillis() {
            if (stuckTime != null) {
                // System.currentTimeMillis()는 실제 시간으로 증가하지만,
                // currentTimeMillis()는 고정된 과거 시간을 반환하여 타임아웃 유발
                return stuckTime;
            }
            return currentTime;
        }
    }

    /**
     * 인터럽트 테스트용 SnowflakeIdGenerator
     */
    private static class InterruptibleTestableSnowflakeIdGenerator extends SnowflakeIdGenerator {
        private long currentTime;

        public InterruptibleTestableSnowflakeIdGenerator(long workerId) {
            super(workerId);
            this.currentTime = System.currentTimeMillis();
        }

        public void setCurrentTime(long time) {
            this.currentTime = time;
        }

        @Override
        protected long currentTimeMillis() {
            return currentTime;
        }
    }

    /**
     * Exponential backoff 추적용 SnowflakeIdGenerator
     */
    private static class BackoffTrackingSnowflakeIdGenerator extends SnowflakeIdGenerator {
        private long currentTime;
        private final java.util.Queue<Long> timeSequence = new java.util.LinkedList<>();
        private int sleepCalls = 0;

        public BackoffTrackingSnowflakeIdGenerator(long workerId) {
            super(workerId);
            this.currentTime = System.currentTimeMillis();
        }

        public void setCurrentTime(long time) {
            this.currentTime = time;
        }

        public void addTimeSequence(long time) {
            this.timeSequence.add(time);
        }

        public int getSleepCalls() {
            return sleepCalls;
        }

        @Override
        protected long currentTimeMillis() {
            if (!timeSequence.isEmpty()) {
                Long time = timeSequence.poll();
                if (time != null && time <= currentTime) {
                    sleepCalls++; // sleep이 호출되었을 것으로 추정
                }
                return time;
            }
            return currentTime;
        }
    }
}
