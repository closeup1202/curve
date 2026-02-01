package com.project.curve.autoconfigure;

import com.project.curve.autoconfigure.outbox.InitializeSchema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "curve")
public class CurveProperties {

    private boolean enabled = true;

    @Valid
    private final Kafka kafka = new Kafka();

    @Valid
    private final Retry retry = new Retry();

    @Valid
    private final Aop aop = new Aop();

    @Valid
    private final IdGenerator idGenerator = new IdGenerator();

    @Valid
    private final Security security = new Security();

    @Valid
    private final Pii pii = new Pii();

    @Valid
    private final Outbox outbox = new Outbox();

    @Valid
    private final Serde serde = new Serde();

    @Data
    public static class Kafka {
        /**
         * Kafka 토픽 이름.
         */
        @NotBlank(message = "Kafka topic is required")
        private String topic = "event.audit.v1";

        /**
         * Dead Letter Queue 토픽 이름 (선택사항).
         * <p>
         * 설정하지 않으면 DLQ 기능이 비활성화됩니다.
         */
        private String dlqTopic;

        /**
         * Kafka Producer 재시도 횟수 (기본값: 3).
         * <p>
         * Kafka Producer 자체의 재시도 설정입니다 (spring.kafka.producer.retries가 우선순위를 가짐).
         */
        @PositiveOrZero(message = "retries must be 0 or greater")
        private int retries = 3;

        /**
         * 재시도 백오프 시간 (밀리초) (기본값: 1000ms = 1초).
         */
        @Positive(message = "retryBackoffMs must be positive")
        private long retryBackoffMs = 1000L;

        /**
         * 요청 타임아웃 (밀리초) (기본값: 30000ms = 30초).
         */
        @Positive(message = "requestTimeoutMs must be positive")
        private int requestTimeoutMs = 30000;

        /**
         * 비동기 전송 모드 활성화 여부 (기본값: false - 동기 전송).
         * <p>
         * true: 비동기 전송 (고성능, 전송 실패 시 콜백 처리)
         * false: 동기 전송 (낮은 성능, 전송 보장)
         */
        private boolean asyncMode = false;

        /**
         * 비동기 전송 타임아웃 (밀리초) (기본값: 5000ms = 5초).
         * <p>
         * asyncMode=true일 때만 사용됩니다.
         */
        @Positive(message = "asyncTimeoutMs must be positive")
        private long asyncTimeoutMs = 5000L;

        /**
         * 동기 전송 타임아웃 (초) (기본값: 30초).
         * <p>
         * asyncMode=false일 때 사용됩니다.
         */
        @Positive(message = "syncTimeoutSeconds must be positive")
        private long syncTimeoutSeconds = 30L;

        /**
         * DLQ 전송 실패 시 로컬 백업 디렉토리 경로 (기본값: ./dlq-backup).
         * <p>
         * DLQ 전송도 실패할 경우 이벤트를 로컬 파일로 백업합니다.
         */
        private String dlqBackupPath = "./dlq-backup";

        /**
         * DLQ 전용 ExecutorService 스레드 풀 크기 (기본값: 2).
         * <p>
         * 비동기 모드에서 DLQ 전송 시 메인 콜백 스레드 차단을 방지하기 위한 별도 스레드 풀입니다.
         */
        @Min(value = 1, message = "dlqExecutorThreads must be at least 1")
        private int dlqExecutorThreads = 2;

        /**
         * DLQ ExecutorService 우아한 종료 타임아웃 (초) (기본값: 30초).
         * <p>
         * 애플리케이션 종료 시 진행 중인 DLQ 작업 완료를 대기하는 시간입니다.
         * 타임아웃 초과 시 강제 종료됩니다.
         */
        @Positive(message = "dlqExecutorShutdownTimeoutSeconds must be positive")
        private long dlqExecutorShutdownTimeoutSeconds = 30L;

        /**
         * 운영 환경 여부 (기본값: false).
         * <p>
         * true: 운영 모드 - DLQ 백업 파일 보안 설정 실패 시 예외 발생
         * false: 개발 모드 - DLQ 백업 파일 보안 설정 실패 시 경고 로그만 출력
         * <p>
         * 보안 고려사항:
         * - 운영 환경에서는 반드시 true로 설정해야 합니다.
         * - Windows 환경에서는 ACL 지원이 필요합니다.
         * - POSIX 시스템 (Linux, macOS) 권장
         */
        private boolean isProduction = false;
    }

