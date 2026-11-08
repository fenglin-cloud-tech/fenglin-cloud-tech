-- P3 AI 中台（HUB-API-1301~1308）
-- 复用已存在的 hub_ai_model / hub_ai_prompt（V1_0_23/24），按总纲 1301/1302 契约补充列；
-- 新增 hub_ai_capability_grant、hub_ai_metering（按月分区）。
-- 文档缺口兜底：
--   1) 1301 请求体字段（providerType/modelType/endpoint/routingWeight/指纹）在既有表中无对应列，本迁移补齐并保持旧列兼容；
--   2) 1302 的 name/scene/systemPrompt/userPrompt 在既有 content 单列中无对应列，本迁移补齐，content 仍写 system+user 合成文本；
--   3) 1304 chunkId 来源文档未定义，按检索服务 doc_id + 序号派生；
--   4) 1307/1308 文档未给权限码，按 ADMIN + 租户 RLS 收敛。
SET search_path TO fenglin_hub, public;

ALTER TABLE hub_ai_model
    ADD COLUMN provider_type       varchar(32),
    ADD COLUMN model_type          varchar(32),
    ADD COLUMN routing_weight      integer NOT NULL DEFAULT 100,
    ADD COLUMN api_key_fingerprint varchar(64);

COMMENT ON COLUMN hub_ai_model.api_key_fingerprint IS 'API Key 指纹（SHA-256 前 16 位），1301 响应仅回显指纹';

ALTER TABLE hub_ai_prompt
    ADD COLUMN name         varchar(128),
    ADD COLUMN scene        varchar(32),
    ADD COLUMN system_prompt text,
    ADD COLUMN user_prompt   text;

CREATE TABLE hub_ai_capability_grant (
    id                bigint       NOT NULL,
    tenant_id         bigint       NOT NULL,
    host_product      varchar(64)  NOT NULL,
    capability        varchar(32)  NOT NULL,
    enabled           smallint     NOT NULL DEFAULT 1,
    quota_qps         integer      NOT NULL DEFAULT 50,
    quota_token_month bigint       NOT NULL DEFAULT 0,
    billing_mode      varchar(16)  NOT NULL DEFAULT 'TOKEN',
    created_by        bigint,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        bigint,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted           smallint     NOT NULL DEFAULT 0,
    version           integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_ai_capability_grant PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_hub_ai_capability_grant
    ON hub_ai_capability_grant (tenant_id, host_product, capability) WHERE deleted = 0;
CREATE TRIGGER trg_hub_ai_capability_grant_updated_at BEFORE UPDATE ON hub_ai_capability_grant
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_ai_metering (
    id           bigint        NOT NULL,
    tenant_id    bigint        NOT NULL,
    host_product varchar(64)   NOT NULL,
    capability   varchar(32)   NOT NULL,
    mode         varchar(16)   NOT NULL DEFAULT 'STANDALONE',
    stat_date    date          NOT NULL,
    call_count   bigint        NOT NULL DEFAULT 0,
    token_in     bigint        NOT NULL DEFAULT 0,
    token_out    bigint        NOT NULL DEFAULT 0,
    cost         numeric(16,4) NOT NULL DEFAULT 0,
    created_by   bigint,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    deleted      smallint      NOT NULL DEFAULT 0,
    version      integer       NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_ai_metering PRIMARY KEY (id, stat_date)
) PARTITION BY RANGE (stat_date);

CREATE TABLE hub_ai_metering_default PARTITION OF hub_ai_metering DEFAULT;

CREATE UNIQUE INDEX uk_hub_ai_metering_daily
    ON hub_ai_metering (tenant_id, host_product, capability, mode, stat_date) WHERE deleted = 0;

ALTER TABLE hub_ai_capability_grant ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_ai_metering ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_hub_ai_capability_grant_tenant ON hub_ai_capability_grant
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_ai_capability_grant_platform ON hub_ai_capability_grant
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);
CREATE POLICY p_hub_ai_metering_tenant ON hub_ai_metering
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_ai_metering_platform ON hub_ai_metering
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_ai_capability_grant TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_ai_metering TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_ai_metering_default TO fl_app, fl_platform_role;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8181, 'platform:ai:model:manage', 'AI 模型配置', 'API', 'ai', 81, 0),
       (8182, 'tenant:ai:prompt:manage', 'AI 提示词管理', 'API', 'ai', 82, 0),
       (8183, 'ai:chat:rag', 'RAG 问答', 'API', 'ai', 83, 0),
       (8184, 'tenant:ai:grant:manage', 'AI 能力授权配置', 'API', 'ai', 84, 0)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_ai_capability_grant IS 'AI 能力授权（HUB-API-1306）';
COMMENT ON TABLE hub_ai_metering IS 'AI 用量计量，按月分区（HUB-API-1307）';
