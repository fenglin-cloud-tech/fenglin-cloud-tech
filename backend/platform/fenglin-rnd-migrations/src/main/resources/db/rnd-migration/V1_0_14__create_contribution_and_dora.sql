-- E2：代码贡献度与 DORA 指标
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_contribution (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    user_id bigint NOT NULL,
    product_id bigint NOT NULL,
    period_type varchar(16) NOT NULL,
    period_key varchar(32) NOT NULL,
    commit_count int NOT NULL DEFAULT 0,
    add_lines int NOT NULL DEFAULT 0,
    del_lines int NOT NULL DEFAULT 0,
    files_changed int NOT NULL DEFAULT 0,
    effective_commits int NOT NULL DEFAULT 0,
    complexity_score numeric(12,4),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_contribution IS '代码贡献度（合并提交/二进制/生成代码排除）';

CREATE TABLE devops_dora_metric (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    period_type varchar(16) NOT NULL,
    period_key varchar(32) NOT NULL,
    deploy_freq numeric(12,4),
    lead_time_hours numeric(12,2),
    change_failure_rate numeric(9,6),
    mttr_hours numeric(12,2),
    level varchar(16),
    computed_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_dora_metric IS 'DORA 指标';
COMMENT ON COLUMN devops_dora_metric.level IS '等级：elite/high/medium/low';
