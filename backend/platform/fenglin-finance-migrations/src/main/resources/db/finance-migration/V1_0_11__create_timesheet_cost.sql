-- D2：工时、成本归集、项目结算
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_timesheet (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    timesheet_no varchar(32) NOT NULL,
    employee_id bigint NOT NULL,
    employee_name varchar(64),
    project_id bigint NOT NULL,
    milestone_id bigint,
    work_date date NOT NULL,
    hours numeric(9,2) NOT NULL,
    task_desc varchar(255),
    cost_rate numeric(20,4) NOT NULL DEFAULT 0,
    cost_amount numeric(20,4) NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL,
    approver_id bigint,
    approved_at timestamptz,
    reject_reason varchar(255),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_fin_timesheet_hours CHECK (hours > 0 AND hours <= 24)
);
COMMENT ON TABLE fin_timesheet IS '项目工时（小时÷8×人日费率=成本）';
COMMENT ON COLUMN fin_timesheet.status IS '状态：SUBMITTED/APPROVED/REJECTED';

CREATE TABLE fin_cost_collection (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    cost_no varchar(32) NOT NULL,
    project_id bigint NOT NULL,
    cost_type varchar(16) NOT NULL,
    source_biz_id bigint,
    subject_code varchar(32),
    amount numeric(20,4) NOT NULL,
    cost_date date NOT NULL,
    description varchar(255),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_cost_collection IS '项目成本归集';
COMMENT ON COLUMN fin_cost_collection.cost_type IS '类型：LABOR/EXPENSE/OUTSOURCE/ASSET';

CREATE TABLE fin_project_settlement (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    settlement_no varchar(32) NOT NULL,
    project_id bigint NOT NULL,
    settlement_amount numeric(20,4) NOT NULL,
    settled_amount numeric(20,4) NOT NULL DEFAULT 0,
    currency_code varchar(3) NOT NULL,
    receivable_id bigint,
    voucher_id bigint,
    status varchar(16) NOT NULL,
    settled_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_project_settlement IS '项目结算';
COMMENT ON COLUMN fin_project_settlement.status IS '状态：APPROVING/SETTLED/CLOSED';
