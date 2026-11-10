-- PLAT-01：IAM 租户数据密钥与手机号租户内唯一
-- hub_tenant_key 结构对齐 fenglin_talent 的 talent_tenant_key，DEK 密文 + 租户哈希盐
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_tenant_key (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    key_version int NOT NULL,
    dek_cipher text NOT NULL,
    tenant_salt varchar(128) NOT NULL,
    key_status varchar(16) NOT NULL,
    rotate_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE hub_tenant_key IS '租户数据密钥（DEK 密文 + 租户盐，销毁 DEK 即不可解密）';
COMMENT ON COLUMN hub_tenant_key.key_status IS '状态：ACTIVE/ROTATED/DISABLED';

CREATE INDEX idx_hub_tenant_key_tenant ON hub_tenant_key (tenant_id);

ALTER TABLE hub_tenant_key ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_tenant_key_tenant ON hub_tenant_key
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_tenant_key_platform ON hub_tenant_key
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_tenant_key TO fl_app, fl_platform_role;

-- 手机号租户内唯一（保留原 idx_hub_user_mobile_hash 非唯一索引）
CREATE UNIQUE INDEX uq_hub_user_mobile_hash
    ON hub_user (tenant_id, mobile_hash)
    WHERE deleted = 0 AND mobile_hash IS NOT NULL;
