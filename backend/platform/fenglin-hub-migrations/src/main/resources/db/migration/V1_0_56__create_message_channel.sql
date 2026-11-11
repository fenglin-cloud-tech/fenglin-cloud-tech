-- PLAT-02：消息通道管理（站内/短信/邮件/企微/钉钉/Webhook）
-- 权限点 tenant:message:channel:manage（8392）此前已存在但无任何接口与存储，本迁移补表并补齐各租户授权。
SET search_path TO fenglin_hub, public;

CREATE TABLE IF NOT EXISTS hub_message_channel (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    channel_type varchar(16) NOT NULL,
    name varchar(100) NOT NULL,
    config jsonb NOT NULL DEFAULT '{}'::jsonb,
    enabled smallint NOT NULL DEFAULT 1,
    is_default smallint NOT NULL DEFAULT 0,
    remark varchar(200),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE hub_message_channel IS '消息通道配置（按租户）';
COMMENT ON COLUMN hub_message_channel.channel_type IS '通道类型：INAPP/SMS/EMAIL/WECOM/DINGTALK/WEBHOOK';
COMMENT ON COLUMN hub_message_channel.config IS '通道配置 JSON，如 webhook 地址、短信签名、SMTP 主机等';
COMMENT ON COLUMN hub_message_channel.is_default IS '同类型通道默认项：1 默认 0 非默认';

CREATE UNIQUE INDEX IF NOT EXISTS uk_hub_message_channel_name
    ON hub_message_channel (tenant_id, channel_type, name) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_hub_message_channel_type
    ON hub_message_channel (tenant_id, channel_type, enabled);

CREATE TRIGGER trg_hub_message_channel_updated
    BEFORE UPDATE ON hub_message_channel
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_message_channel ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_message_channel FORCE ROW LEVEL SECURITY;
CREATE POLICY p_hub_message_channel_tenant ON hub_message_channel
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message_channel TO fl_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message_channel TO fl_platform_role;

-- 授权各租户 TENANT_ADMIN
INSERT INTO hub_role_permission (id, tenant_id, role_id, permission_code, created_at, deleted, version)
SELECT (extract(epoch FROM clock_timestamp()) * 1000)::bigint * 1000 + row_number() OVER (),
       r.tenant_id,
       r.id,
       'tenant:message:channel:manage',
       now(),
       0,
       0
FROM hub_role r
WHERE r.code = 'TENANT_ADMIN' AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;
