-- FIN-02 方案 C 第一期：统一往来单位主数据（三表，无外键）
-- 幂等：IF NOT EXISTS / DROP POLICY IF EXISTS；Flyway 版本已存在则整体跳过。
-- 回滚：DROP TABLE mdm_partner_alias; DROP TABLE mdm_partner_merge; DROP TABLE mdm_business_partner;
SET search_path TO fenglin_mdm, public;

CREATE TABLE IF NOT EXISTS mdm_business_partner (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    partner_code varchar(32) NOT NULL,
    name varchar(256) NOT NULL,
    short_name varchar(128),
    unified_credit_code varchar(32),
    legal_rep varchar(64),
    contact_name varchar(64),
    contact_phone varchar(32),
    contact_email varchar(128),
    partner_type varchar(16) NOT NULL DEFAULT 'BOTH',
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    source_domain varchar(16) NOT NULL,
    source_ref varchar(128) NOT NULL,
    normalized_name varchar(256) NOT NULL,
    dedup_key varchar(64) NOT NULL,
    merged_into bigint,
    remark varchar(512),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE mdm_business_partner IS '统一往来单位主数据（客户/供应商/工单客户/合同相对方共用）';
COMMENT ON COLUMN mdm_business_partner.partner_type IS 'CUSTOMER/SUPPLIER/BOTH';
COMMENT ON COLUMN mdm_business_partner.status IS 'DRAFT/ENABLED/DISABLED/MERGED';
COMMENT ON COLUMN mdm_business_partner.source_domain IS '来源域：CRM/FINANCE/SERVICE/LEGAL';
COMMENT ON COLUMN mdm_business_partner.source_ref IS '来源域业务引用（业务编码，不是内部 ID 的对外暴露途径）';
COMMENT ON COLUMN mdm_business_partner.dedup_key IS 'sha256(tenant_id|normalized_name)，用于同租户归一化同名判重';

CREATE UNIQUE INDEX IF NOT EXISTS uk_mdm_partner_code ON mdm_business_partner (tenant_id, partner_code);
CREATE UNIQUE INDEX IF NOT EXISTS uk_mdm_partner_dedup ON mdm_business_partner (tenant_id, dedup_key);
CREATE INDEX IF NOT EXISTS idx_mdm_partner_status ON mdm_business_partner (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_mdm_partner_credit ON mdm_business_partner (tenant_id, unified_credit_code);
CREATE INDEX IF NOT EXISTS idx_mdm_partner_merged ON mdm_business_partner (tenant_id, merged_into);

CREATE TABLE IF NOT EXISTS mdm_partner_merge (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    source_partner_id bigint NOT NULL,
    target_partner_id bigint NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    reason varchar(512),
    merged_by bigint NOT NULL,
    merged_at timestamptz NOT NULL DEFAULT now(),
    revoked_by bigint,
    revoked_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE mdm_partner_merge IS '合并映射：只置标记，不改业务外键；撤销仅置 REVOKED';
COMMENT ON COLUMN mdm_partner_merge.status IS 'ACTIVE/REVOKED';

CREATE UNIQUE INDEX IF NOT EXISTS uk_mdm_partner_merge_active
    ON mdm_partner_merge (tenant_id, source_partner_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_mdm_partner_merge_target ON mdm_partner_merge (tenant_id, target_partner_id);

CREATE TABLE IF NOT EXISTS mdm_partner_alias (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    partner_id bigint NOT NULL,
    alias_type varchar(16) NOT NULL,
    alias_value varchar(256) NOT NULL,
    normalized_value varchar(256),
    source_merge_id bigint,
    active smallint NOT NULL DEFAULT 1,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE mdm_partner_alias IS '别名/曾用名：合并后原名称保留并可检索；撤销合并仅置 active=0';
COMMENT ON COLUMN mdm_partner_alias.alias_type IS 'NAME/CODE/CREDIT_CODE';

CREATE INDEX IF NOT EXISTS idx_mdm_partner_alias_norm ON mdm_partner_alias (tenant_id, normalized_value);
CREATE INDEX IF NOT EXISTS idx_mdm_partner_alias_partner ON mdm_partner_alias (tenant_id, partner_id);

ALTER TABLE mdm_business_partner ENABLE ROW LEVEL SECURITY;
ALTER TABLE mdm_business_partner FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON mdm_business_partner;
CREATE POLICY tenant_isolation ON mdm_business_partner
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE mdm_partner_merge ENABLE ROW LEVEL SECURITY;
ALTER TABLE mdm_partner_merge FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON mdm_partner_merge;
CREATE POLICY tenant_isolation ON mdm_partner_merge
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE mdm_partner_alias ENABLE ROW LEVEL SECURITY;
ALTER TABLE mdm_partner_alias FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON mdm_partner_alias;
CREATE POLICY tenant_isolation ON mdm_partner_alias
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON mdm_business_partner TO fl_mdm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON mdm_partner_merge TO fl_mdm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON mdm_partner_alias TO fl_mdm_app;
