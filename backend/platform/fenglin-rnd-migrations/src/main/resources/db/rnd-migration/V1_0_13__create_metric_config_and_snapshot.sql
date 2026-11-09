-- E2：度量配置与效能指标快照
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_metric_config (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    metric_code varchar(64) NOT NULL,
    metric_name varchar(128) NOT NULL,
    metric_group varchar(32) NOT NULL,
    formula text NOT NULL,
    caliber_version varchar(16) NOT NULL DEFAULT 'v1',
    weight numeric(6,2),
    target_value numeric(18,6),
    unit varchar(16),
    extra_filter jsonb,
    status varchar(16) NOT NULL DEFAULT 'enabled',
    effective_from timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_metric_config IS '度量配置（tenant_id=0 为平台内置口径，租户可覆盖）';
COMMENT ON COLUMN devops_metric_config.metric_group IS '分组：contribution/delivery/quality/dora';
COMMENT ON COLUMN devops_metric_config.status IS '状态：enabled/disabled';

CREATE TABLE devops_metric_snapshot (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    metric_code varchar(64) NOT NULL,
    scope_type varchar(16) NOT NULL,
    scope_id bigint NOT NULL,
    period_type varchar(16) NOT NULL,
    period_key varchar(32) NOT NULL,
    metric_value numeric(18,6) NOT NULL,
    numerator numeric(18,6),
    denominator numeric(18,6),
    sample_count int,
    data_quality varchar(16) NOT NULL DEFAULT 'normal',
    caliber_version varchar(16) NOT NULL DEFAULT 'v1',
    computed_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_snapshot_denominator CHECK (denominator IS NULL OR denominator >= 0)
);
COMMENT ON TABLE devops_metric_snapshot IS '效能指标快照';
COMMENT ON COLUMN devops_metric_snapshot.scope_type IS '范围类型：tenant/product/team/user';
COMMENT ON COLUMN devops_metric_snapshot.period_type IS '周期：day/week/sprint/month';
COMMENT ON COLUMN devops_metric_snapshot.data_quality IS '数据质量：normal/missing/outlier';
