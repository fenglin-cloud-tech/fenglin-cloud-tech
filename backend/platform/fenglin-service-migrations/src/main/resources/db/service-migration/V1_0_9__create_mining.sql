-- F3：流程挖掘事件日志、挖掘结果
SET search_path TO fenglin_service, public;

CREATE TABLE svc_mining_event_log (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    case_id varchar(64) NOT NULL,
    activity varchar(128) NOT NULL,
    activity_code varchar(64),
    event_time timestamptz NOT NULL,
    resource varchar(64),
    lifecycle varchar(16) NOT NULL DEFAULT 'COMPLETE',
    source_system varchar(32) NOT NULL,
    attributes jsonb,
    ingested_at timestamptz NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_mining_event_log IS '流程挖掘事件日志（文档要求月分区，此处普通表+时间索引）';
COMMENT ON COLUMN svc_mining_event_log.lifecycle IS '生命周期：START/COMPLETE/SCHEDULE';
COMMENT ON COLUMN svc_mining_event_log.source_system IS '来源：TICKET/FLOW/EXTERNAL/RPA';

CREATE TABLE svc_mining_result (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    result_type varchar(16) NOT NULL,
    name varchar(255) NOT NULL,
    source_system varchar(32),
    period_start timestamptz,
    period_end timestamptz,
    model_json jsonb,
    metrics_json jsonb,
    candidate_json jsonb,
    status varchar(16) NOT NULL DEFAULT 'READY',
    computed_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_mining_result IS '挖掘结果';
COMMENT ON COLUMN svc_mining_result.result_type IS '类型：MODEL/CONFORMANCE/BOTTLENECK/AUTOMATION_CANDIDATE';
COMMENT ON COLUMN svc_mining_result.status IS '状态：READY/ARCHIVED';