    @Data
    public static class Retry {
        /**
         * 전송 실패 시 재시도 활성화 여부 (기본값: true).
         */
        private boolean enabled = true;

        /**
         * 최대 재시도 횟수 (기본값: 3).
         */
        @Min(value = 1, message = "maxAttempts must be at least 1")
        private int maxAttempts = 3;

        /**
         * 재시도 백오프 초기 지연 시간 (밀리초) (기본값: 1000ms = 1초).
         */
        @Positive(message = "initialInterval must be positive")
        private long initialInterval = 1000L;

        /**
         * 재시도 백오프 승수 (기본값: 2.0).
         * <p>
         * 예: 1초 -> 2초 -> 4초
         */
        @Min(value = 1, message = "multiplier must be at least 1")
        private double multiplier = 2.0;

        /**
         * 재시도 백오프 최대 지연 시간 (밀리초) (기본값: 10000ms = 10초).
         */
        @Positive(message = "maxInterval must be positive")
        private long maxInterval = 10000L;
    }

    @Data
    public static class Aop {
        /**
         * @PublishEvent AOP 활성화 여부 (기본값: true).
         */
        private boolean enabled = true;
    }

    @Data
    public static class IdGenerator {
        /**
         * Snowflake ID 생성기 워커 ID (0 ~ 1023).
         * <p>
         * 분산 환경에서 각 인스턴스마다 고유한 값으로 설정해야 합니다.
         * 기본값: 1
         */
        @Min(value = 0, message = "workerId must be at least 0")
        @Max(value = 1023, message = "workerId must be at most 1023")
        private long workerId = 1L;

        /**
         * 워커 ID 자동 생성 모드 (기본값: false).
         * <p>
         * true: MAC 주소 기반 자동 생성 (충돌 가능성 있음)
         * false: 설정된 workerId 값 사용
         */
        private boolean autoGenerate = false;
    }

    @Data
    public static class Security {
        /**
         * X-Forwarded-For 헤더 사용 여부 (기본값: false).
         * <p>
         * true: Spring Boot의 ForwardedHeaderFilter 사용 권장
         * false: request.getRemoteAddr()만 사용 (가장 안전함)
         * <p>
         * 프록시/로드밸런서 뒤에서 실행 시:
         * server.forward-headers-strategy=framework 설정 필요
         * <p>
         * 보안 고려사항:
         * - 신뢰할 수 없는 프록시 환경에서는 false로 설정하세요.
         * - X-Forwarded-For 헤더 스푸핑 공격에 주의하세요.
         * - 운영 환경에서는 server.tomcat.remoteip.internal-proxies 설정이 필요합니다.
         */
        private boolean useForwardedHeaders = false;
    }

    @Data
    public static class Pii {
        /**
         * PII 처리 기능 활성화 여부 (기본값: true).
         */
        private boolean enabled = true;

        /**
         * 암호화/해싱 설정.
         */
        private final Crypto crypto = new Crypto();

        @Data
        public static class Crypto {
            /**
             * 기본 암호화 키 (Base64 인코딩된 AES-256 키).
             * <p>
             * PII_ENCRYPTION_KEY 환경 변수 사용 권장.
             * 설정하지 않으면 암호화 기능을 사용할 수 없습니다.
             */
            private String defaultKey;

            /**
             * 해싱에 사용할 솔트(Salt).
             * <p>
             * PII_HASH_SALT 환경 변수 사용 권장.
             * 설정하지 않으면 솔트 없이 해싱합니다.
             */
            private String salt;
        }
    }

    @Data
    public static class Outbox {
        /**
         * Transactional Outbox Pattern 활성화 여부 (기본값: false).
         * <p>
         * true: DB 트랜잭션과 이벤트 발행 간의 원자성 보장
         * - @PublishEvent에서 outbox=true 사용 가능
         * - 주기적으로 PENDING 상태 이벤트를 Kafka로 발행
         * <p>
         * false: 레거시 모드 (즉시 Kafka로 발행)
         */
        private boolean enabled = false;

        /**
         * Outbox 이벤트 발행자 활성화 여부 (기본값: true).
         * <p>
         * true: 애플리케이션 내에서 주기적으로 PENDING 이벤트를 조회하여 Kafka로 발행 (Polling 방식)
         * false: DB에 저장만 하고 발행하지 않음. CDC(Debezium 등)를 통해 외부에서 발행할 때 사용
         */
        private boolean publisherEnabled = true;

