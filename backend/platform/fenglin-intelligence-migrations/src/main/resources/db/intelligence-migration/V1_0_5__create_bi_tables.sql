-- H3 BI/洞察域：指标、数据集、字段、看板、图表、洞察任务/结果、归因、订阅
SET search_path TO fenglin_intelligence, public;

CREATE TABLE ai_metric_definition (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    code varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    category varchar(32),
    metric_type varchar(16) NOT NULL DEFAULT 'ATOMIC',
    formula text NOT NULL,
    unit varchar(16),
    grain varchar(16) NOT NULL DEFAULT 'DAY',
    dimensions jsonb,
    owner_id bigint,
    lineage jsonb,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    version_no int NOT NULL DEFAULT 1,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_metric_definition IS '指标定义（tenant_id=0 为平台内置口径，可读不可改）';
COMMENT ON COLUMN ai_metric_definition.metric_type IS 'ATOMIC/DERIVED/COMPOSITE';
COMMENT ON COLUMN ai_metric_definition.grain IS 'DAY/WEEK/MONTH/QUARTER/YEAR';
COMMENT ON COLUMN ai_metric_definition.status IS 'DRAFT/PUBLISHED/DEPRECATED';

CREATE TABLE ai_dataset (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    code varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    data_source_id bigint NOT NULL,
    mode varchar(16) NOT NULL DEFAULT 'DIRECT',
    sql_text text,
    accel_engine varchar(24),
    row_filter text,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    refresh_cron varchar(32),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_dataset IS 'BI 数据集';
COMMENT ON COLUMN ai_dataset.mode IS 'DIRECT/ACCEL/IMPORT';
COMMENT ON COLUMN ai_dataset.accel_engine IS 'DORIS/CLICKHOUSE';
COMMENT ON COLUMN ai_dataset.status IS 'DRAFT/PUBLISHED/DISABLED';

CREATE TABLE ai_dataset_field (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    dataset_id bigint NOT NULL,
    field_name varchar(64) NOT NULL,
    field_type varchar(24) NOT NULL DEFAULT 'DIMENSION',
    data_type varchar(24) NOT NULL DEFAULT 'STRING',
    expression text,
    metric_id bigint,
    dimension_table varchar(64),
    is_sensitive boolean NOT NULL DEFAULT false,
    sort_no int NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_dataset_field IS '数据集字段';
COMMENT ON COLUMN ai_dataset_field.field_type IS 'DIMENSION/MEASURE';
COMMENT ON COLUMN ai_dataset_field.data_type IS 'STRING/NUMBER/DATE/DATETIME/BOOLEAN';

CREATE TABLE ai_dashboard (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    code varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    layout_json jsonb NOT NULL,
    theme varchar(24),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    share_type varchar(16) NOT NULL DEFAULT 'PRIVATE',
    mobile_enabled boolean NOT NULL DEFAULT false,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_dashboard IS 'BI 看板';
COMMENT ON COLUMN ai_dashboard.status IS 'DRAFT/PUBLISHED/ARCHIVED';
COMMENT ON COLUMN ai_dashboard.share_type IS 'PRIVATE/DEPT/TENANT/PUBLIC_LINK';

CREATE TABLE ai_chart (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    dashboard_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    chart_type varchar(24) NOT NULL DEFAULT 'TABLE',
    dataset_id bigint NOT NULL,
    query_json jsonb NOT NULL,
    style_json jsonb,
    drill_json jsonb,
    alert_json jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_chart IS 'BI 图表';
COMMENT ON COLUMN ai_chart.chart_type IS 'LINE/BAR/COLUMN/PIE/TABLE/KPI/DUAL_AXIS/MAP/FUNNEL';

CREATE TABLE ai_insight_task (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    name varchar(128) NOT NULL,
    target_type varchar(16) NOT NULL DEFAULT 'METRIC',
    target_id bigint NOT NULL,
    trigger_type varchar(16) NOT NULL DEFAULT 'MANUAL',
    cron_expr varchar(32),
    rule_json jsonb,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    last_run_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_insight_task IS '洞察任务（异常检测/归因）';
COMMENT ON COLUMN ai_insight_task.target_type IS 'METRIC/DATASET/DASHBOARD';
COMMENT ON COLUMN ai_insight_task.trigger_type IS 'MANUAL/CRON/EVENT';
COMMENT ON COLUMN ai_insight_task.status IS 'PENDING/RUNNING/SUCCESS/FAILED/DISABLED';

CREATE TABLE ai_insight_result (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    task_id bigint NOT NULL,
    metric_id bigint,
    anomaly_type varchar(24) NOT NULL,
    magnitude numeric(12,4),
    conclusion text NOT NULL,
    suggestion text,
    confidence numeric(5,4) NOT NULL DEFAULT 1.0,
    evidence_json jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_insight_conf CHECK (confidence >= 0 AND confidence <= 1)
);
COMMENT ON TABLE ai_insight_result IS '洞察结果（不可删，留痕）';
COMMENT ON COLUMN ai_insight_result.anomaly_type IS 'SPIKE/DROP/TREND/OUTLIER/CORRELATION';

CREATE TABLE ai_attribution_analysis (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    insight_result_id bigint,
    metric_id bigint NOT NULL,
    dimension_field varchar(64) NOT NULL,
    algorithm varchar(24) NOT NULL DEFAULT 'CONTRIBUTION',
    contributions jsonb NOT NULL,
    main_factor varchar(256),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_attribution_analysis IS '归因分析（不可删，留痕）';
COMMENT ON COLUMN ai_attribution_analysis.algorithm IS 'CONTRIBUTION/SHAP/ADTRIBUTOR';

CREATE TABLE ai_insight_subscription (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    task_id bigint NOT NULL,
    channels varchar(64) NOT NULL DEFAULT 'STATION',
    receivers varchar(512),
    frequency varchar(16) NOT NULL DEFAULT 'DAILY',
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_insight_subscription IS '洞察订阅（文档未建模，按接口 AI-API-244 兜底新增）';
COMMENT ON COLUMN ai_insight_subscription.channels IS 'STATION/EMAIL/IM/WEBHOOK，逗号分隔';
COMMENT ON COLUMN ai_insight_subscription.frequency IS 'REALTIME/DAILY/WEEKLY';
