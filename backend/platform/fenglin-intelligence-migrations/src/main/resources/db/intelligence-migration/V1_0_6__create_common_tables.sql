-- 公共支撑表：数据权限、租户盐、写接口幂等
SET search_path TO fenglin_intelligence, public;

CREATE TABLE ai_data_permission (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    subject_type varchar(16) NOT NULL,
    subject_id bigint NOT NULL,
    resource_type varchar(24) NOT NULL,
    resource_id bigint,
    data_scope varchar(24) NOT NULL DEFAULT 'SELF',
    scope_dept_ids bigint[],
    column_hidden varchar(512),
    row_filter_expr text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_data_permission IS 'BI 数据权限（行过滤/列脱敏）';
COMMENT ON COLUMN ai_data_permission.subject_type IS 'USER/ROLE/DEPT';
COMMENT ON COLUMN ai_data_permission.data_scope IS 'ALL/DEPT_AND_CHILD/DEPT/SELF/CUSTOM';
COMMENT ON COLUMN ai_data_permission.column_hidden IS '需隐藏的字段，逗号分隔';

CREATE TABLE ai_tenant_key (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    salt varchar(128) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_tenant_key IS '租户盐（PII/凭据指纹哈希用；文档未列，按总纲兜底）';

CREATE TABLE ai_idempotency_record (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    idem_key varchar(64) NOT NULL,
    api_path varchar(128) NOT NULL,
    biz_id bigint NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_idempotency_record IS '写接口幂等记录（文档未列，按总纲兜底）';
