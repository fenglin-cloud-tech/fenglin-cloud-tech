-- 云研管理端收口：度量口径版本历史与租户集成配置
-- 文档基线：05_丰麟云研_05_接口文档 API-72（发布口径版本）、API-59 预留（集成管理配置）。
-- 偏差与兜底标注：
--   1) 文档未给出 devops_metric_caliber / devops_tenant_config 的 DDL，按总纲以本脚本补齐；
--      口径历史独立成表，devops_metric_config 仅保存当前生效口径与版本号。
--   2) 接口路径文档为 /api/devops/v1，实现沿用既有 /api/rnd/v1，脚本只落存储不做路径决策。
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_metric_caliber (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    metric_code varchar(64) NOT NULL,
    caliber_version varchar(16) NOT NULL,
    formula text NOT NULL,
    effective_from timestamptz NOT NULL DEFAULT now(),
    status varchar(16) NOT NULL DEFAULT 'published',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_metric_caliber IS '度量口径版本历史（历史快照保留原版本标记）';
COMMENT ON COLUMN devops_metric_caliber.status IS '状态：published/retired';

CREATE TABLE devops_tenant_config (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    timezone varchar(64) NOT NULL DEFAULT 'Asia/Shanghai',
    data_retention_months int NOT NULL DEFAULT 24,
    dora_target_level varchar(16) NOT NULL DEFAULT 'high',
    webhook_base_url varchar(512),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_tenant_config_retention CHECK (data_retention_months > 0)
);
COMMENT ON TABLE devops_tenant_config IS '云研租户集成与度量配置（管理端集成配置 A-05 兜底存储）';
COMMENT ON COLUMN devops_tenant_config.config_json IS '集成扩展配置（GitLab/Jenkins/SonarQube 等非敏感项）';
COMMENT ON COLUMN devops_tenant_config.dora_target_level IS 'DORA 目标等级：elite/high/medium/low';

-- 新表不在 V1_1_0 / V1_1_1 的 DO 循环覆盖范围内，需单独建索引、RLS 与触发器
CREATE INDEX idx_devops_metric_caliber_tenant ON devops_metric_caliber (tenant_id);
CREATE INDEX idx_devops_tenant_config_tenant ON devops_tenant_config (tenant_id);

CREATE UNIQUE INDEX uk_metric_caliber ON devops_metric_caliber
    (tenant_id, metric_code, caliber_version) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_tenant_config ON devops_tenant_config (tenant_id) WHERE deleted = 0;

CREATE TRIGGER trg_devops_metric_caliber_updated BEFORE UPDATE ON devops_metric_caliber
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_devops_tenant_config_updated BEFORE UPDATE ON devops_tenant_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE devops_metric_caliber ENABLE ROW LEVEL SECURITY;
ALTER TABLE devops_metric_caliber FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON devops_metric_caliber
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON devops_metric_caliber TO fl_devops_app;

ALTER TABLE devops_tenant_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE devops_tenant_config FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON devops_tenant_config
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON devops_tenant_config TO fl_devops_app;
