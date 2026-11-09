-- E1：Webhook 事件（文档要求月分区，按既有范式以普通表+时间索引实现）
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_webhook_event (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    source varchar(16) NOT NULL,
    event_type varchar(64) NOT NULL,
    event_id varchar(128) NOT NULL,
    payload jsonb NOT NULL,
    signature_valid smallint NOT NULL DEFAULT 0,
    process_status varchar(16) NOT NULL DEFAULT 'pending',
    retry_count int NOT NULL DEFAULT 0,
    error_msg varchar(1024),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_webhook_event IS 'Webhook 事件（多态来源 vcs/ci/quality）';
COMMENT ON COLUMN devops_webhook_event.source IS '来源：gitlab/github/jenkins/sonarqube';
COMMENT ON COLUMN devops_webhook_event.process_status IS '处理状态：pending/processed/failed/dlq';
