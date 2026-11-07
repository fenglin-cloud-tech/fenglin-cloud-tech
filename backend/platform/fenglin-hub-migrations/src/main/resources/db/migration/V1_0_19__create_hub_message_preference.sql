-- P0 文档缺口补表（C-12）：消息偏好。待文档同步。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_message_preference (
    id          bigint      PRIMARY KEY,
    tenant_id   bigint      NOT NULL,
    user_id     bigint      NOT NULL,
    event_type  varchar(64) NOT NULL,
    channel     varchar(32) NOT NULL,
    enabled     smallint    NOT NULL DEFAULT 1,
    quiet_hours varchar(32),
    created_by  bigint,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted     smallint    NOT NULL DEFAULT 0,
    version     integer     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_hub_message_preference
    ON hub_message_preference (tenant_id, user_id, event_type, channel) WHERE deleted = 0;
CREATE INDEX idx_hub_message_preference_tenant ON hub_message_preference (tenant_id, user_id);
CREATE TRIGGER trg_hub_message_preference_updated_at BEFORE UPDATE ON hub_message_preference
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_message_preference ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_message_preference_tenant ON hub_message_preference
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_message_preference_platform ON hub_message_preference
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_message_preference TO fl_app, fl_platform_role;
COMMENT ON TABLE hub_message_preference IS '消息偏好（C-12 待文档同步）';
