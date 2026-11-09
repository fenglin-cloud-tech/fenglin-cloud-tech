-- F2：低代码应用定义、表单定义、表单字段
SET search_path TO fenglin_service, public;

CREATE TABLE svc_app_definition (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    app_code varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    icon varchar(128),
    description text,
    owner_id bigint,
    dept_id bigint,
    env varchar(8) NOT NULL DEFAULT 'DRAFT',
    engine_app_ref varchar(64),
    visible_scope jsonb,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    script_risk_level varchar(8),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_app_definition IS '低代码应用定义（运行数据由中台 lowcode 托管）';
COMMENT ON COLUMN svc_app_definition.env IS '环境：DRAFT/PRE/PROD';
COMMENT ON COLUMN svc_app_definition.status IS '状态：DRAFT/PUBLISHED/FROZEN/ARCHIVED';

CREATE TABLE svc_form_definition (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    app_id bigint NOT NULL,
    form_key varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    version_no int NOT NULL DEFAULT 1,
    schema_json jsonb NOT NULL,
    engine_ref varchar(64),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_form_definition IS '表单定义（Schema 元数据）';
COMMENT ON COLUMN svc_form_definition.status IS '状态：DRAFT/PUBLISHED/ARCHIVED';

CREATE TABLE svc_form_field (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    form_id bigint NOT NULL,
    field_key varchar(64) NOT NULL,
    label varchar(128) NOT NULL,
    field_type varchar(32) NOT NULL,
    required boolean NOT NULL DEFAULT false,
    options jsonb,
    validators jsonb,
    linkage jsonb,
    perm_json jsonb,
    sort_no int NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_form_field IS '表单字段';
COMMENT ON COLUMN svc_form_field.field_type IS '类型：TEXT/NUMBER/DATE/SELECT/MULTI/ATTACHMENT/USER/DEPT/RELATION';
