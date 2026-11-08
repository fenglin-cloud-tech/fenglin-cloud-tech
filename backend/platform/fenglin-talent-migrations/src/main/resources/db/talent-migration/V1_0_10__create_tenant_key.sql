-- 支撑表：租户数据密钥（每租户 DEK 密文与哈希盐）
-- 文档基线：03_丰麟云才_03 第 4.6.1、7.2 节
-- 说明：tenant_salt 为参与哈希的租户盐（密文存储），DEK 由 KMS CMK 加密后存 dek_cipher。
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_tenant_key (
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

COMMENT ON TABLE talent_tenant_key IS '租户数据密钥（DEK 密文 + 租户盐，销毁 DEK 即不可解密）';
COMMENT ON COLUMN talent_tenant_key.key_status IS '状态：ACTIVE/ROTATED/DISABLED';
