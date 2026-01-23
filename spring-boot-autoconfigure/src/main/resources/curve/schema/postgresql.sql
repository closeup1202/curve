-- Curve Outbox: PostgreSQL schema
-- 운영 환경에서 Flyway/Liquibase 마이그레이션 참조용 스크립트입니다.

CREATE TABLE IF NOT EXISTS curve_outbox_events (
    event_id        VARCHAR(64)     NOT NULL PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(100)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         TEXT            NOT NULL,
    occurred_at     TIMESTAMP       NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    retry_count     INT             NOT NULL DEFAULT 0,
    published_at    TIMESTAMP,
    error_message   VARCHAR(500),
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL,
    version         BIGINT
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON curve_outbox_events (status);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON curve_outbox_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_occurred_at ON curve_outbox_events (occurred_at);
