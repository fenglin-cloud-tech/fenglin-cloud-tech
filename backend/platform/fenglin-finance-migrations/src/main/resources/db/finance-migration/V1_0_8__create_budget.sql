-- D1：预算与预算控制台账
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_budget (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    budget_no varchar(32) NOT NULL,
    budget_name varchar(128) NOT NULL,
    period_year int NOT NULL,
    dimension varchar(16) NOT NULL,
    dimension_id bigint,
    subject_code varchar(32),
    currency_code varchar(3) NOT NULL,
    budget_amount numeric(20,4) NOT NULL,
    occupied_amount numeric(20,4) NOT NULL DEFAULT 0,
    executed_amount numeric(20,4) NOT NULL DEFAULT 0,
    control_mode varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_fin_budget_positive CHECK (budget_amount >= 0),
    CONSTRAINT ck_fin_budget_not_exceed CHECK (occupied_amount + executed_amount <= budget_amount)
);
COMMENT ON TABLE fin_budget IS '预算（部门/项目/科目维度）';
COMMENT ON COLUMN fin_budget.dimension IS '维度：DEPT/PROJECT/SUBJECT';
COMMENT ON COLUMN fin_budget.control_mode IS '控制模式：STRICT（强控）/WARN（弱控）';
COMMENT ON COLUMN fin_budget.status IS '状态：DRAFT/APPROVING/RELEASED/CLOSED';

CREATE TABLE fin_budget_control (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    budget_id bigint NOT NULL,
    biz_type varchar(32) NOT NULL,
    biz_id bigint NOT NULL,
    action varchar(16) NOT NULL,
    amount numeric(20,4) NOT NULL,
    balance_after numeric(20,4) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_budget_control IS '预算控制台账';
COMMENT ON COLUMN fin_budget_control.action IS '动作：OCCUPY/RELEASE/CONSUME';
