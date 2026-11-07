-- P0 文档缺口补表（C-12）：AI 提示词模板。tenant_id=0 表示平台模板。待文档同步。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_ai_prompt (
    id             bigint      PRIMARY KEY,
    tenant_id      bigint      NOT NULL DEFAULT 0,
    code           varchar(64) NOT NULL,
    prompt_version integer     NOT NULL DEFAULT 1,
    content        text        NOT NULL,
    variables      jsonb,
    status         varchar(32) NOT NULL DEFAULT 'DRAFT',
    created_by     bigint,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_by     bigint,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    deleted        smallint    NOT NULL DEFAULT 0,
    version        integer     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_hub_ai_prompt
    ON hub_ai_prompt (tenant_id, code, prompt_version) WHERE deleted = 0;
CREATE TRIGGER trg_hub_ai_prompt_updated_at BEFORE UPDATE ON hub_ai_prompt
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_ai_prompt ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_ai_prompt_tenant ON hub_ai_prompt
    USING (tenant_id = 0 OR tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = 0 OR tenant_id = current_tenant_id());
CREATE POLICY p_hub_ai_prompt_platform ON hub_ai_prompt
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_ai_prompt TO fl_app, fl_platform_role;
COMMENT ON TABLE hub_ai_prompt IS 'AI 提示词模板（C-12 待文档同步）';
