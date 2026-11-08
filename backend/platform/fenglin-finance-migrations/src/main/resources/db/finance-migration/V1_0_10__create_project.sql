-- D2：项目、成员、里程碑、人工费率
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_project (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    project_no varchar(32) NOT NULL,
    project_name varchar(128) NOT NULL,
    project_type varchar(16) NOT NULL,
    customer_id bigint,
    customer_name varchar(128),
    contract_no varchar(64),
    contract_amount numeric(20,4) NOT NULL DEFAULT 0,
    budget_amount numeric(20,4) NOT NULL DEFAULT 0,
    estimated_total_cost numeric(20,4) NOT NULL DEFAULT 0,
    actual_cost numeric(20,4) NOT NULL DEFAULT 0,
    recognized_revenue numeric(20,4) NOT NULL DEFAULT 0,
    start_date date,
    end_date date,
    manager_id bigint,
    dept_id bigint,
    currency_code varchar(3) NOT NULL,
    status varchar(16) NOT NULL,
    description varchar(512),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_project IS '项目（立项→执行→结算）';
COMMENT ON COLUMN fin_project.project_type IS '类型：INTERNAL/EXTERNAL/RD';
COMMENT ON COLUMN fin_project.status IS '状态：PORTAL/APPROVING/ACTIVE/SUSPENDED/SETTLING/CLOSED';

CREATE TABLE fin_project_member (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    project_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    employee_name varchar(64),
    member_role varchar(32),
    allocation_pct numeric(9,6) NOT NULL DEFAULT 1,
    cost_rate numeric(20,4),
    join_date date NOT NULL,
    leave_date date,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_project_member IS '项目成员';
COMMENT ON COLUMN fin_project_member.status IS '状态：ACTIVE/LEFT';

CREATE TABLE fin_project_milestone (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    project_id bigint NOT NULL,
    milestone_name varchar(128) NOT NULL,
    plan_date date NOT NULL,
    actual_date date,
    deliverable varchar(512),
    weight numeric(9,6) NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_project_milestone IS '项目里程碑';
COMMENT ON COLUMN fin_project_milestone.status IS '状态：PENDING/DONE/DELAYED';

CREATE TABLE fin_labor_rate (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint,
    member_role varchar(32),
    cost_rate numeric(20,4) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_labor_rate IS '人工成本费率（人日）';
