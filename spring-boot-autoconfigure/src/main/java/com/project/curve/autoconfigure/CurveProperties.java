package com.project.curve.autoconfigure;

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

    @Data
    public static class Kafka {
        /**
         * Kafka 토픽 이름
         */
        @NotBlank(message = "Kafka topic은 필수입니다")
        private String topic = "event.audit.v1";

        /**
         * Dead Letter Queue 토픽 이름 (선택사항)
         * 설정하지 않으면 DLQ 기능이 비활성화됨
         */
        private String dlqTopic;

        /**
         * Kafka Producer 재시도 횟수 (기본값: 3)
         * Kafka Producer 자체 재시도 설정 (spring.kafka.producer.retries 우선)
         */
        @PositiveOrZero(message = "retries는 0 이상이어야 합니다")
        private int retries = 3;

        /**
         * 재시도 백오프 시간(ms) (기본값: 1000ms = 1초)
         */
        @Positive(message = "retryBackoffMs는 양수여야 합니다")
        private long retryBackoffMs = 1000L;

        /**
         * 요청 타임아웃(ms) (기본값: 30000ms = 30초)
         */
        @Positive(message = "requestTimeoutMs는 양수여야 합니다")
        private int requestTimeoutMs = 30000;

        /**
         * 비동기 전송 모드 활성화 여부 (기본값: false - 동기 전송)
         * true: 비동기 전송 (높은 성능, 전송 실패 시 콜백 처리)
         * false: 동기 전송 (낮은 성능, 전송 보장)
         */
        private boolean asyncMode = false;

        /**
         * 비동기 전송 타임아웃(ms) (기본값: 5000ms = 5초)
         * asyncMode=true일 때만 사용
         */
        @Positive(message = "asyncTimeoutMs는 양수여야 합니다")
        private long asyncTimeoutMs = 5000L;

        /**
         * 동기 전송 타임아웃(초) (기본값: 30초)
         * asyncMode=false일 때 사용
         */
        @Positive(message = "syncTimeoutSeconds는 양수여야 합니다")
        private long syncTimeoutSeconds = 30L;

        /**
         * DLQ 전송 실패 시 로컬 백업 디렉토리 경로 (기본값: ./dlq-backup)
         * DLQ 전송도 실패한 경우 이벤트를 로컬 파일로 백업
         */
        private String dlqBackupPath = "./dlq-backup";

        /**
         * DLQ 전송 전용 ExecutorService 스레드 풀 크기 (기본값: 2)
         * 비동기 모드에서 DLQ 전송 시 메인 콜백 스레드 블로킹을 방지하기 위한 별도 스레드 풀
         */
        @Min(value = 1, message = "dlqExecutorThreads는 1 이상이어야 합니다")
        private int dlqExecutorThreads = 2;

        /**
         * DLQ ExecutorService 우아한 종료 타임아웃(초) (기본값: 30초)
         * 애플리케이션 종료 시 실행 중인 DLQ 작업 완료를 대기하는 시간
         * 타임아웃 초과 시 강제 종료
         */
        @Positive(message = "dlqExecutorShutdownTimeoutSeconds는 양수여야 합니다")
        private long dlqExecutorShutdownTimeoutSeconds = 30L;
    }

    @Data
    public static class Retry {
        /**
         * 전송 실패 시 재시도 활성화 여부 (기본값: true)
         */
        private boolean enabled = true;

        /**
         * 최대 재시도 횟수 (기본값: 3)
         */
        @Min(value = 1, message = "maxAttempts는 1 이상이어야 합니다")
        private int maxAttempts = 3;

        /**
         * 재시도 백오프 초기 지연 시간(ms) (기본값: 1000ms = 1초)
         */
        @Positive(message = "initialInterval은 양수여야 합니다")
        private long initialInterval = 1000L;

        /**
         * 재시도 백오프 배수 (기본값: 2.0)
         * 예: 1초 -> 2초 -> 4초
         */
        @Min(value = 1, message = "multiplier는 1 이상이어야 합니다")
        private double multiplier = 2.0;

        /**
         * 재시도 백오프 최대 지연 시간(ms) (기본값: 10000ms = 10초)
         */
        @Positive(message = "maxInterval은 양수여야 합니다")
        private long maxInterval = 10000L;
    }

    @Data
    public static class Aop {
        /**
         * @PublishEvent AOP 활성화 여부 (기본값: true)
         */
        private boolean enabled = true;
    }

    @Data
    public static class IdGenerator {
        /**
         * Snowflake ID Generator의 Worker ID (0 ~ 1023)
         * 분산 환경에서 각 인스턴스마다 고유한 값을 설정해야 함
         * 기본값: 1
         */
        @Min(value = 0, message = "workerId는 0 이상이어야 합니다")
        @Max(value = 1023, message = "workerId는 1023 이하여야 합니다")
        private long workerId = 1L;

        /**
         * Worker ID 자동 생성 모드 (기본값: false)
         * true: MAC 주소 기반으로 자동 생성 (충돌 가능성 있음)
         * false: workerId 설정값 사용
         */
        private boolean autoGenerate = false;
    }

    @Data
    public static class Security {
        /**
         * X-Forwarded-For 헤더 사용 여부 (기본값: false)
         * true: Spring Boot의 ForwardedHeaderFilter 사용 권장
         * false: request.getRemoteAddr()만 사용 (가장 안전)
         * <p>
         * 프록시/로드밸런서 뒤에서 실행되는 경우:
         * server.forward-headers-strategy=framework 설정 필수
         * <p>
         * 보안 주의사항:
         * - 신뢰할 수 없는 프록시 환경에서는 false로 설정
         * - X-Forwarded-For 헤더 스푸핑 공격에 주의
         * - 프로덕션 환경에서는 server.tomcat.remoteip.internal-proxies 설정 필수
         */
        private boolean useForwardedHeaders = false;
    }

    @Data
    public static class Pii {
        /**
         * PII 처리 기능 활성화 여부 (기본값: true)
         */
        private boolean enabled = true;

        /**
         * 암호화/해싱 설정
         */
        private final Crypto crypto = new Crypto();

        @Data
        public static class Crypto {
            /**
             * 기본 암호화 키 (Base64 인코딩된 AES-256 키)
             * 환경변수 PII_ENCRYPTION_KEY 사용 권장
             * 미설정 시 암호화 기능 사용 불가
             */
            private String defaultKey;

            /**
             * 해싱에 사용할 솔트
             * 환경변수 PII_HASH_SALT 사용 권장
             * 미설정 시 솔트 없이 해싱
             */
            private String salt;
        }
    }
}