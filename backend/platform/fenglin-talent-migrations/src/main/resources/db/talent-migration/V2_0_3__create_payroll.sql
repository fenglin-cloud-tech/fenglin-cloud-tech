-- 云才薪酬：薪资项、薪资周期、核算明细、发放单
-- 设计约定：
--   1) 薪资项按租户维护，核算规则 FIXED（固定金额）/PERCENT（按基本薪资比例）；
--   2) 一个周期一人一条核算明细，唯一索引兜底重复核算；
--   3) 发放单按周期唯一，提交即发起工作流审批，审批通过后可发放；
--   4) RLS 与审计列沿用员工表范式。
SET search_path TO fenglin_talent, public;

ALTER TABLE talent_employee ADD COLUMN IF NOT EXISTS base_salary numeric(18, 2);
COMMENT ON COLUMN talent_employee.base_salary IS '月度基本薪资（薪酬核算依据）';

CREATE SEQUENCE IF NOT EXISTS talent_payslip_no_seq AS bigint START WITH 1 INCREMENT BY 1;
GRANT USAGE, SELECT ON SEQUENCE talent_payslip_no_seq TO fl_talent_app;

CREATE TABLE talent_pay_item (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    item_code varchar(32) NOT NULL,
    item_name varchar(64) NOT NULL,
    item_type varchar(16) NOT NULL,
    calc_type varchar(16) NOT NULL DEFAULT 'FIXED',
    amount numeric(18, 2) NOT NULL DEFAULT 0,
    taxable smallint NOT NULL DEFAULT 1,
    sort_no int NOT NULL DEFAULT 0,
    item_status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    remark varchar(255),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_pay_item IS '薪资项（收入/扣减项，按租户配置核算规则）';
COMMENT ON COLUMN talent_pay_item.item_type IS '薪资项类型：EARNING 收入 / DEDUCTION 扣减';
COMMENT ON COLUMN talent_pay_item.calc_type IS '核算方式：FIXED 固定金额 / PERCENT 基本薪资比例';
COMMENT ON COLUMN talent_pay_item.amount IS 'FIXED 为金额，PERCENT 为比例（0.1000 表示 10%）';
COMMENT ON COLUMN talent_pay_item.taxable IS '是否计税：1 是 / 0 否';

CREATE UNIQUE INDEX uk_pay_item_code ON talent_pay_item (tenant_id, item_code) WHERE deleted = 0;

CREATE TABLE talent_pay_period (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    period_code varchar(16) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    pay_date date,
    period_status varchar(16) NOT NULL DEFAULT 'DRAFT',
    employee_count int NOT NULL DEFAULT 0,
    gross_total numeric(18, 2) NOT NULL DEFAULT 0,
    net_total numeric(18, 2) NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_pay_period IS '薪资周期（如 2026-11）';
COMMENT ON COLUMN talent_pay_period.period_status IS
    '周期状态：DRAFT 草稿 / CALCULATED 已核算 / APPROVING 发放审批中 / APPROVED 审批通过 / PAID 已发放 / CLOSED 已关闭';
COMMENT ON COLUMN talent_pay_period.employee_count IS '本期核算人数';
COMMENT ON COLUMN talent_pay_period.gross_total IS '应发合计';
COMMENT ON COLUMN talent_pay_period.net_total IS '实发合计';

CREATE UNIQUE INDEX uk_pay_period_code ON talent_pay_period (tenant_id, period_code) WHERE deleted = 0;
CREATE INDEX idx_pay_period_status ON talent_pay_period (tenant_id, period_status);

CREATE TABLE talent_payroll_record (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    period_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    base_salary numeric(18, 2) NOT NULL DEFAULT 0,
    earning_total numeric(18, 2) NOT NULL DEFAULT 0,
    deduction_total numeric(18, 2) NOT NULL DEFAULT 0,
    gross_pay numeric(18, 2) NOT NULL DEFAULT 0,
    net_pay numeric(18, 2) NOT NULL DEFAULT 0,
    calc_detail jsonb,
    rec_status varchar(16) NOT NULL DEFAULT 'CALCULATED',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_payroll_record IS '薪酬核算明细（一个周期一人一条）';
COMMENT ON COLUMN talent_payroll_record.calc_detail IS '核算明细 JSON：{base, items:[{code,name,type,amount}]}';
COMMENT ON COLUMN talent_payroll_record.rec_status IS '明细状态：CALCULATED 已核算 / ADJUSTED 已调整';

CREATE UNIQUE INDEX uk_payroll_period_emp ON talent_payroll_record (tenant_id, period_id, employee_id)
    WHERE deleted = 0;
CREATE INDEX idx_payroll_emp ON talent_payroll_record (tenant_id, employee_id);

CREATE TABLE talent_payslip (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    period_id bigint NOT NULL,
    slip_no varchar(32) NOT NULL,
    employee_count int NOT NULL DEFAULT 0,
    gross_total numeric(18, 2) NOT NULL DEFAULT 0,
    net_total numeric(18, 2) NOT NULL DEFAULT 0,
    slip_status varchar(16) NOT NULL DEFAULT 'APPROVING',
    process_instance_id varchar(64),
    approved_by bigint,
    approved_at timestamptz,
    approve_opinion varchar(500),
    paid_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_payslip IS '薪资发放单（按周期唯一，提交即发起工作流审批）';
COMMENT ON COLUMN talent_payslip.slip_status IS '发放单状态：APPROVING 审批中 / APPROVED 已通过 / PAID 已发放 / VOID 已作废';

CREATE UNIQUE INDEX uk_payslip_no ON talent_payslip (tenant_id, slip_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_payslip_period ON talent_payslip (tenant_id, period_id) WHERE deleted = 0;

ALTER TABLE talent_pay_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_pay_item FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_pay_item_tenant ON talent_pay_item;
CREATE POLICY p_talent_pay_item_tenant ON talent_pay_item
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE talent_pay_period ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_pay_period FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_pay_period_tenant ON talent_pay_period;
CREATE POLICY p_talent_pay_period_tenant ON talent_pay_period
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE talent_payroll_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_payroll_record FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_payroll_record_tenant ON talent_payroll_record;
CREATE POLICY p_talent_payroll_record_tenant ON talent_payroll_record
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE talent_payslip ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_payslip FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_payslip_tenant ON talent_payslip;
CREATE POLICY p_talent_payslip_tenant ON talent_payslip
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

DROP TRIGGER IF EXISTS trg_talent_pay_item_updated ON talent_pay_item;
CREATE TRIGGER trg_talent_pay_item_updated BEFORE UPDATE ON talent_pay_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_talent_pay_period_updated ON talent_pay_period;
CREATE TRIGGER trg_talent_pay_period_updated BEFORE UPDATE ON talent_pay_period
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_talent_payroll_record_updated ON talent_payroll_record;
CREATE TRIGGER trg_talent_payroll_record_updated BEFORE UPDATE ON talent_payroll_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_talent_payslip_updated ON talent_payslip;
CREATE TRIGGER trg_talent_payslip_updated BEFORE UPDATE ON talent_payslip
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON talent_pay_item, talent_pay_period, talent_payroll_record, talent_payslip
    TO fl_talent_app;
