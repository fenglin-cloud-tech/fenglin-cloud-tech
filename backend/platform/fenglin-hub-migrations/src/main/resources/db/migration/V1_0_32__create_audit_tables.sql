-- P1 审计域：审计日志（按月分区）与哈希链。日志只追加，租户隔离，平台级事件 tenant_id = 0
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_audit_log (
    id            bigint       NOT NULL,
    tenant_id     bigint       NOT NULL,
    log_no        varchar(40)  NOT NULL,
    event_type    varchar(64)  NOT NULL,
    module        varchar(32)  NOT NULL,
    action        varchar(64)  NOT NULL,
    operator_id   bigint,
    operator_name varchar(128),
    target_type   varchar(64),
    target_id     varchar(128),
    request_id    varchar(64),
    trace_id      varchar(64),
    client_type   varchar(16),
    ip_enc        varchar(256),
    result        varchar(16)  NOT NULL DEFAULT 'SUCCESS',
    risk_level    varchar(16)  NOT NULL DEFAULT 'LOW',
    detail        jsonb,
    occurred_at   timestamptz  NOT NULL DEFAULT now(),
    created_by    bigint,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    deleted       smallint     NOT NULL DEFAULT 0,
    version       integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_audit_log PRIMARY KEY (id, occurred_at),
    CONSTRAINT uk_hub_audit_log_no UNIQUE (log_no, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE TABLE hub_audit_log_default PARTITION OF hub_audit_log DEFAULT;

CREATE INDEX idx_hub_audit_log_tenant_time ON hub_audit_log (tenant_id, occurred_at DESC);
CREATE INDEX idx_hub_audit_log_event ON hub_audit_log (tenant_id, event_type, occurred_at DESC);
CREATE INDEX idx_hub_audit_log_request ON hub_audit_log (request_id);

CREATE TABLE hub_audit_chain (
    id            bigint       NOT NULL PRIMARY KEY,
    tenant_id     bigint       NOT NULL,
    log_id        bigint       NOT NULL,
    partition_key varchar(16)  NOT NULL,
    seq_no        bigint       NOT NULL,
    content_hash  varchar(128) NOT NULL,
    prev_hash     varchar(128),
    chain_hash    varchar(128) NOT NULL,
    anchor_at     timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted       smallint     NOT NULL DEFAULT 0,
    version       integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_audit_chain_seq UNIQUE (tenant_id, partition_key, seq_no)
);
CREATE INDEX idx_hub_audit_chain_log ON hub_audit_chain (tenant_id, log_id);
CREATE TRIGGER trg_hub_audit_chain_updated_at BEFORE UPDATE ON hub_audit_chain
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 分区表授权不自动下推到分区，默认分区需显式授权；新增月分区同样需要授权（分区维护任务待办）
GRANT SELECT, INSERT, UPDATE, DELETE ON
    hub_audit_log, hub_audit_log_default, hub_audit_chain
    TO fl_app, fl_platform_role;

ALTER TABLE hub_audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_audit_log_tenant ON hub_audit_log
    USING (tenant_id = 0 OR tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = 0 OR tenant_id = current_tenant_id());
CREATE POLICY p_hub_audit_log_platform ON hub_audit_log
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

ALTER TABLE hub_audit_chain ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_audit_chain_tenant ON hub_audit_chain
    USING (tenant_id = 0 OR tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = 0 OR tenant_id = current_tenant_id());
CREATE POLICY p_hub_audit_chain_platform ON hub_audit_chain
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

COMMENT ON TABLE hub_audit_log IS '审计日志（按月分区，P1 使用默认分区）';
COMMENT ON TABLE hub_audit_chain IS '审计哈希链（防篡改校验）';
