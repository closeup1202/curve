package com.project.curve.spring.outbox.publisher;

import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.core.outbox.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Outbox 이벤트를 주기적으로 Kafka로 발행하는 퍼블리셔.
 * <p>
 * Transactional Outbox Pattern의 핵심 컴포넌트입니다.
 *
 * <h3>동작 방식</h3>
 * <ol>
 *   <li>일정 간격(기본 1초)으로 PENDING 상태의 이벤트를 조회</li>
 *   <li>조회된 이벤트를 Kafka로 발행 시도</li>
 *   <li>성공 시 PUBLISHED 상태로 변경</li>
 *   <li>실패 시 재시도 횟수 증가, 최대 재시도 초과 시 FAILED 상태로 변경</li>
 * </ol>
 *
 * <h3>설정</h3>
 * <pre>
 * curve:
 *   outbox:
 *     enabled: true
 *     poll-interval-ms: 1000      # 폴링 간격
 *     batch-size: 100              # 한 번에 처리할 이벤트 수
 *     max-retries: 3               # 최대 재시도 횟수
 * </pre>
 *
 * @see OutboxEvent
 * @see OutboxEventRepository
 */
@Slf4j
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final int batchSize;
    private final int maxRetries;
    private final int sendTimeoutSeconds;
    private final boolean cleanupEnabled;
    private final int retentionDays;
    private final boolean dynamicBatchingEnabled;
    private final boolean circuitBreakerEnabled;

    // 통계 및 메트릭
    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    // 서킷 브레이커 상태
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    private volatile boolean circuitOpen = false;
    private volatile long circuitOpenedAt = 0;

    // 서킷 브레이커 설정
    private static final int FAILURE_THRESHOLD = 5; // 5회 연속 실패 시 회로 개방
    private static final long CIRCUIT_OPEN_DURATION_MS = 60000L; // 1분 후 반개방(Half-Open) 시도
    private static final int HALF_OPEN_MAX_ATTEMPTS = 3; // 반개방 상태에서 최대 시도 횟수

    public OutboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            String topic,
            int batchSize,
            int maxRetries,
            int sendTimeoutSeconds,
            boolean cleanupEnabled,
            int retentionDays,
            boolean dynamicBatchingEnabled,
            boolean circuitBreakerEnabled
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
        this.cleanupEnabled = cleanupEnabled;
        this.retentionDays = retentionDays;
        this.dynamicBatchingEnabled = dynamicBatchingEnabled;
        this.circuitBreakerEnabled = circuitBreakerEnabled;

        log.info("OutboxEventPublisher initialized: topic={}, batchSize={}, maxRetries={}, sendTimeoutSeconds={}, " +
                        "cleanupEnabled={}, retentionDays={}, dynamicBatching={}, circuitBreaker={}",
                topic, batchSize, maxRetries, sendTimeoutSeconds, cleanupEnabled, retentionDays,
                dynamicBatchingEnabled, circuitBreakerEnabled);
    }

    /**
     * PENDING 상태의 이벤트를 주기적으로 Kafka로 발행합니다.
     * <p>
     * 설정된 poll-interval-ms 간격으로 실행됩니다.
     * 서킷 브레이커 및 동적 배치 크기 조정을 지원합니다.
     */
    @Scheduled(fixedDelayString = "${curve.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        // 서킷 브레이커 체크
        if (circuitBreakerEnabled && !shouldAllowRequest()) {
            log.debug("Circuit breaker is OPEN, skipping outbox processing");
            return;
        }

        try {
            // 동적 배치 크기 계산
            int effectiveBatchSize = calculateEffectiveBatchSize();

            List<OutboxEvent> pendingEvents = outboxRepository.findPendingForProcessing(effectiveBatchSize);

            if (pendingEvents.isEmpty()) {
                return;
            }

            log.debug("Processing {} pending outbox events (batchSize: {}, circuitState: {})",
                    pendingEvents.size(), effectiveBatchSize, getCircuitState());

            // 이벤트 처리
            int successCount = 0;
            int failureCount = 0;

            for (OutboxEvent event : pendingEvents) {
                try {
                    processEvent(event);
                    successCount++;
                    recordSuccess();
                } catch (Exception e) {
                    failureCount++;
                    recordFailure();
                    log.warn("Failed to process outbox event: eventId={}", event.getEventId(), e);
                }
            }

            log.debug("Outbox batch completed: success={}, failure={}, total={}",
                    successCount, failureCount, pendingEvents.size());

        } catch (Exception e) {
            log.error("Failed to process pending outbox events", e);
            recordFailure();
        }
    }

    /**
     * 동적 배치 크기 계산.
     * <p>
     * 큐 깊이(대기 중인 이벤트 수)에 따라 배치 크기를 조정합니다.
     * 큐가 많이 쌓여있으면 더 큰 배치로 처리하여 처리량을 높입니다.
     */
    private int calculateEffectiveBatchSize() {
        if (!dynamicBatchingEnabled) {
            return batchSize;
        }

        long pendingCount = outboxRepository.countByStatus(OutboxStatus.PENDING);

        // 동적 배치 크기 계산 로직
        // 예: 대기 건수가 1000건 이상이면 배치 크기 2배 (최대 500)
        if (pendingCount > 1000) {
            int dynamicSize = Math.min(batchSize * 2, 500); // 최대 500
            log.debug("High queue depth detected ({}), increasing batch size to {}", pendingCount, dynamicSize);
            return dynamicSize;
        } else if (pendingCount > 500) {
            int dynamicSize = Math.min((int) (batchSize * 1.5), 300); // 최대 300
            return dynamicSize;
        } else if (pendingCount < 10) {
            // 대기 건수가 적으면 작은 배치 사용
            return Math.min(batchSize, 10);
        }

        return batchSize;
    }

    /**
     * 서킷 브레이커: 요청 허용 여부 확인.
     */
    private boolean shouldAllowRequest() {
        if (!circuitOpen) {
            return true; // 회로 닫힘 (정상)
        }

        // 반개방(Half-Open) 시도 (회로 열린 후 일정 시간 경과)
        long now = System.currentTimeMillis();
        if (now - circuitOpenedAt >= CIRCUIT_OPEN_DURATION_MS) {
            log.info("Circuit breaker transitioning to HALF-OPEN state, attempting recovery");
            return true;
        }

        return false; // 회로 열림 (차단)
    }

    /**
     * 성공 기록 (서킷 브레이커).
     */
    private void recordSuccess() {
        consecutiveFailures.set(0);
        lastSuccessTime.set(System.currentTimeMillis());

        if (circuitOpen) {
            log.info("Circuit breaker transitioning to CLOSED state after successful request");
            circuitOpen = false;
            circuitOpenedAt = 0;
        }
    }

    /**
     * 실패 기록 (서킷 브레이커).
     */
    private void recordFailure() {
        if (!circuitBreakerEnabled) {
            return;
        }

        int failures = consecutiveFailures.incrementAndGet();

        if (!circuitOpen && failures >= FAILURE_THRESHOLD) {
            circuitOpen = true;
            circuitOpenedAt = System.currentTimeMillis();
            log.error("Circuit breaker OPENED after {} consecutive failures. " +
                            "Will retry after {}ms. This indicates Kafka may be unhealthy.",
                    failures, CIRCUIT_OPEN_DURATION_MS);
        }
    }

    /**
     * 서킷 브레이커 상태 조회.
     */
    private String getCircuitState() {
        if (!circuitBreakerEnabled) {
            return "DISABLED";
        }
        if (!circuitOpen) {
            return "CLOSED";
        }
        long now = System.currentTimeMillis();
        if (now - circuitOpenedAt >= CIRCUIT_OPEN_DURATION_MS) {
            return "HALF-OPEN";
        }
        return "OPEN";
    }

    /**
     * 오래된 PUBLISHED 이벤트 정리.
     * <p>
     * 설정된 cleanup-cron 주기로 실행됩니다.
     */
    @Scheduled(cron = "${curve.outbox.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupOldEvents() {
        if (!cleanupEnabled) {
            return;
        }

        log.info("Starting outbox cleanup job (retentionDays={})", retentionDays);
        Instant before = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedCount = 0;
        int batchDeleteSize = 1000; // 한 번에 삭제할 최대 개수

        try {
            // 대량 삭제 시 트랜잭션 부하 분산을 위해 반복 삭제
            while (true) {
                int count = outboxRepository.deleteByStatusAndOccurredAtBefore(
                        OutboxStatus.PUBLISHED, before, batchDeleteSize
                );
                deletedCount += count;
                if (count < batchDeleteSize) {
                    break;
                }
            }
            log.info("Outbox cleanup completed. Deleted {} events older than {}", deletedCount, before);
        } catch (Exception e) {
            log.error("Failed to cleanup old outbox events", e);
        }
    }

    /**
     * 개별 이벤트 처리.
     *
     * @param event 처리할 이벤트
     */
    private void processEvent(OutboxEvent event) {
        try {
            // Kafka로 발행 (타임아웃 적용)
            kafkaTemplate.send(topic, event.getEventId(), event.getPayload())
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);

            // 발행 성공 처리
            event.markAsPublished();
            outboxRepository.save(event);
            publishedCount.incrementAndGet();

            log.debug("Outbox event published successfully: eventId={}, aggregateType={}, aggregateId={}",
                    event.getEventId(), event.getAggregateType(), event.getAggregateId());

        } catch (Exception e) {
            // 발행 실패 처리
            handlePublishFailure(event, e);
        }
    }

    /**
     * 발행 실패 처리.
     *
     * @param event 실패한 이벤트
     * @param error 발생한 예외
     */
    private void handlePublishFailure(OutboxEvent event, Exception error) {
        // 지수 백오프 적용 (1초, 2초, 4초, 8초...)
        long backoffMs = (long) Math.pow(2, event.getRetryCount()) * 1000L;
        int retryCount = event.scheduleNextRetry(backoffMs);

        log.warn("Failed to publish outbox event (attempt {}/{}): eventId={}, nextRetryIn={}ms, error={}",
                retryCount, maxRetries, event.getEventId(), backoffMs, error.getMessage());

        if (event.exceededMaxRetries(maxRetries)) {
            // 최대 재시도 횟수 초과
            event.markAsFailed(truncate(error.getMessage(), 500));
            failedCount.incrementAndGet();

            log.error("Outbox event permanently failed after {} retries: eventId={}, aggregateType={}, aggregateId={}",
                    maxRetries, event.getEventId(), event.getAggregateType(), event.getAggregateId());
        }

        outboxRepository.save(event);
    }

    /**
     * 발행 통계 조회.
     *
     * @return 통계 정보
     */
    public PublisherStats getStats() {
        long totalPending = outboxRepository.countByStatus(OutboxStatus.PENDING);
        long totalPublished = outboxRepository.countByStatus(OutboxStatus.PUBLISHED);
        long totalFailed = outboxRepository.countByStatus(OutboxStatus.FAILED);

        return new PublisherStats(
                totalPending,
                totalPublished,
                totalFailed,
                publishedCount.get(),
                failedCount.get(),
                getCircuitState(),
                consecutiveFailures.get(),
                System.currentTimeMillis() - lastSuccessTime.get()
        );
    }

    /**
     * 통계 초기화.
     */
    public void resetStats() {
        publishedCount.set(0);
        failedCount.set(0);
        consecutiveFailures.set(0);
        circuitOpen = false;
        circuitOpenedAt = 0;
        lastSuccessTime.set(System.currentTimeMillis());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * Outbox Publisher 통계 정보.
     *
     * @param totalPending             전체 PENDING 이벤트 수
     * @param totalPublished           전체 PUBLISHED 이벤트 수
     * @param totalFailed              전체 FAILED 이벤트 수
     * @param publishedCountSinceStart 시작 후 발행 성공 수
     * @param failedCountSinceStart    시작 후 발행 실패 수
     * @param circuitBreakerState      서킷 브레이커 상태 (CLOSED, OPEN, HALF-OPEN, DISABLED)
     * @param consecutiveFailures      연속 실패 횟수
     * @param timeSinceLastSuccessMs   마지막 성공 이후 경과 시간 (밀리초)
     */
    public record PublisherStats(
            long totalPending,
            long totalPublished,
            long totalFailed,
            int publishedCountSinceStart,
            int failedCountSinceStart,
            String circuitBreakerState,
            int consecutiveFailures,
            long timeSinceLastSuccessMs
    ) {
    }
}
