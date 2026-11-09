-- 云财收口：业财一体规则配置（文档 45 表清单未列，按功能文档 F-D1-15 兜底补充）
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_business_rule (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    rule_code varchar(32) NOT NULL,
    rule_name varchar(128) NOT NULL,
    rule_type varchar(32) NOT NULL,
    config_json jsonb NOT NULL,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_business_rule IS '业财一体规则配置';

CREATE INDEX idx_fin_business_rule_tenant ON fin_business_rule (tenant_id);
CREATE UNIQUE INDEX uk_fin_business_rule_code ON fin_business_rule (tenant_id, rule_code) WHERE deleted = 0;

CREATE TRIGGER trg_fin_business_rule_updated BEFORE UPDATE ON fin_business_rule
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE fin_business_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE fin_business_rule FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON fin_business_rule
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON fin_business_rule TO fl_finance_app;
