-- P1 IAM 域迁移：16 张表（对应方案 T1-04）
-- 与《数据库设计文档》差异：hub_account 按裁决 C-06 拆分为 password_hash（BCrypt），*_enc 仅存可解密字段
-- 版本号说明：方案写作 V1_0_3/4，但平台库已应用到 V1_0_26，按 Flyway 顺序执行改为 V1_0_27

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_user (
    id                  bigint       PRIMARY KEY,
    tenant_id           bigint       NOT NULL,
    uuid                uuid         NOT NULL DEFAULT gen_random_uuid(),
    employee_no         varchar(32),
    username            varchar(64)  NOT NULL,
    nickname            varchar(64),
    real_name           varchar(64),
    mobile_enc          text,
    mobile_hash         varchar(64),
    email_enc           text,
    email_hash          varchar(64),
    avatar              varchar(512),
    primary_org_id      bigint,
    primary_position_id bigint,
    user_type           varchar(16)  NOT NULL DEFAULT 'INTERNAL',
    status              varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled         smallint     NOT NULL DEFAULT 0,
    last_login_at       timestamptz,
    created_by          bigint,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          bigint,
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    deleted             smallint     NOT NULL DEFAULT 0,
    version             integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_user_uuid UNIQUE (uuid),
    CONSTRAINT uk_hub_user_username UNIQUE (tenant_id, username),
    CONSTRAINT uk_hub_user_employee_no UNIQUE (tenant_id, employee_no)
);
CREATE INDEX idx_hub_user_tenant ON hub_user (tenant_id);
CREATE INDEX idx_hub_user_mobile_hash ON hub_user (tenant_id, mobile_hash);
CREATE INDEX idx_hub_user_email_hash ON hub_user (tenant_id, email_hash);
CREATE INDEX idx_hub_user_org ON hub_user (tenant_id, primary_org_id);
CREATE TRIGGER trg_hub_user_updated_at BEFORE UPDATE ON hub_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_account (
    id                  bigint      PRIMARY KEY,
    tenant_id           bigint      NOT NULL,
    user_id             bigint      NOT NULL,
    password_hash       text        NOT NULL,
    password_updated_at timestamptz NOT NULL DEFAULT now(),
    fail_count          integer     NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    mfa_secret_enc      text,
    pwd_reset_required  smallint    NOT NULL DEFAULT 0,
    created_by          bigint,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          bigint,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted             smallint    NOT NULL DEFAULT 0,
    version             integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_account_user UNIQUE (tenant_id, user_id)
);
CREATE INDEX idx_hub_account_tenant ON hub_account (tenant_id);
CREATE TRIGGER trg_hub_account_updated_at BEFORE UPDATE ON hub_account
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_org (
    id          bigint       PRIMARY KEY,
    tenant_id   bigint       NOT NULL,
    parent_id   bigint,
    path_code   varchar(64),
    name        varchar(128) NOT NULL,
    org_type    varchar(16)  NOT NULL DEFAULT 'DEPT',
    leader_id   bigint,
    sort        integer      NOT NULL DEFAULT 0,
    status      varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0
);
CREATE INDEX idx_hub_org_tenant ON hub_org (tenant_id);
CREATE INDEX idx_hub_org_path ON hub_org (tenant_id, path_code);
CREATE INDEX idx_hub_org_parent ON hub_org (tenant_id, parent_id);
CREATE TRIGGER trg_hub_org_updated_at BEFORE UPDATE ON hub_org
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_user_org (
    id         bigint      PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    user_id    bigint      NOT NULL,
    org_id     bigint      NOT NULL,
    is_primary smallint    NOT NULL DEFAULT 0,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted    smallint    NOT NULL DEFAULT 0,
    version    integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_user_org UNIQUE (tenant_id, user_id, org_id)
);
CREATE INDEX idx_hub_user_org_user ON hub_user_org (tenant_id, user_id);
CREATE INDEX idx_hub_user_org_org ON hub_user_org (tenant_id, org_id);
CREATE TRIGGER trg_hub_user_org_updated_at BEFORE UPDATE ON hub_user_org
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_position (
    id         bigint      PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    code       varchar(32) NOT NULL,
    name       varchar(64) NOT NULL,
    rank       varchar(16),
    org_id     bigint,
    status     varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted    smallint    NOT NULL DEFAULT 0,
    version    integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_position_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_hub_position_tenant ON hub_position (tenant_id);
CREATE TRIGGER trg_hub_position_updated_at BEFORE UPDATE ON hub_position
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_user_position (
    id          bigint      PRIMARY KEY,
    tenant_id   bigint      NOT NULL,
    user_id     bigint      NOT NULL,
    position_id bigint      NOT NULL,
    is_primary  smallint    NOT NULL DEFAULT 0,
    created_by  bigint,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted     smallint    NOT NULL DEFAULT 0,
    version     integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_user_position UNIQUE (tenant_id, user_id, position_id)
);
CREATE INDEX idx_hub_user_position_user ON hub_user_position (tenant_id, user_id);
CREATE TRIGGER trg_hub_user_position_updated_at BEFORE UPDATE ON hub_user_position
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_role (
    id          bigint      PRIMARY KEY,
    tenant_id   bigint      NOT NULL,
    code        varchar(64) NOT NULL,
    name        varchar(64) NOT NULL,
    role_type   varchar(16) NOT NULL DEFAULT 'BIZ',
    data_scope  varchar(24) NOT NULL DEFAULT 'SELF',
    parent_id   bigint,
    builtin     smallint    NOT NULL DEFAULT 0,
    status      varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_by  bigint,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted     smallint    NOT NULL DEFAULT 0,
    version     integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_role_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_hub_role_tenant ON hub_role (tenant_id);
CREATE TRIGGER trg_hub_role_updated_at BEFORE UPDATE ON hub_role
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 全局表：权限点由平台统一定义，不含 tenant_id，不启用 RLS
CREATE TABLE hub_permission (
    id          bigint       PRIMARY KEY,
    code        varchar(128) NOT NULL,
    name        varchar(64),
    type        varchar(16)  NOT NULL DEFAULT 'API',
    domain      varchar(32),
    parent_code varchar(128),
    sort        integer      NOT NULL DEFAULT 0,
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_permission_code UNIQUE (code)
);
CREATE INDEX idx_hub_permission_domain ON hub_permission (domain);
CREATE INDEX idx_hub_permission_parent ON hub_permission (parent_code);
CREATE TRIGGER trg_hub_permission_updated_at BEFORE UPDATE ON hub_permission
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_role_permission (
    id              bigint       PRIMARY KEY,
    tenant_id       bigint       NOT NULL,
    role_id         bigint       NOT NULL,
    permission_code varchar(128) NOT NULL,
    created_by      bigint,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      bigint,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted         smallint     NOT NULL DEFAULT 0,
    version         integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_role_permission UNIQUE (tenant_id, role_id, permission_code)
);
CREATE INDEX idx_hub_rp_role ON hub_role_permission (tenant_id, role_id);
CREATE INDEX idx_hub_rp_perm ON hub_role_permission (permission_code);
CREATE TRIGGER trg_hub_role_permission_updated_at BEFORE UPDATE ON hub_role_permission
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_user_role (
    id         bigint      PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    user_id    bigint      NOT NULL,
    role_id    bigint      NOT NULL,
    granted_at timestamptz NOT NULL DEFAULT now(),
    expire_at  timestamptz,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted    smallint    NOT NULL DEFAULT 0,
    version    integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_user_role UNIQUE (tenant_id, user_id, role_id)
);
CREATE INDEX idx_hub_ur_user ON hub_user_role (tenant_id, user_id);
CREATE INDEX idx_hub_ur_role ON hub_user_role (tenant_id, role_id);
CREATE TRIGGER trg_hub_user_role_updated_at BEFORE UPDATE ON hub_user_role
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_data_scope (
    id              bigint        PRIMARY KEY,
    tenant_id       bigint        NOT NULL,
    subject_type    varchar(16)   NOT NULL,
    subject_id      bigint        NOT NULL,
    resource_type   varchar(64)   NOT NULL,
    scope_type      varchar(24)   NOT NULL,
    custom_dept_ids varchar(1024),
    rule_expr       text,
    created_by      bigint,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_by      bigint,
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    deleted         smallint      NOT NULL DEFAULT 0,
    version         integer       NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_data_scope UNIQUE (tenant_id, subject_type, subject_id, resource_type)
);
CREATE INDEX idx_hub_ds_subject ON hub_data_scope (tenant_id, subject_type, subject_id);
CREATE TRIGGER trg_hub_data_scope_updated_at BEFORE UPDATE ON hub_data_scope
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_field_permission (
    id            bigint      PRIMARY KEY,
    tenant_id     bigint      NOT NULL,
    subject_type  varchar(16) NOT NULL,
    subject_id    bigint      NOT NULL,
    resource_type varchar(64) NOT NULL,
    field_name    varchar(64) NOT NULL,
    access        varchar(16) NOT NULL,
    created_by    bigint,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    bigint,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    deleted       smallint    NOT NULL DEFAULT 0,
    version       integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_field_permission UNIQUE (tenant_id, subject_type, subject_id, resource_type, field_name)
);
CREATE INDEX idx_hub_fp_subject ON hub_field_permission (tenant_id, subject_type, subject_id);
CREATE TRIGGER trg_hub_field_permission_updated_at BEFORE UPDATE ON hub_field_permission
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 全局表：菜单由平台统一定义
CREATE TABLE hub_menu (
    id          bigint       PRIMARY KEY,
    code        varchar(64)  NOT NULL,
    name        varchar(64),
    client_type varchar(16),
    path        varchar(256),
    component   varchar(256),
    parent_code varchar(64),
    icon        varchar(64),
    sort        integer      NOT NULL DEFAULT 0,
    visible     smallint     NOT NULL DEFAULT 1,
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_menu_code UNIQUE (code)
);
CREATE INDEX idx_hub_menu_client ON hub_menu (client_type);
CREATE TRIGGER trg_hub_menu_updated_at BEFORE UPDATE ON hub_menu
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 全局表：字典类型由平台统一定义
CREATE TABLE hub_dict_type (
    id         bigint      PRIMARY KEY,
    code       varchar(64) NOT NULL,
    name       varchar(64),
    scope      varchar(16) NOT NULL DEFAULT 'PLATFORM',
    status     smallint    NOT NULL DEFAULT 1,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted    smallint    NOT NULL DEFAULT 0,
    version    integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_dict_type_code UNIQUE (code)
);
CREATE TRIGGER trg_hub_dict_type_updated_at BEFORE UPDATE ON hub_dict_type
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_dict_item (
    id         bigint       PRIMARY KEY,
    tenant_id  bigint       NOT NULL,
    dict_code  varchar(64)  NOT NULL,
    item_value varchar(64)  NOT NULL,
    item_label varchar(128),
    sort       integer      NOT NULL DEFAULT 0,
    status     smallint     NOT NULL DEFAULT 1,
    created_by bigint,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz  NOT NULL DEFAULT now(),
    deleted    smallint     NOT NULL DEFAULT 0,
    version    integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_dict_item UNIQUE (tenant_id, dict_code, item_value)
);
CREATE INDEX idx_hub_dict_item_code ON hub_dict_item (tenant_id, dict_code);
CREATE TRIGGER trg_hub_dict_item_updated_at BEFORE UPDATE ON hub_dict_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_param (
    id           bigint       PRIMARY KEY,
    tenant_id    bigint       NOT NULL,
    param_key    varchar(128) NOT NULL,
    param_value  text,
    scope        varchar(16)  NOT NULL DEFAULT 'TENANT',
    product_code varchar(64),
    value_type   varchar(16)  NOT NULL DEFAULT 'STRING',
    remark       varchar(256),
    created_by   bigint,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted      smallint     NOT NULL DEFAULT 0,
    version      integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_param_key UNIQUE (tenant_id, param_key)
);
CREATE INDEX idx_hub_param_tenant ON hub_param (tenant_id);
CREATE TRIGGER trg_hub_param_updated_at BEFORE UPDATE ON hub_param
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON
    hub_user, hub_account, hub_org, hub_user_org, hub_position, hub_user_position,
    hub_role, hub_permission, hub_role_permission, hub_user_role,
    hub_data_scope, hub_field_permission, hub_menu, hub_dict_type, hub_dict_item, hub_param
    TO fl_app, fl_platform_role;

COMMENT ON TABLE hub_user IS '用户（P1 IAM）';
COMMENT ON TABLE hub_account IS '账号凭据（password_hash 为 BCrypt，裁决 C-06）';
COMMENT ON TABLE hub_permission IS '功能权限点，平台全局定义';
COMMENT ON TABLE hub_menu IS '菜单，平台全局定义';
