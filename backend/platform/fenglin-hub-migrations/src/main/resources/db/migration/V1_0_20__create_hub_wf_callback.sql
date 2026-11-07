-- P0 文档缺口补表（C-12）：工作流回调。待文档同步。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_wf_callback (
    id           bigint       PRIMARY KEY,
    tenant_id    bigint       NOT NULL,
    process_key  varchar(64)  NOT NULL,
    callback_url varchar(512) NOT NULL,
    secret_enc   text,
    events       varchar(512),
    timeout_ms   integer      NOT NULL DEFAULT 3000,
    max_retry    integer      NOT NULL DEFAULT 3,
    enabled      smallint     NOT NULL DEFAULT 1,
    created_by   bigint,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted      smallint     NOT NULL DEFAULT 0,
    version      integer      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_hub_wf_callback
    ON hub_wf_callback (tenant_id, process_key) WHERE deleted = 0;
CREATE TRIGGER trg_hub_wf_callback_updated_at BEFORE UPDATE ON hub_wf_callback
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_wf_callback ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_wf_callback_tenant ON hub_wf_callback
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_wf_callback_platform ON hub_wf_callback
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_wf_callback TO fl_app, fl_platform_role;
COMMENT ON TABLE hub_wf_callback IS '工作流回调配置（C-12 待文档同步）';
