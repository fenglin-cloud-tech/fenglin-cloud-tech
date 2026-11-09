-- F2：数据模型、模型字段、页面 Schema、应用发布记录
SET search_path TO fenglin_service, public;

CREATE TABLE svc_data_model (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    app_id bigint NOT NULL,
    model_key varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    version_no int NOT NULL DEFAULT 1,
    engine_ref varchar(64),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_data_model IS '数据模型';
COMMENT ON COLUMN svc_data_model.status IS '状态：DRAFT/PUBLISHED/ARCHIVED';

CREATE TABLE svc_data_model_field (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    model_id bigint NOT NULL,
    field_key varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    data_type varchar(32) NOT NULL,
    length int,
    nullable boolean NOT NULL DEFAULT true,
    unique_flag boolean NOT NULL DEFAULT false,
    indexed boolean NOT NULL DEFAULT false,
    ref_model_id bigint,
    ref_type varchar(8),
    calc_expr varchar(512),
    sort_no int NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_data_model_field IS '数据模型字段';
COMMENT ON COLUMN svc_data_model_field.data_type IS '类型：STRING/INTEGER/DECIMAL/DATE/DATETIME/BOOLEAN/JSON/REF';
COMMENT ON COLUMN svc_data_model_field.ref_type IS '关联类型：1:1/1:N/N:N';

CREATE TABLE svc_page_schema (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    app_id bigint NOT NULL,
    page_key varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    page_type varchar(16),
    schema_json jsonb NOT NULL,
    data_binding jsonb,
    perm_json jsonb,
    version_no int NOT NULL DEFAULT 1,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_page_schema IS '页面搭建定义';
COMMENT ON COLUMN svc_page_schema.page_type IS '类型：LIST/DETAIL/DASHBOARD/FORM/KANBAN';
COMMENT ON COLUMN svc_page_schema.status IS '状态：DRAFT/PUBLISHED/ARCHIVED';

CREATE TABLE svc_app_release (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    app_id bigint NOT NULL,
    release_no varchar(32) NOT NULL,
    from_env varchar(8),
    to_env varchar(8) NOT NULL,
    snapshot_json jsonb NOT NULL,
    approval_status varchar(16) NOT NULL DEFAULT 'PENDING',
    approver_id bigint,
    approved_at timestamptz,
    rollback_from varchar(32),
    status varchar(16) NOT NULL DEFAULT 'RELEASED',
    release_note varchar(512),
    script_scan_json jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_app_release IS '应用发布记录';
COMMENT ON COLUMN svc_app_release.approval_status IS '审批状态：PENDING/APPROVED/REJECTED';
COMMENT ON COLUMN svc_app_release.status IS '状态：RELEASED/ROLLED_BACK/FAILED';
