-- P1 认证域迁移：hub_login_session、hub_sso_config（T1-09）
-- 补表：hub_auth_token（refresh token 轮换持久化）、hub_auth_revocation（访问令牌撤销）
-- 说明：文档只定义 Redis 黑名单，未定义 refresh token 持久化表，此两表为 C-12 同类缺口，待文档同步

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_login_session (
    id          bigint      PRIMARY KEY,
    tenant_id   bigint      NOT NULL,
    session_id  varchar(64) NOT NULL,
    user_id     bigint      NOT NULL,
    client_type varchar(16) NOT NULL,
    login_type  varchar(24) NOT NULL DEFAULT 'PASSWORD',
    ip_enc      text,
    user_agent  varchar(512),
    device_id   varchar(128),
    login_at    timestamptz NOT NULL DEFAULT now(),
    expire_at   timestamptz NOT NULL,
    status      varchar(16) NOT NULL DEFAULT 'ONLINE',
    created_by  bigint,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted     smallint    NOT NULL DEFAULT 0,
    version     integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_login_session UNIQUE (session_id)
);
CREATE INDEX idx_hub_sess_user ON hub_login_session (tenant_id, user_id, status);
CREATE INDEX idx_hub_sess_expire ON hub_login_session (tenant_id, expire_at);
CREATE TRIGGER trg_hub_login_session_updated_at BEFORE UPDATE ON hub_login_session
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_sso_config (
    id                bigint       PRIMARY KEY,
    tenant_id         bigint       NOT NULL,
    protocol          varchar(16)  NOT NULL,
    client_id         varchar(128),
    client_secret_enc text,
    issuer            varchar(256),
    sso_url           varchar(512),
    metadata_url      varchar(512),
    attribute_mapping text,
    auto_provision    smallint     NOT NULL DEFAULT 0,
    status            varchar(16)  NOT NULL DEFAULT 'DISABLED',
    created_by        bigint,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        bigint,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted           smallint     NOT NULL DEFAULT 0,
    version           integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_sso_config UNIQUE (tenant_id, protocol)
);
CREATE TRIGGER trg_hub_sso_config_updated_at BEFORE UPDATE ON hub_sso_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_auth_token (
    id          bigint      PRIMARY KEY,
    tenant_id   bigint      NOT NULL,
    user_id     bigint      NOT NULL,
    session_id  varchar(64) NOT NULL,
    token_hash  varchar(64) NOT NULL,
    token_type  varchar(16) NOT NULL DEFAULT 'REFRESH',
    client_type varchar(16) NOT NULL,
    issued_at   timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    revoked     smallint    NOT NULL DEFAULT 0,
    rotated_at  timestamptz,
    created_by  bigint,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted     smallint    NOT NULL DEFAULT 0,
    version     integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_auth_token UNIQUE (token_hash)
);
CREATE INDEX idx_hub_auth_token_session ON hub_auth_token (tenant_id, session_id);
CREATE INDEX idx_hub_auth_token_user ON hub_auth_token (tenant_id, user_id);
CREATE TRIGGER trg_hub_auth_token_updated_at BEFORE UPDATE ON hub_auth_token
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_auth_revocation (
    id         bigint      PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    jti        varchar(64) NOT NULL,
    user_id    bigint,
    token_type varchar(16) NOT NULL DEFAULT 'ACCESS',
    reason     varchar(128),
    revoked_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    deleted    smallint    NOT NULL DEFAULT 0,
    version    integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_auth_revocation UNIQUE (jti)
);
CREATE INDEX idx_hub_auth_revocation_expire ON hub_auth_revocation (expires_at);
CREATE TRIGGER trg_hub_auth_revocation_updated_at BEFORE UPDATE ON hub_auth_revocation
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON
    hub_login_session, hub_sso_config, hub_auth_token, hub_auth_revocation
    TO fl_app, fl_platform_role;

COMMENT ON TABLE hub_login_session IS '登录会话（P1 认证域）';
COMMENT ON TABLE hub_auth_token IS 'refresh token 轮换记录，仅存哈希（文档缺口，待同步）';
COMMENT ON TABLE hub_auth_revocation IS '访问令牌撤销列表（文档定义为 Redis，当前 P1 以库表兜底）';
