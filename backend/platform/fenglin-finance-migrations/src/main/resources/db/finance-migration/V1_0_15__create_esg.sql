-- D4：双碳目标、ESG 指标、ESG 报告
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_carbon_target (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    target_no varchar(32) NOT NULL,
    target_name varchar(128) NOT NULL,
    target_year int NOT NULL,
    base_year int NOT NULL,
    base_emission numeric(20,4) NOT NULL,
    target_emission numeric(20,4) NOT NULL,
    reduction_rate numeric(9,6) NOT NULL,
    current_emission numeric(20,4) NOT NULL DEFAULT 0,
    progress numeric(9,6) NOT NULL DEFAULT 0,
    owner_id bigint,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_carbon_target IS '双碳目标与进度';
COMMENT ON COLUMN fin_carbon_target.status IS '状态：ACTIVE/ACHIEVED/EXPIRED';

CREATE TABLE fin_esg_indicator (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    indicator_code varchar(32) NOT NULL,
    indicator_name varchar(128) NOT NULL,
    category varchar(8) NOT NULL,
    unit varchar(16),
    period_year int NOT NULL,
    target_value numeric(20,4),
    actual_value numeric(20,4),
    data_source varchar(128),
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_esg_indicator IS 'ESG 指标（环境/社会/治理）';
COMMENT ON COLUMN fin_esg_indicator.category IS '类别：E/S/G';
COMMENT ON COLUMN fin_esg_indicator.status IS '状态：DRAFT/COLLECTED/VERIFIED';

CREATE TABLE fin_esg_report (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    report_no varchar(32) NOT NULL,
    report_name varchar(128) NOT NULL,
    period_year int NOT NULL,
    report_type varchar(16) NOT NULL,
    report_boundary varchar(255),
    summary jsonb,
    file_id bigint,
    status varchar(16) NOT NULL,
    published_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_esg_report IS 'ESG 报告';
COMMENT ON COLUMN fin_esg_report.report_type IS '类型：ANNUAL/SPECIAL';
COMMENT ON COLUMN fin_esg_report.status IS '状态：DRAFT/REVIEWING/PUBLISHED';
