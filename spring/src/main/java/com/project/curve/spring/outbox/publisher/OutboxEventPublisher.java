package com.project.curve.spring.outbox.publisher;

import com.project.curve.core.outbox.OutboxEvent;
import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.core.outbox.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    public OutboxEventPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            String topic,
            int batchSize,
            int maxRetries
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;

        log.info("OutboxEventPublisher initialized: topic={}, batchSize={}, maxRetries={}",
                topic, batchSize, maxRetries);
    }

    /**
     * PENDING 이벤트를 주기적으로 Kafka로 발행.
     * <p>
     * 설정된 poll-interval-ms 주기로 실행됩니다.
     */
    @Scheduled(fixedDelayString = "${curve.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        try {
            List<OutboxEvent> pendingEvents = outboxRepository.findByStatus(
                    OutboxStatus.PENDING,
                    batchSize
            );

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
     * 개별 이벤트 처리.
     *
     * @param event 처리할 이벤트
     */
    private void processEvent(OutboxEvent event) {
        try {
            // Kafka로 발행
            kafkaTemplate.send(topic, event.getEventId(), event.getPayload())
                    .get(); // 동기 전송 (결과 확인)

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
        int retryCount = event.incrementRetryCount();

        log.warn("Failed to publish outbox event (attempt {}/{}): eventId={}, error={}",
                retryCount, maxRetries, event.getEventId(), error.getMessage());

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
