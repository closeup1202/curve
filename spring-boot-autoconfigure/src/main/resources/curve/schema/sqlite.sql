-- Curve Outbox: SQLite schema
-- SQLite 환경 참조용 스크립트입니다.
-- 주의: SQLite는 행 단위 잠금을 지원하지 않으므로 다중 인스턴스 환경에 적합하지 않습니다.

CREATE TABLE IF NOT EXISTS curve_outbox_events (
    event_id        TEXT        NOT NULL PRIMARY KEY,
    aggregate_type  TEXT        NOT NULL,
    aggregate_id    TEXT        NOT NULL,
    event_type      TEXT        NOT NULL,
    payload         TEXT        NOT NULL,
    occurred_at     TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    retry_count     INTEGER     NOT NULL DEFAULT 0,
    published_at    TEXT,
    error_message   TEXT,
    created_at      TEXT        NOT NULL,
    updated_at      TEXT        NOT NULL,
    version         INTEGER
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON curve_outbox_events (status);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON curve_outbox_events (aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_outbox_occurred_at ON curve_outbox_events (occurred_at);
