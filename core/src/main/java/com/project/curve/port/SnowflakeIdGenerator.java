package com.project.curve.port;

public class SnowflakeIdGenerator implements IdGenerator {
    private final long workerId;
    private final long workerIdBits = 10L;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        long maxWorkerId = ~(-1L << workerIdBits);
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException("Worker ID out of range");
        }
        this.workerId = workerId;
    }

    @Override
    public synchronized Long generate() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards!");
        }

        long sequenceBits = 12L;
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & ((1L << sequenceBits) - 1);
            if (sequence == 0) {
                while (timestamp <= lastTimestamp) {
                    timestamp = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 예: 2024-01-01 기점 (커스텀 가능)
        long epoch = 1704067200000L;

        return ((timestamp - epoch) << (workerIdBits + sequenceBits))
                | (workerId << sequenceBits)
                | sequence;
    }
}
