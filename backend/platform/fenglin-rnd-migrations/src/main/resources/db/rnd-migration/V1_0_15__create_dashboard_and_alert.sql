-- E2：洞察大盘与效能预警
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_insight_dashboard (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    scope_type varchar(16) NOT NULL DEFAULT 'tenant',
    scope_id bigint,
    layout_json jsonb NOT NULL,
    metric_codes jsonb NOT NULL,
    shared smallint NOT NULL DEFAULT 0,
    owner_id bigint NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_insight_dashboard IS '洞察大盘';

CREATE TABLE devops_metric_alert (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    metric_code varchar(64) NOT NULL,
    scope_type varchar(16) NOT NULL,
    scope_id bigint NOT NULL,
    threshold numeric(18,6) NOT NULL,
    compare_op varchar(8) NOT NULL,
    triggered_value numeric(18,6),
    status varchar(16) NOT NULL DEFAULT 'active',
    notify_channel varchar(64),
    triggered_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_metric_alert IS '效能预警';
COMMENT ON COLUMN devops_metric_alert.compare_op IS '比较：gt/lt/gte/lte';
COMMENT ON COLUMN devops_metric_alert.status IS '状态：active/triggered/resolved';
