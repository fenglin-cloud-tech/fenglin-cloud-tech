-- 通用：标签、标签关联、附件、租户盐、幂等记录
SET search_path TO fenglin_service, public;

CREATE TABLE svc_tag (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(64) NOT NULL,
    tag_group varchar(64),
    color varchar(16),
    biz_scope varchar(32),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_tag IS '标签定义';
COMMENT ON COLUMN svc_tag.biz_scope IS '适用对象：TICKET/KB/APP/ROBOT';

CREATE TABLE svc_tag_relation (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    tag_id bigint NOT NULL,
    biz_type varchar(24) NOT NULL,
    biz_id bigint NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_tag_relation IS '标签关联（多态）';

CREATE TABLE svc_attachment (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    biz_type varchar(24) NOT NULL,
    biz_id bigint NOT NULL,
    file_id varchar(64) NOT NULL,
    file_name varchar(255) NOT NULL,
    file_size bigint,
    content_type varchar(128),
    uploader_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_attachment IS '附件（文件服务 fileId 引用）';

CREATE TABLE svc_tenant_key (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    salt varchar(64) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_tenant_key IS '租户 PII 哈希盐（按租户隔离）';

CREATE TABLE svc_idempotency_record (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    idem_key varchar(64) NOT NULL,
    api_path varchar(128) NOT NULL,
    biz_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_idempotency_record IS '写接口幂等记录';
