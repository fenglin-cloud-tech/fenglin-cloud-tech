-- 公共：附件、幂等记录、租户盐
-- 偏差标注：devops_tenant_key 文档未列，按总纲 PII（作者邮箱/集成令牌）哈希盐管理兜底补全。
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_attachment (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    biz_type varchar(32) NOT NULL,
    biz_id bigint NOT NULL,
    file_id varchar(64) NOT NULL,
    file_name varchar(256) NOT NULL,
    file_size bigint NOT NULL,
    mime_type varchar(128),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_attachment IS '附件（多态：biz_type + biz_id）';

CREATE TABLE devops_idempotency_record (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    idem_key varchar(128) NOT NULL,
    api_path varchar(256) NOT NULL,
    request_hash varchar(128),
    biz_id bigint,
    response_body jsonb,
    expire_at timestamptz NOT NULL DEFAULT now() + interval '24 hours',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_idempotency_record IS '幂等记录（Idempotency-Key）';

CREATE TABLE devops_tenant_key (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    salt varchar(128) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_tenant_key IS '租户 PII 哈希盐（文档未列，按总纲兜底补全）';
COMMENT ON COLUMN devops_tenant_key.salt IS '32 字节随机盐，标准 Base64 编码';
