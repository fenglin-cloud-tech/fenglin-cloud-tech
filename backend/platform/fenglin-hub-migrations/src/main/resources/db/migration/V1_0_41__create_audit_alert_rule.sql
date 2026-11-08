-- P2 审计告警配置（HUB-API-804）
-- 文档缺口兜底：数据库设计未定义告警规则表，按接口契约的 items 结构新增 hub_audit_alert_rule（租户级全量替换）。
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_audit_alert_rule (
    id            bigint       NOT NULL,
    tenant_id     bigint       NOT NULL,
    code          varchar(64)  NOT NULL,
    event_type    varchar(64)  NOT NULL,
    threshold     integer      NOT NULL DEFAULT 1,
    receiver_role varchar(64)  NOT NULL,
    channels      varchar(128) NOT NULL,
    enabled       smallint     NOT NULL DEFAULT 1,
    created_by    bigint,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted       smallint     NOT NULL DEFAULT 0,
    version       integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_audit_alert_rule PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_hub_audit_alert_rule_code
    ON hub_audit_alert_rule (tenant_id, code) WHERE deleted = 0;
CREATE INDEX idx_audit_alert_rule_tenant ON hub_audit_alert_rule (tenant_id, enabled);
CREATE TRIGGER trg_hub_audit_alert_rule_updated_at BEFORE UPDATE ON hub_audit_alert_rule
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_audit_alert_rule ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_audit_alert_rule_tenant ON hub_audit_alert_rule
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_audit_alert_rule_platform ON hub_audit_alert_rule
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_audit_alert_rule TO fl_app, fl_platform_role;

-- 契约未指定 804 的权限码，兜底为平台审计告警管理权限
INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8132, 'platform:audit:alert:manage', '审计告警规则管理', 'API', 'audit', 32, 0)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_audit_alert_rule IS '审计告警规则（HUB-API-804 文档缺口兜底）';
