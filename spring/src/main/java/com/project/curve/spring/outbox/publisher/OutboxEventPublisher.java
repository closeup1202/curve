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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Outbox 이벤트를 주기적으로 Kafka로 발행하는 Publisher.
 * <p>
 * Transactional Outbox Pattern의 핵심 컴포넌트입니다.
 *
 * <h3>동작 방식</h3>
 * <ol>
 *   <li>고정 주기(기본 1초)로 PENDING 상태의 이벤트 조회</li>
 *   <li>조회된 이벤트를 Kafka로 발행 시도</li>
 *   <li>성공 시 PUBLISHED 상태로 변경</li>
 *   <li>실패 시 재시도 카운트 증가, 최대 횟수 초과 시 FAILED 상태로 변경</li>
 * </ol>
 *
 * <h3>설정</h3>
 * <pre>
 * curve:
 *   outbox:
 *     enabled: true
 *     poll-interval-ms: 1000      # 폴링 주기
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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;
    private final int maxRetries;
    private final int sendTimeoutSeconds;
    private final boolean cleanupEnabled;
    private final int retentionDays;

    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    public OutboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            String topic,
            int batchSize,
            int maxRetries,
            int sendTimeoutSeconds,
            boolean cleanupEnabled,
            int retentionDays
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
        this.cleanupEnabled = cleanupEnabled;
        this.retentionDays = retentionDays;

        log.info("OutboxEventPublisher initialized: topic={}, batchSize={}, maxRetries={}, sendTimeoutSeconds={}, cleanupEnabled={}, retentionDays={}",
                topic, batchSize, maxRetries, sendTimeoutSeconds, cleanupEnabled, retentionDays);
    }

    /**
     * PENDING 이벤트를 주기적으로 Kafka로 발행.
     * <p>
     * 설정된 poll-interval-ms 주기로 실행됩니다.
     */
    @Scheduled(fixedDelayString = "${curve.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        // 스케줄러 실행 시 MDC 컨텍스트가 없을 수 있으므로, 필요한 경우 여기서 설정
        // 예: MDC.put("traceId", UUID.randomUUID().toString());
        
        try {
            List<OutboxEvent> pendingEvents = outboxRepository.findPendingForProcessing(batchSize);

            if (pendingEvents.isEmpty()) {
                return;
            }

            log.debug("Processing {} pending outbox events", pendingEvents.size());

            for (OutboxEvent event : pendingEvents) {
                processEvent(event);
            }

        } catch (Exception e) {
            log.error("Failed to process pending outbox events", e);
        }
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
            // 반복적으로 삭제하여 대량 삭제 시 트랜잭션 부하 분산
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

            // 발행 성공
            event.markAsPublished();
            outboxRepository.save(event);
            publishedCount.incrementAndGet();

            log.debug("Outbox event published successfully: eventId={}, aggregateType={}, aggregateId={}",
                    event.getEventId(), event.getAggregateType(), event.getAggregateId());

        } catch (Exception e) {
            // 발행 실패
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
            // 최대 재시도 초과
            event.markAsFailed(truncate(error.getMessage(), 500));
            failedCount.incrementAndGet();

            log.error("Outbox event permanently failed after {} retries: eventId={}, aggregateType={}, aggregateId={}",
                    maxRetries, event.getEventId(), event.getAggregateType(), event.getAggregateId());
        }

        outboxRepository.save(event);
    }

    /**
     * 게시 통계 조회.
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
                failedCount.get()
        );
    }

    /**
     * 통계 초기화.
     */
    public void resetStats() {
        publishedCount.set(0);
        failedCount.set(0);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * Outbox Publisher 통계.
     */
    public record PublisherStats(
            long totalPending,
            long totalPublished,
            long totalFailed,
            int publishedCountSinceStart,
            int failedCountSinceStart
    ) {
    }
}
