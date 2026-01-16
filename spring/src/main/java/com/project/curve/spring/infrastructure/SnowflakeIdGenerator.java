package com.project.curve.spring.infrastructure;

import com.project.curve.core.envelope.EventId;
import com.project.curve.core.exception.ClockMovedBackwardsException;
import com.project.curve.core.port.IdGenerator;
import lombok.extern.slf4j.Slf4j;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public final class SnowflakeIdGenerator implements IdGenerator {

    private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS); // 1023
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS); // 4095

    /**
     * 시간 역행 시 대기할 최대 시간 (밀리초)
     */
    private static final long MAX_BACKWARD_MS = 100L;

    private final long workerId;
    private final Lock lock = new ReentrantLock();
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * Worker ID를 명시적으로 지정하는 생성자
     *
     * @param workerId 0 ~ 1023 사이의 고유 Worker ID
     */
    public SnowflakeIdGenerator(long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("Worker ID must be between 0 and %d, but got %d", MAX_WORKER_ID, workerId));
        }
        this.workerId = workerId;
        log.info("SnowflakeIdGenerator initialized with workerId: {}", workerId);
    }

    /**
     * MAC 주소 기반으로 Worker ID를 자동 생성하는 생성자
     * 주의: 네트워크 환경에 따라 충돌 가능성이 있음
     */
    public static SnowflakeIdGenerator createWithAutoWorkerId() {
        long generatedWorkerId = generateWorkerIdFromMacAddress();
        log.warn("Worker ID auto-generated from MAC address: {} (collision possible in distributed environment)",
                generatedWorkerId);
        return new SnowflakeIdGenerator(generatedWorkerId);
    }

    /**
     * MAC 주소를 기반으로 Worker ID를 생성
     * MAC 주소의 하위 10비트를 사용 (0 ~ 1023)
     */
    private static long generateWorkerIdFromMacAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();

                if (mac != null && mac.length >= 6) {
                    // MAC 주소의 마지막 2바이트를 사용하여 Worker ID 생성
                    long workerId = ((0x000000FF & (long) mac[mac.length - 2]) << 2)
                            | ((0x000000FF & (long) mac[mac.length - 1]) >> 6);
                    return workerId & MAX_WORKER_ID;
                }
            }
        } catch (SocketException e) {
            log.warn("Failed to get MAC address, using default worker ID: 1", e);
        }
        // MAC 주소를 가져올 수 없는 경우 기본값 1 반환
        return 1L;
    }

    @Override
    public EventId generate() {
        lock.lock();
        try {
            long timestamp = currentTimeMillis();

            // 시간이 역행한 경우
            if (timestamp < lastTimestamp) {
                long backwardMs = lastTimestamp - timestamp;

                // 작은 역행(100ms 이하)은 대기 후 재시도
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
        } finally {
            lock.unlock();
        }
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
