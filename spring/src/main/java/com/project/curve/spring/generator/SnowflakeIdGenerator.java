package com.project.curve.spring.generator;

import com.project.curve.core.envelope.EventId;
import com.project.curve.core.exception.ClockMovedBackwardsException;
import com.project.curve.core.port.IdGenerator;

public final class SnowflakeIdGenerator implements IdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS); // 1023
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS); // 4095

    /**
     * 시간 역행 시 대기할 최대 시간 (밀리초)
     */
    private static final long MAX_BACKWARD_MS = 5L;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("Worker ID must be between 0 and %d, but got %d", MAX_WORKER_ID, workerId));
        }
        this.workerId = workerId;
    }

    @Override
    public synchronized EventId generate() {
        long timestamp = currentTimeMillis();

        // 시간이 역행한 경우
        if (timestamp < lastTimestamp) {
            long backwardMs = lastTimestamp - timestamp;

            // 작은 역행(5ms 이하)은 대기 후 재시도
            if (backwardMs <= MAX_BACKWARD_MS) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            } else {
                // 큰 역행은 예외 발생
                throw new ClockMovedBackwardsException(lastTimestamp, timestamp);
            }
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntilNextMillis(timestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        long id = ((timestamp - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (workerId << SEQUENCE_BITS)
                | sequence;

        return EventId.of(String.valueOf(id));
    }

    private long waitUntilNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
