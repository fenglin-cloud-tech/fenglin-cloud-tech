-- P0 文档缺口补表（C-12）：Webhook 投递记录。待文档同步。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_webhook_delivery (
    id              bigint      PRIMARY KEY,
    tenant_id       bigint      NOT NULL,
    subscription_id bigint      NOT NULL REFERENCES hub_webhook_subscription (id),
    event_id        varchar(64) NOT NULL,
    event_type      varchar(64) NOT NULL,
    payload         jsonb,
    status          varchar(32) NOT NULL DEFAULT 'PENDING',
    retry_count     integer     NOT NULL DEFAULT 0,
    next_retry_at   timestamptz,
    response_code   integer,
    response_body   text,
    delivered_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_hub_webhook_delivery_retry
    ON hub_webhook_delivery (tenant_id, status, next_retry_at);
CREATE INDEX idx_hub_webhook_delivery_event ON hub_webhook_delivery (event_id);

ALTER TABLE hub_webhook_delivery ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_webhook_delivery_tenant ON hub_webhook_delivery
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_webhook_delivery_platform ON hub_webhook_delivery
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_webhook_delivery TO fl_app, fl_platform_role;
COMMENT ON TABLE hub_webhook_delivery IS 'Webhook 投递记录（C-12 待文档同步）';
