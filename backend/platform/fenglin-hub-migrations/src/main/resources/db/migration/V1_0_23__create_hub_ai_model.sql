-- P0 文档缺口补表（C-12）：AI 模型目录。tenant_id=0 表示平台目录。待文档同步。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_ai_model (
    id             bigint       PRIMARY KEY,
    tenant_id      bigint       NOT NULL DEFAULT 0,
    code           varchar(64)  NOT NULL,
    provider       varchar(32)  NOT NULL,
    model_name     varchar(128) NOT NULL,
    base_url       varchar(512),
    api_key_enc    text,
    capabilities   varchar(256),
    context_window integer,
    enabled        smallint     NOT NULL DEFAULT 1,
    created_by     bigint,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     bigint,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted        smallint     NOT NULL DEFAULT 0,
    version        integer      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_hub_ai_model
    ON hub_ai_model (tenant_id, code) WHERE deleted = 0;
CREATE TRIGGER trg_hub_ai_model_updated_at BEFORE UPDATE ON hub_ai_model
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_ai_model ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_ai_model_tenant ON hub_ai_model
    USING (tenant_id = 0 OR tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = 0 OR tenant_id = current_tenant_id());
CREATE POLICY p_hub_ai_model_platform ON hub_ai_model
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_ai_model TO fl_app, fl_platform_role;
COMMENT ON TABLE hub_ai_model IS 'AI 模型目录，tenant_id=0 为平台公共模型（C-12 待文档同步）';
