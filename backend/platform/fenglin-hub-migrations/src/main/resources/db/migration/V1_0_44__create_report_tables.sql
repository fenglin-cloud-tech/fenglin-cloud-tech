-- P3 报表与指标（HUB-API-1101~1105）
-- 依据 00 数据库设计 4.43 hub_report_def、4.44 hub_metric；并按 C-10 补齐数据集两表。
-- 文档缺口兜底：
--   1) hub_metric 在“设计原则 2”被列为无租户全局表，但表清单/4.44 标注 RLS 且 UNIQUE(tenant)，
--      本实现按表清单执行（tenant_id + RLS），避免跨租户指标口径互相污染；
--   2) hub_report_dataset / hub_report_dataset_field / hub_report_def.config 的字段结构文档未定义，
--      按“数据集 SQL 模板 + 字段白名单”实现，详见 report-service 说明；
--   3) 1102/1104 未声明权限码，分别复用 tenant:report:metric:manage 与 biz:report:view。
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_metric (
    id          bigint       NOT NULL,
    tenant_id   bigint       NOT NULL,
    code        varchar(64)  NOT NULL,
    name        varchar(64)  NOT NULL,
    metric_type varchar(16)  NOT NULL DEFAULT 'ATOMIC',
    formula     text,
    unit        varchar(16),
    grain       varchar(16)  NOT NULL DEFAULT 'DAY',
    dimensions  varchar(512),
    lineage     jsonb,
    owner_id    bigint,
    status      varchar(16)  NOT NULL DEFAULT 'PUBLISHED',
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_metric PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_metric_tenant_code ON hub_metric (tenant_id, code) WHERE deleted = 0;
CREATE INDEX idx_hub_metric_type ON hub_metric (tenant_id, metric_type, status) WHERE deleted = 0;
CREATE TRIGGER trg_hub_metric_updated_at BEFORE UPDATE ON hub_metric
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_report_dataset (
    id           bigint       NOT NULL,
    tenant_id    bigint       NOT NULL,
    code         varchar(40)  NOT NULL,
    name         varchar(128) NOT NULL,
    description  varchar(512),
    sql_template text         NOT NULL,
    status       varchar(16)  NOT NULL DEFAULT 'ENABLED',
    created_by   bigint,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted      smallint     NOT NULL DEFAULT 0,
    version      integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_report_dataset PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_report_dataset_tenant_code ON hub_report_dataset (tenant_id, code) WHERE deleted = 0;
CREATE TRIGGER trg_hub_report_dataset_updated_at BEFORE UPDATE ON hub_report_dataset
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_report_dataset_field (
    id          bigint      NOT NULL,
    tenant_id   bigint      NOT NULL,
    dataset_id  bigint      NOT NULL,
    field_name  varchar(64) NOT NULL,
    column_name varchar(64) NOT NULL,
    field_role  varchar(16) NOT NULL DEFAULT 'DIMENSION',
    aggregate   varchar(16),
    data_type   varchar(16),
    sort        integer     NOT NULL DEFAULT 0,
    created_by  bigint,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted     smallint    NOT NULL DEFAULT 0,
    version     integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_report_dataset_field PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_report_dataset_field ON hub_report_dataset_field (dataset_id, field_name)
    WHERE deleted = 0;
CREATE INDEX idx_hub_report_dataset_field_ds ON hub_report_dataset_field (tenant_id, dataset_id);
CREATE TRIGGER trg_hub_report_dataset_field_updated_at BEFORE UPDATE ON hub_report_dataset_field
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_report_def (
    id           bigint       NOT NULL,
    tenant_id    bigint       NOT NULL,
    code         varchar(40)  NOT NULL,
    name         varchar(128) NOT NULL,
    report_type  varchar(16)  NOT NULL DEFAULT 'REPORT',
    dataset_id   bigint,
    config       jsonb,
    scope        varchar(16)  NOT NULL DEFAULT 'TENANT',
    product_code varchar(64),
    status       varchar(16)  NOT NULL DEFAULT 'DRAFT',
    created_by   bigint,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted      smallint     NOT NULL DEFAULT 0,
    version      integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_report_def PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_report_def_tenant_code ON hub_report_def (tenant_id, code) WHERE deleted = 0;
CREATE INDEX idx_hub_report_def_type ON hub_report_def (tenant_id, report_type, status) WHERE deleted = 0;
CREATE TRIGGER trg_hub_report_def_updated_at BEFORE UPDATE ON hub_report_def
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_metric ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_report_dataset ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_report_dataset_field ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_report_def ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_hub_metric_tenant ON hub_metric
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_metric_platform ON hub_metric
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_report_dataset_tenant ON hub_report_dataset
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_report_dataset_platform ON hub_report_dataset
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_report_dataset_field_tenant ON hub_report_dataset_field
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_report_dataset_field_platform ON hub_report_dataset_field
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_report_def_tenant ON hub_report_def
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_report_def_platform ON hub_report_def
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_metric TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_report_dataset TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_report_dataset_field TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_report_def TO fl_app, fl_platform_role;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8161, 'tenant:report:metric:manage', '指标口径管理', 'API', 'report', 61, 0),
       (8162, 'biz:report:view', '报表查看', 'API', 'report', 62, 0),
       (8163, 'biz:report:export', '报表导出', 'API', 'report', 63, 0)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_metric IS '指标口径（HUB-API-1101/1102）';
COMMENT ON TABLE hub_report_dataset IS '报表数据集：sql_template 为管理员预置只读查询（C-10 补齐）';
COMMENT ON TABLE hub_report_dataset_field IS '数据集字段白名单：维度/度量与聚合方式（C-10 补齐）';
COMMENT ON TABLE hub_report_def IS '报表/看板定义（HUB-API-1104/1105）';
