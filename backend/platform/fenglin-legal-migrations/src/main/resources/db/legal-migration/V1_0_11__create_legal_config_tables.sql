-- 云法配置持久化：到期提醒、合同字段、字典、CA 服务商
-- 说明：此前管理端配置接口仅回显 saved=true，本迁移补齐配置表；
--       所有表沿用 legal 域约定（tenant_id + deleted + version + updated_at 触发器 + RLS）。
SET search_path TO fenglin_legal, public;

CREATE TABLE IF NOT EXISTS legal_reminder_config (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    offset_days int NOT NULL DEFAULT 30,
    channels jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_legal_reminder_config_tenant UNIQUE (tenant_id)
);
COMMENT ON TABLE legal_reminder_config IS '合同到期提醒配置（每租户一条）';
COMMENT ON COLUMN legal_reminder_config.channels IS '通知渠道 JSON 数组：INBOX/EMAIL/IM';

CREATE TABLE IF NOT EXISTS legal_contract_field_config (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    fields jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_legal_contract_field_config_tenant UNIQUE (tenant_id)
);
COMMENT ON TABLE legal_contract_field_config IS '合同字段可见/必填配置（每租户一条）';
COMMENT ON COLUMN legal_contract_field_config.fields IS '字段配置 JSON 数组：field_key/visible/required';

CREATE TABLE IF NOT EXISTS legal_dict_config (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    dict_type varchar(64) NOT NULL,
    items jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_legal_dict_config_tenant_type UNIQUE (tenant_id, dict_type)
);
COMMENT ON TABLE legal_dict_config IS '云法字典配置（按租户 + 字典类型）';
COMMENT ON COLUMN legal_dict_config.items IS '字典项 JSON 数组';

CREATE TABLE IF NOT EXISTS legal_ca_provider (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    provider_code varchar(64) NOT NULL,
    name varchar(128),
    endpoint varchar(255),
    secret_ref varchar(255) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_legal_ca_provider_tenant_code UNIQUE (tenant_id, provider_code)
);
COMMENT ON TABLE legal_ca_provider IS 'CA 服务商配置（密钥仅存 KMS 引用）';
COMMENT ON COLUMN legal_ca_provider.secret_ref IS 'KMS 密钥引用，必须 kms:// 前缀';
COMMENT ON COLUMN legal_ca_provider.status IS '状态：ENABLED/DISABLED';

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'legal_reminder_config', 'legal_contract_field_config',
        'legal_dict_config', 'legal_ca_provider'
    ]
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON fenglin_legal.%I '
                       'FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
                       'trg_' || t || '_updated', t);
        EXECUTE format('ALTER TABLE fenglin_legal.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_legal.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON fenglin_legal.%I '
                       'USING (tenant_id = current_tenant_id()) '
                       'WITH CHECK (tenant_id = current_tenant_id())',
                       'p_legal_' || t || '_tenant', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_legal.%I TO fl_legal_app', t);
    END LOOP;
END
$$;