        /**
         * Outbox 테이블 스키마 초기화 모드 (기본값: embedded).
         * <p>
         * embedded: 임베디드 DB(H2, HSQLDB)에서만 자동 생성
         * always: 항상 자동 생성 (테이블 없을 때만 CREATE)
         * never: 서비스에서 직접 관리 (Flyway/Liquibase 등)
         */
        private InitializeSchema initializeSchema = InitializeSchema.EMBEDDED;

        /**
         * Outbox 이벤트 폴링 간격 (밀리초) (기본값: 1000ms = 1초).
         * <p>
         * PENDING 상태 이벤트를 얼마나 자주 조회할지 설정합니다.
         */
        @Positive(message = "pollIntervalMs must be positive")
        private long pollIntervalMs = 1000L;

        /**
         * 한 번에 처리할 이벤트 배치 크기 (기본값: 100).
         * <p>
         * PENDING 이벤트가 많을 때 한 번에 가져올 최대 개수입니다.
         */
        @Min(value = 1, message = "batchSize must be at least 1")
        @Max(value = 1000, message = "batchSize must be at most 1000")
        private int batchSize = 100;

        /**
         * Outbox 이벤트 발행 최대 재시도 횟수 (기본값: 3).
         * <p>
         * PENDING 상태에서 발행 실패 시 최대 재시도 횟수입니다.
         * 초과 시 FAILED 상태로 변경됩니다.
         */
        @Min(value = 1, message = "maxRetries must be at least 1")
        private int maxRetries = 3;

        /**
         * Kafka 전송 타임아웃 (초) (기본값: 10초).
         * <p>
         * Kafka로 이벤트 발행 시 응답을 기다리는 최대 시간입니다.
         * 초과 시 재시도 대상으로 간주합니다.
         */
        @Positive(message = "sendTimeoutSeconds must be positive")
        private int sendTimeoutSeconds = 10;

        /**
         * PUBLISHED 이벤트 자동 정리 활성화 여부 (기본값: false).
         * <p>
         * true: 테이블 크기 관리를 위해 오래된 PUBLISHED 이벤트를 주기적으로 삭제
         * false: 수동 정리 필요
         */
        private boolean cleanupEnabled = false;

        /**
         * PUBLISHED 이벤트 보관 기간 (일) (기본값: 7일).
         * <p>
         * cleanupEnabled=true일 때, 이 기간보다 오래된 PUBLISHED 이벤트를 삭제합니다.
         */
        @Min(value = 1, message = "retentionDays must be at least 1")
        private int retentionDays = 7;

        /**
         * 정리 작업 실행 스케줄 (Cron 표현식) (기본값: 매일 새벽 2시).
         * <p>
         * cleanupEnabled=true일 때 사용됩니다.
         */
        private String cleanupCron = "0 0 2 * * *";

        /**
         * 동적 배치 크기 조정 활성화 여부 (기본값: true).
         * <p>
         * true: 큐 깊이(대기 중인 이벤트 수)에 따라 배치 크기를 자동으로 조정
         *   - pending > 1000: batchSize * 2 (최대 500)
         *   - pending > 500: batchSize * 1.5 (최대 300)
         *   - pending < 10: min(batchSize, 10)
         * false: 고정된 batchSize 사용
         * <p>
         * 부하가 높은 상황에서 처리량을 자동으로 늘려 큐를 빠르게 비웁니다.
         */
        private boolean dynamicBatchingEnabled = true;

        /**
         * 서킷 브레이커 활성화 여부 (기본값: true).
         * <p>
         * true: Kafka 장애 감지 시 자동으로 요청을 차단하여 시스템 보호
         *   - 5회 연속 실패 시 회로 개방(OPEN)
         *   - 1분 후 반개방(Half-Open) 상태로 자동 전환하여 복구 시도
         * false: 실패하더라도 계속 재시도
         * <p>
         * Kafka가 장시간 다운되었을 때 무의미한 재시도를 방지하고 시스템 부하를 줄입니다.
         */
        private boolean circuitBreakerEnabled = true;
    }

    @Data
    public static class Serde {
        /**
         * 직렬화 타입 (기본값: JSON).
         * <p>
         * - JSON: Jackson 기반 JSON 직렬화
         * - AVRO: Avro 기반 바이너리 직렬화 (구현 필요)
         * - PROTOBUF: Protocol Buffers 기반 직렬화 (구현 필요)
         */
        private SerdeType type = SerdeType.JSON;

        /**
         * Schema Registry URL (Avro 사용 시 필수).
         */
        private String schemaRegistryUrl;

        public enum SerdeType {
            JSON, AVRO, PROTOBUF
        }
    }
}
