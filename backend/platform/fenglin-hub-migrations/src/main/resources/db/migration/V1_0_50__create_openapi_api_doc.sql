-- 开放平台接口文档元数据 + 在线联调沙箱（OPEN-01）。
-- 口径：接口文档是平台统一发布的开放资源清单，不含租户数据，故不建 tenant_id、不启用 RLS；
--       与 hub_permission 一致，按平台全局字典处理，fl_app 只读、fl_platform_role 可维护。
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_api_doc (
    id              bigint       NOT NULL,
    code            varchar(64)  NOT NULL,
    method          varchar(8)   NOT NULL,
    path            varchar(256) NOT NULL,
    name            varchar(128) NOT NULL,
    domain          varchar(32)  NOT NULL,
    summary         varchar(512),
    description     text,
    required_scope  varchar(128),
    request_schema  text,
    response_schema text,
    sample_request  text,
    sample_response text,
    sandbox_enabled smallint     NOT NULL DEFAULT 1,
    status          varchar(16)  NOT NULL DEFAULT 'PUBLISHED',
    sort            integer      NOT NULL DEFAULT 0,
    created_by      bigint,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      bigint,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted         smallint     NOT NULL DEFAULT 0,
    version         integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_api_doc PRIMARY KEY (id),
    CONSTRAINT uk_hub_api_doc_code UNIQUE (code)
);

CREATE INDEX idx_hub_api_doc_domain ON hub_api_doc (domain, sort) WHERE deleted = 0;
CREATE TRIGGER trg_hub_api_doc_updated_at BEFORE UPDATE ON hub_api_doc
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT ON hub_api_doc TO fl_app, fl_platform_role;
GRANT INSERT, UPDATE, DELETE ON hub_api_doc TO fl_platform_role;

-- 沙箱属于开发者门户能力，单独授权，避免与 portal:view 混用。
INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8195, 'biz:openapi:sandbox:call', '开发者门户在线联调沙箱调用', 'API', 'openapi', 65, 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO hub_role_permission
    (id, tenant_id, role_id, permission_code, created_at, updated_at, deleted, version)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY r.tenant_id) AS id,
       r.tenant_id,
       r.id AS role_id,
       'biz:openapi:sandbox:call' AS permission_code,
       now(),
       now(),
       0,
       0
FROM hub_role r
CROSS JOIN (SELECT COALESCE(max(id), 0) AS max_id FROM hub_role_permission) base
WHERE r.code = 'TENANT_ADMIN'
  AND r.deleted = 0
ON CONFLICT (tenant_id, role_id, permission_code) DO NOTHING;

-- 当前服务内真实发布的开放接口只有验签探针；业务域开放资源发布时在此表追加即可。
INSERT INTO hub_api_doc (id, code, method, path, name, domain, summary, description, required_scope,
                         request_schema, response_schema, sample_request, sample_response,
                         sandbox_enabled, status, sort)
VALUES (9201, 'openapi.ping.v1', 'GET', '/openapi/ping/v1/ping', '验签自检探针', 'platform',
        '校验 AppKey/AppSecret 与 HMAC 签名链路，回显调用方 app_id 与 tenant_id',
        '无需业务权限订阅，仅要求 AppKey 状态 ACTIVE、应用 ENABLED 且未过期；用于联调自检与健康检查。',
        NULL,
        $json$
        {"type":"object","properties":{},"description":"GET 请求无请求体；签名基于空 body 的 SHA-256"}
        $json$,
        $json$
        {"type":"object","properties":{"code":{"type":"string"},"message":{"type":"string"},"data":{"type":"object","properties":{"app_id":{"type":"integer"},"tenant_id":{"type":"integer"},"path":{"type":"string"}}}}}
        $json$,
        $json$
        {"method":"GET","path":"/openapi/ping/v1/ping","headers":{"X-App-Key":"AK...","X-Timestamp":"秒级时间戳","X-Nonce":"一次性随机串","X-Signature":"hmac_sha256_hex(AppSecret, METHOD+PATH+TIMESTAMP+NONCE+sha256_hex(body))"}}
        $json$,
        $json$
        {"code":"0","message":"success","data":{"app_id":1101,"tenant_id":7501,"path":"/openapi/ping/v1/ping"}}
        $json$,
        1, 'PUBLISHED', 10)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_api_doc IS '开放平台接口文档元数据（平台全局发布，供开发者门户展示与联调沙箱使用）';
COMMENT ON COLUMN hub_api_doc.required_scope IS '调用该接口需订阅的权限码；为空表示无需业务订阅';
COMMENT ON COLUMN hub_api_doc.sandbox_enabled IS '是否允许门户沙箱调用：1 允许、0 禁止';
