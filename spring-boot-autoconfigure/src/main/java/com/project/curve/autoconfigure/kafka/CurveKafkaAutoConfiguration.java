package com.project.curve.autoconfigure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.curve.autoconfigure.CurveProperties;
import com.project.curve.core.context.EventContextProvider;
import com.project.curve.core.port.EventProducer;
import com.project.curve.core.serde.EventSerializer;
import com.project.curve.kafka.producer.KafkaEventProducer;
import com.project.curve.spring.factory.EventEnvelopeFactory;
import com.project.curve.spring.metrics.CurveMetricsCollector;
import com.project.curve.spring.infrastructure.GracefulExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Configuration
public class CurveKafkaAutoConfiguration {

    /**
     * DLQ 전송을 위한 전용 ExecutorService (우아한 종료 지원)
     * <p>
     * <b>주요 기능:</b>
     * <ul>
     *   <li>비동기 Kafka 전송 콜백에서 동기적으로 DLQ 전송 시 스레드 차단 방지</li>
     *   <li>고정 크기 스레드 풀 사용 (기본값: 2개 스레드)</li>
     *   <li>애플리케이션 종료 시 실행 중인 작업 완료 대기 (30초 타임아웃)</li>
     *   <li>타임아웃 초과 시 강제 종료 및 로깅</li>
     * </ul>
     * <p>
     * {@link GracefulExecutorService}를 사용하여 우아한 종료를 보장합니다.
     */
    @Bean(name = "curveDlqExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "curveDlqExecutor")
    public ExecutorService dlqExecutor(CurveProperties properties) {
        int threadPoolSize = properties.getKafka().getDlqExecutorThreads();
        long terminationTimeoutSeconds = properties.getKafka().getDlqExecutorShutdownTimeoutSeconds();

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "curve-dlq-" + threadNumber.getAndIncrement());
                // 종료 대기를 위해 데몬 스레드가 아닌 일반 스레드로 설정
                thread.setDaemon(false);
                return thread;
            }
        };

        ExecutorService rawExecutor = Executors.newFixedThreadPool(threadPoolSize, threadFactory);
        ExecutorService gracefulExecutor = new GracefulExecutorService(rawExecutor, terminationTimeoutSeconds);

        log.debug("DLQ ExecutorService created with {} threads (graceful shutdown timeout: {}s)",
                threadPoolSize, terminationTimeoutSeconds);

        return gracefulExecutor;
    }

    @Bean
    @ConditionalOnMissingBean(EventProducer.class)
    public EventProducer eventProducer(
            EventEnvelopeFactory envelopeFactory,
            EventContextProvider eventContextProvider,
            KafkaTemplate<String, Object> kafkaTemplate,
            EventSerializer eventSerializer,
            ObjectMapper objectMapper,
            CurveProperties properties,
            CurveMetricsCollector metricsCollector,
            @Autowired(required = false) @Qualifier("curveRetryTemplate") RetryTemplate retryTemplate,
            @Autowired(required = false) @Qualifier("curveDlqExecutor") ExecutorService dlqExecutor
    ) {
        var kafkaConfig = properties.getKafka();
        boolean hasRetry = retryTemplate != null && properties.getRetry().isEnabled();

        return KafkaEventProducer.builder()
                .envelopeFactory(envelopeFactory)
                .eventContextProvider(eventContextProvider)
                .kafkaTemplate(kafkaTemplate)
                .eventSerializer(eventSerializer)
                .objectMapper(objectMapper)
                .topic(kafkaConfig.getTopic())
                .dlqTopic(kafkaConfig.getDlqTopic())
                .retryTemplate(hasRetry ? retryTemplate : null)
                .asyncMode(kafkaConfig.isAsyncMode())
                .asyncTimeoutMs(kafkaConfig.getAsyncTimeoutMs())
                .syncTimeoutSeconds(kafkaConfig.getSyncTimeoutSeconds())
                .dlqBackupPath(kafkaConfig.getDlqBackupPath())
                .dlqExecutor(dlqExecutor)
                .metricsCollector(metricsCollector)
                .isProduction(kafkaConfig.isProduction())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory
    ) {
        log.debug("Creating default KafkaTemplate");
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @ConditionalOnMissingBean(ProducerFactory.class)
    public ProducerFactory<String, Object> producerFactory(
            KafkaProperties kafkaProperties,
            CurveProperties curveProperties
    ) {
        var kafkaConfig = curveProperties.getKafka();

        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.RETRIES_CONFIG, kafkaConfig.getRetries());
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, kafkaConfig.getRetryBackoffMs());
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, kafkaConfig.getRequestTimeoutMs());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        log.debug("ProducerFactory configured: retries={}, retryBackoffMs={}, requestTimeoutMs={}",
                kafkaConfig.getRetries(), kafkaConfig.getRetryBackoffMs(), kafkaConfig.getRequestTimeoutMs());

        return new DefaultKafkaProducerFactory<>(props);
    }
}
