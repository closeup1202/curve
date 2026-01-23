-- Curve Outbox: MySQL / MariaDB schema
-- 운영 환경에서 Flyway/Liquibase 마이그레이션 참조용 스크립트입니다.

CREATE TABLE IF NOT EXISTS curve_outbox_events (
    event_id        VARCHAR(64)     NOT NULL,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(100)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         TEXT            NOT NULL,
    occurred_at     TIMESTAMP(6)    NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    retry_count     INT             NOT NULL DEFAULT 0,
    published_at    TIMESTAMP(6)    NULL,
    error_message   VARCHAR(500),
    created_at      TIMESTAMP(6)    NOT NULL,
    updated_at      TIMESTAMP(6)    NOT NULL,
    version         BIGINT,
    PRIMARY KEY (event_id),
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_aggregate (aggregate_type, aggregate_id),
    INDEX idx_outbox_occurred_at (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
