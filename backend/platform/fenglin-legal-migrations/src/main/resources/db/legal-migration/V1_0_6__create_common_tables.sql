-- 通用附件表 + 总纲兜底表（租户 PII 盐、写接口幂等记录）
-- 标注：legal_tenant_key / legal_idempotency_record 为总纲兜底扩展表，不改变文档既有 27 张业务表定义。
SET search_path TO fenglin_legal, public;

CREATE TABLE legal_attachment (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    object_type varchar(32) NOT NULL,
    object_id bigint NOT NULL,
    file_name varchar(255) NOT NULL,
    file_ref varchar(255) NOT NULL,
    file_hash varchar(64),
    file_size bigint,
    mime_type varchar(128),
    sensitive_flag boolean DEFAULT false,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_attachment IS '通用附件（多态关联合同/模板/事件等）';

CREATE TABLE legal_tenant_key (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    salt varchar(64) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_tenant_key IS '租户 PII 哈希盐（按租户隔离，总纲兜底表）';

CREATE TABLE legal_idempotency_record (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    idem_key varchar(64) NOT NULL,
    api_path varchar(128) NOT NULL,
    biz_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE legal_idempotency_record IS '写接口幂等记录（总纲兜底表）';
