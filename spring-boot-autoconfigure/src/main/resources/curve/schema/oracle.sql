-- Curve Outbox: Oracle schema
-- 운영 환경에서 Flyway/Liquibase 마이그레이션 참조용 스크립트입니다.

CREATE TABLE curve_outbox_events (
    event_id        VARCHAR2(64)    NOT NULL PRIMARY KEY,
    aggregate_type  VARCHAR2(100)   NOT NULL,
    aggregate_id    VARCHAR2(100)   NOT NULL,
    event_type      VARCHAR2(100)   NOT NULL,
    payload         CLOB            NOT NULL,
    occurred_at     TIMESTAMP       NOT NULL,
    status          VARCHAR2(20)    NOT NULL,
    retry_count     NUMBER(10)      DEFAULT 0 NOT NULL,
    published_at    TIMESTAMP,
    error_message   VARCHAR2(500),
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL,
    version         NUMBER(19)
);

CREATE INDEX idx_outbox_status ON curve_outbox_events (status);
CREATE INDEX idx_outbox_aggregate ON curve_outbox_events (aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_occurred_at ON curve_outbox_events (occurred_at);
