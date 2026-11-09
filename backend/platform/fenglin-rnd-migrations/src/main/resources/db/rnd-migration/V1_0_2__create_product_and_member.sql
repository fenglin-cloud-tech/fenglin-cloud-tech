-- E1：产品、产品成员、需求状态定义
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_product (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_code varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    product_line varchar(64),
    owner_id bigint NOT NULL,
    visibility varchar(16) NOT NULL DEFAULT 'private',
    sprint_cycle_days int NOT NULL DEFAULT 14,
    requirement_scheme_id bigint,
    description text,
    status varchar(16) NOT NULL DEFAULT 'active',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_product IS '产品';
COMMENT ON COLUMN devops_product.visibility IS '可见范围：private/public';
COMMENT ON COLUMN devops_product.status IS '状态：active/archived';

CREATE TABLE devops_product_member (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_code varchar(32) NOT NULL,
    data_scope varchar(16) NOT NULL DEFAULT 'self',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_product_member IS '产品成员';
COMMENT ON COLUMN devops_product_member.role_code IS '角色：owner/admin/developer/tester/reporter';
COMMENT ON COLUMN devops_product_member.data_scope IS '数据范围：self/dept/dept_sub/dept_list/all';

CREATE TABLE devops_requirement_status (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    status_code varchar(32) NOT NULL,
    status_name varchar(64) NOT NULL,
    category varchar(16) NOT NULL,
    color varchar(16),
    sort_no int NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_requirement_status IS '需求状态定义（product_id=0 表示租户级默认方案，服务创建产品时按产品落库）';
COMMENT ON COLUMN devops_requirement_status.category IS '类别：todo/doing/done';
