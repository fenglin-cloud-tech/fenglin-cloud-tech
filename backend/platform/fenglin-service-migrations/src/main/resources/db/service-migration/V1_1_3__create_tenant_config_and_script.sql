-- 兜底补充（文档 37 表未覆盖管理端配置与低代码脚本，接口 SVC-API-043/044/046-049/077/170/171/172 需要持久化）：
--   svc_tenant_config：处理组/值班表/分派规则/资产提醒/重试策略等键值配置
--   svc_script_definition：低代码脚本定义与静态扫描结果
-- 标注：属于总纲兜底扩展表，不改变文档既有 37 张业务表定义。
SET search_path TO fenglin_service, public;

CREATE TABLE svc_tenant_config (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    config_type varchar(32) NOT NULL,
    config_key varchar(64) NOT NULL,
    config_json jsonb NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_tenant_config IS '服务域租户配置（处理组/值班/分派规则/提醒/重试策略，总纲兜底表）';
COMMENT ON COLUMN svc_tenant_config.config_type IS '类型：GROUP/DUTY/DISPATCH_RULE/ASSET_REMINDER/RETRY_POLICY/TICKET_TYPE/TENANT';

CREATE UNIQUE INDEX uk_tenant_config ON svc_tenant_config (tenant_id, config_type, config_key) WHERE deleted = 0;

CREATE TABLE svc_script_definition (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    app_id bigint NOT NULL,
    script_key varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    language varchar(16) NOT NULL DEFAULT 'JS',
    content text NOT NULL,
    version_no int NOT NULL DEFAULT 1,
    risk_level varchar(8),
    scan_result jsonb,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_script_definition IS '低代码脚本定义与扫描结果（总纲兜底表）';
COMMENT ON COLUMN svc_script_definition.status IS '状态：DRAFT/PUBLISHED/DISABLED';

CREATE UNIQUE INDEX uk_script_key ON svc_script_definition (tenant_id, app_id, script_key, version_no) WHERE deleted = 0;

ALTER TABLE svc_tenant_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE svc_tenant_config FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON svc_tenant_config
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON svc_tenant_config TO fl_service_app;

ALTER TABLE svc_script_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE svc_script_definition FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON svc_script_definition
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON svc_script_definition TO fl_service_app;

CREATE INDEX idx_tenant_config_type ON svc_tenant_config (tenant_id, config_type);
CREATE INDEX idx_script_app ON svc_script_definition (tenant_id, app_id, status);

CREATE TRIGGER trg_svc_tenant_config_updated BEFORE UPDATE ON svc_tenant_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_svc_script_definition_updated BEFORE UPDATE ON svc_script_definition
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
