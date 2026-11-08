-- P3 开放平台（HUB-API-1001~1006）
-- 依据 00 数据库设计 4.39~4.42：hub_open_app / hub_app_key / hub_api_quota / hub_api_call_log。
-- 文档缺口兜底：
--   1) hub_api_quota 增加 used_date（文档仅有 used_today，缺按日重置依据）；
--   2) hub_app_key 增加 secret_fingerprint（接口 1003 需回显指纹，文档未定义列）；
--   3) hub_api_call_log 采用 RANGE(called_at) 分区，本期仅建 DEFAULT 分区，月度预建与归档待调度补齐；
--   4) AppKey 需在无租户上下文时解析归属租户，新增 SECURITY DEFINER 函数 resolve_open_app_key。
SET search_path TO fenglin_hub, public;

CREATE SEQUENCE IF NOT EXISTS hub_open_app_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE hub_open_app (
    id            bigint       NOT NULL,
    tenant_id     bigint       NOT NULL,
    code          varchar(40)  NOT NULL,
    name          varchar(128) NOT NULL,
    owner_type    varchar(16)  NOT NULL DEFAULT 'INTERNAL',
    owner_id      bigint,
    scopes        varchar(1024),
    redirect_uris varchar(1024),
    review_status varchar(16)  NOT NULL DEFAULT 'PENDING',
    status        varchar(16)  NOT NULL DEFAULT 'DISABLED',
    created_by    bigint,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted       smallint     NOT NULL DEFAULT 0,
    version       integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_open_app PRIMARY KEY (id),
    CONSTRAINT uk_hub_open_app_code UNIQUE (code)
);

CREATE INDEX idx_open_app_tenant ON hub_open_app (tenant_id, created_at DESC) WHERE deleted = 0;
CREATE INDEX idx_open_app_tenant_name ON hub_open_app (tenant_id, name) WHERE deleted = 0;
CREATE TRIGGER trg_hub_open_app_updated_at BEFORE UPDATE ON hub_open_app
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_app_key (
    id                 bigint      NOT NULL,
    tenant_id          bigint      NOT NULL,
    app_id             bigint      NOT NULL,
    app_key            varchar(64) NOT NULL,
    app_secret_enc     text        NOT NULL,
    secret_fingerprint varchar(16),
    status             varchar(16) NOT NULL DEFAULT 'ACTIVE',
    expire_at          timestamptz,
    created_by         bigint,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_by         bigint,
    updated_at         timestamptz NOT NULL DEFAULT now(),
    deleted            smallint    NOT NULL DEFAULT 0,
    version            integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_app_key PRIMARY KEY (id),
    CONSTRAINT uk_hub_app_key_key UNIQUE (app_key)
);

CREATE INDEX idx_app_key_app ON hub_app_key (tenant_id, app_id, status) WHERE deleted = 0;
CREATE TRIGGER trg_hub_app_key_updated_at BEFORE UPDATE ON hub_app_key
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_api_quota (
    id          bigint       NOT NULL,
    tenant_id   bigint       NOT NULL,
    app_id      bigint       NOT NULL,
    api_path    varchar(256) NOT NULL,
    qps_limit   integer,
    daily_limit bigint,
    used_today  bigint       NOT NULL DEFAULT 0,
    used_date   date         NOT NULL DEFAULT CURRENT_DATE,
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_api_quota PRIMARY KEY (id),
    CONSTRAINT uk_hub_api_quota_app_path UNIQUE (app_id, api_path)
);

CREATE INDEX idx_api_quota_tenant ON hub_api_quota (tenant_id, app_id);
CREATE TRIGGER trg_hub_api_quota_updated_at BEFORE UPDATE ON hub_api_quota
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_api_call_log (
    id          bigint       NOT NULL,
    tenant_id   bigint       NOT NULL,
    app_id      bigint       NOT NULL,
    app_key     varchar(64),
    api_path    varchar(256) NOT NULL,
    method      varchar(8),
    http_status integer,
    biz_code    varchar(16),
    latency_ms  integer,
    request_id  varchar(64),
    called_at   timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_api_call_log PRIMARY KEY (id, called_at)
) PARTITION BY RANGE (called_at);

CREATE TABLE hub_api_call_log_default PARTITION OF hub_api_call_log DEFAULT;

CREATE INDEX idx_call_app ON hub_api_call_log (tenant_id, app_id, called_at DESC);
CREATE INDEX idx_call_path ON hub_api_call_log (tenant_id, api_path, called_at DESC);

ALTER TABLE hub_open_app ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_app_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_api_quota ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_api_call_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_hub_open_app_tenant ON hub_open_app
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_open_app_platform ON hub_open_app
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

CREATE POLICY p_hub_app_key_tenant ON hub_app_key
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_app_key_platform ON hub_app_key
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

CREATE POLICY p_hub_api_quota_tenant ON hub_api_quota
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_api_quota_platform ON hub_api_quota
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

CREATE POLICY p_hub_api_call_log_tenant ON hub_api_call_log
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_api_call_log_platform ON hub_api_call_log
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT USAGE, SELECT ON SEQUENCE hub_open_app_code_seq TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_open_app TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_app_key TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_api_quota TO fl_app, fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_api_call_log TO fl_app, fl_platform_role;

-- AppKey 全局唯一，签名验签时需要先解析归属租户；函数以属主身份执行，绕过 RLS 完成定位。
CREATE OR REPLACE FUNCTION resolve_open_app_key(p_app_key varchar)
RETURNS TABLE (tenant_id bigint, app_id bigint, app_secret_enc text,
               key_status varchar, expire_at timestamptz, app_status varchar)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = fenglin_hub, public
AS $$
    SELECT k.tenant_id, k.app_id, k.app_secret_enc, k.status, k.expire_at, a.status
      FROM hub_app_key k
      JOIN hub_open_app a ON a.id = k.app_id AND a.tenant_id = k.tenant_id AND a.deleted = 0
     WHERE k.app_key = p_app_key AND k.deleted = 0
$$;

GRANT EXECUTE ON FUNCTION resolve_open_app_key(varchar) TO fl_app, fl_platform_role;

-- 契约缺口兜底：1004/1005/1006 未在接口文档声明权限码，按平台/业务端补充独立权限点。
INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8151, 'platform:openapi:app:review', '开放应用注册与审核', 'API', 'openapi', 51, 0),
       (8152, 'platform:openapi:key:manage', '开放应用密钥管理', 'API', 'openapi', 52, 0),
       (8153, 'platform:openapi:quota:manage', '开放应用配额管理', 'API', 'openapi', 53, 0),
       (8154, 'platform:openapi:calllog:view', '开放平台调用日志查看', 'API', 'openapi', 54, 0),
       (8155, 'biz:openapi:portal:view', '开发者门户应用查看', 'API', 'openapi', 55, 0)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_open_app IS '开放应用（HUB-API-1001/1002）';
COMMENT ON TABLE hub_app_key IS '开放应用密钥，AppSecret 仅 AES-GCM 密文落库（HUB-API-1003）';
COMMENT ON TABLE hub_api_quota IS '开放 API 配额；used_date 为按日重置兜底字段（HUB-API-1004）';
COMMENT ON TABLE hub_api_call_log IS '开放 API 调用日志，按月分区（本期 DEFAULT 分区兜底，HUB-API-1005）';
