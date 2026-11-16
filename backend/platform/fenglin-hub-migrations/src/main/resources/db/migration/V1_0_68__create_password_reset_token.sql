-- PLAT-22：忘记密码/一次性重置令牌 + 用户级令牌版本（旧 access token 失效）
SET search_path TO fenglin_hub, public;

-- 1) 用户令牌版本：access token 携带 tv，改密/重置 +1，网关比对后旧令牌立即 401
ALTER TABLE hub_user ADD COLUMN IF NOT EXISTS token_version bigint NOT NULL DEFAULT 0;

-- 2) 一次性口令重置令牌：只存哈希；30 分钟有效；单次使用；使用即写 used_at
CREATE TABLE hub_password_reset_token (
    id             bigint       NOT NULL,
    tenant_id      bigint       NOT NULL,
    user_id        bigint       NOT NULL,
    token_hash     char(64)     NOT NULL,
    request_ip     varchar(64),
    request_source varchar(16)  NOT NULL DEFAULT 'SELF',
    expires_at     timestamptz  NOT NULL,
    used_at        timestamptz,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    deleted        smallint     NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_password_reset_token PRIMARY KEY (id),
    CONSTRAINT ck_password_reset_source CHECK (request_source IN ('SELF', 'ADMIN'))
);

CREATE UNIQUE INDEX uk_password_reset_token_hash
    ON hub_password_reset_token (token_hash)
    WHERE deleted = 0;
CREATE INDEX idx_password_reset_token_user
    ON hub_password_reset_token (tenant_id, user_id, expires_at DESC)
    WHERE deleted = 0;

ALTER TABLE hub_password_reset_token ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_password_reset_token_tenant ON hub_password_reset_token
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_password_reset_token_platform ON hub_password_reset_token
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE ON hub_password_reset_token TO fl_app, fl_platform_role;

COMMENT ON TABLE hub_password_reset_token IS '忘记密码/管理员一次性重置令牌（PLAT-22），仅存 SHA-256 哈希，单次使用';
COMMENT ON COLUMN hub_user.token_version IS 'PLAT-22 用户令牌版本：改密/重置递增，用于旧 access token 立即失效';
