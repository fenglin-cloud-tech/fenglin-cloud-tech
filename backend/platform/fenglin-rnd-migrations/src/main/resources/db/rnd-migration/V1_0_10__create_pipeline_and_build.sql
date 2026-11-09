-- E1：流水线、流水线运行、构建记录
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_pipeline (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    ci_type varchar(16) NOT NULL,
    external_id varchar(128),
    repo_id bigint,
    trigger_mode varchar(16) NOT NULL DEFAULT 'manual',
    status varchar(16) NOT NULL DEFAULT 'active',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_pipeline IS '流水线';
COMMENT ON COLUMN devops_pipeline.ci_type IS 'CI 类型：jenkins/gitlab_ci';
COMMENT ON COLUMN devops_pipeline.trigger_mode IS '触发方式：manual/webhook/schedule';
COMMENT ON COLUMN devops_pipeline.status IS '状态：active/disabled';

CREATE TABLE devops_pipeline_run (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    pipeline_id bigint NOT NULL,
    run_no varchar(64) NOT NULL,
    trigger_by bigint,
    trigger_type varchar(16) NOT NULL DEFAULT 'manual',
    status varchar(16) NOT NULL DEFAULT 'running',
    started_at timestamptz,
    finished_at timestamptz,
    duration_ms bigint,
    log_url varchar(512),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_pipeline_run IS '流水线运行';
COMMENT ON COLUMN devops_pipeline_run.status IS '状态：running/success/failed/aborted';

CREATE TABLE devops_build (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    run_id bigint NOT NULL,
    build_no varchar(64) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'running',
    commit_sha varchar(64),
    artifact_name varchar(256),
    artifact_version varchar(64),
    artifact_url varchar(512),
    quality_gate varchar(16),
    started_at timestamptz,
    finished_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_build IS '构建记录';
COMMENT ON COLUMN devops_build.quality_gate IS 'Sonar 质量门禁：pass/fail';
