-- P0 文档缺口补表（C-12）：Webhook 订阅。待文档同步。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_webhook_subscription (
    id          bigint       PRIMARY KEY,
    tenant_id   bigint       NOT NULL,
    code        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    target_url  varchar(512) NOT NULL,
    secret_enc  text,
    event_types varchar(512),
    status      varchar(32)  NOT NULL DEFAULT 'ACTIVE',
    enabled     smallint     NOT NULL DEFAULT 1,
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_hub_webhook_subscription
    ON hub_webhook_subscription (tenant_id, code) WHERE deleted = 0;
CREATE TRIGGER trg_hub_webhook_subscription_updated_at BEFORE UPDATE ON hub_webhook_subscription
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_webhook_subscription ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_webhook_subscription_tenant ON hub_webhook_subscription
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_webhook_subscription_platform ON hub_webhook_subscription
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_webhook_subscription TO fl_app, fl_platform_role;
COMMENT ON TABLE hub_webhook_subscription IS 'Webhook 订阅（C-12 待文档同步）';
