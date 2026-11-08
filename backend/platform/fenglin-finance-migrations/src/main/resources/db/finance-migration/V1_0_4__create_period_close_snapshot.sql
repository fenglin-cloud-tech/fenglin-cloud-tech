-- D1：结账快照
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_period_close_snapshot (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    ledger_id bigint NOT NULL,
    period_code varchar(7) NOT NULL,
    snapshot_type varchar(16) NOT NULL,
    trial_debit numeric(20,4) NOT NULL,
    trial_credit numeric(20,4) NOT NULL,
    snapshot_json jsonb NOT NULL,
    record_hash varchar(64) NOT NULL,
    prev_hash varchar(64) NOT NULL,
    closed_by bigint NOT NULL,
    closed_at timestamptz NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_period_close_snapshot IS '期间结账快照（哈希链，快照不可篡改）';
COMMENT ON COLUMN fin_period_close_snapshot.snapshot_type IS '类型：CLOSE/YEAR_CLOSE';
