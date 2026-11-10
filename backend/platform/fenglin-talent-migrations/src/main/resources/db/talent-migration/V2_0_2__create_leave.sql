-- 云才假期：假期类型、年度额度、请假申请（审批联动工作流）
-- 幂等约定：申请单携带 idempotency_key，数据库部分唯一索引兜底重复提交；
--           额度扣减在申请通过时执行，带 version 乐观锁。
SET search_path TO fenglin_talent, public;

CREATE SEQUENCE IF NOT EXISTS talent_leave_no_seq AS bigint START WITH 1 INCREMENT BY 1;
GRANT USAGE, SELECT ON SEQUENCE talent_leave_no_seq TO fl_talent_app;

CREATE TABLE talent_leave_type (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    type_code varchar(32) NOT NULL,
    type_name varchar(64) NOT NULL,
    annual_quota_days numeric(5, 1) NOT NULL DEFAULT 0,
    paid smallint NOT NULL DEFAULT 1,
    sort_no int NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_leave_type IS '假期类型（按租户配置年度额度与是否带薪）';
COMMENT ON COLUMN talent_leave_type.annual_quota_days IS '年度基础额度（天）';
COMMENT ON COLUMN talent_leave_type.paid IS '是否带薪：1 带薪 / 0 不带薪';

CREATE UNIQUE INDEX uk_leave_type_code ON talent_leave_type (tenant_id, type_code) WHERE deleted = 0;

CREATE TABLE talent_leave_balance (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    leave_type_id bigint NOT NULL,
    balance_year int NOT NULL,
    total_days numeric(6, 1) NOT NULL DEFAULT 0,
    used_days numeric(6, 1) NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_leave_balance IS '员工年度假期额度（首次查询时按类型额度懒初始化）';
COMMENT ON COLUMN talent_leave_balance.total_days IS '年度总额度（天）';
COMMENT ON COLUMN talent_leave_balance.used_days IS '已使用（审批通过后累加，撤销时回滚）';

CREATE UNIQUE INDEX uk_leave_balance ON talent_leave_balance (tenant_id, employee_id, leave_type_id, balance_year)
    WHERE deleted = 0;
CREATE INDEX idx_leave_balance_emp ON talent_leave_balance (tenant_id, employee_id, balance_year);

CREATE TABLE talent_leave_request (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    request_no varchar(32) NOT NULL,
    employee_id bigint NOT NULL,
    leave_type_id bigint NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    days numeric(5, 1) NOT NULL,
    reason varchar(500),
    lr_status varchar(16) NOT NULL DEFAULT 'APPROVING',
    process_instance_id varchar(64),
    idempotency_key varchar(64),
    approved_by bigint,
    approved_at timestamptz,
    approve_opinion varchar(500),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_leave_request IS '请假申请（提交即发起工作流审批）';
COMMENT ON COLUMN talent_leave_request.lr_status IS '申请状态：APPROVING/APPROVED/REJECTED/CANCELED';
COMMENT ON COLUMN talent_leave_request.days IS '请假天数（由起止日期计算或前端传入后校验）';
COMMENT ON COLUMN talent_leave_request.idempotency_key IS '客户端幂等键，重复提交返回同一申请单';

CREATE UNIQUE INDEX uk_leave_req_no ON talent_leave_request (tenant_id, request_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_leave_idempotency ON talent_leave_request (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted = 0;
CREATE INDEX idx_leave_req_emp ON talent_leave_request (tenant_id, employee_id, created_at);
CREATE INDEX idx_leave_req_status ON talent_leave_request (tenant_id, lr_status);

ALTER TABLE talent_leave_type ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_leave_type FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_leave_type_tenant ON talent_leave_type;
CREATE POLICY p_talent_leave_type_tenant ON talent_leave_type
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE talent_leave_balance ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_leave_balance FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_leave_balance_tenant ON talent_leave_balance;
CREATE POLICY p_talent_leave_balance_tenant ON talent_leave_balance
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE talent_leave_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_leave_request FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_leave_request_tenant ON talent_leave_request;
CREATE POLICY p_talent_leave_request_tenant ON talent_leave_request
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

DROP TRIGGER IF EXISTS trg_talent_leave_type_updated ON talent_leave_type;
CREATE TRIGGER trg_talent_leave_type_updated BEFORE UPDATE ON talent_leave_type
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_talent_leave_balance_updated ON talent_leave_balance;
CREATE TRIGGER trg_talent_leave_balance_updated BEFORE UPDATE ON talent_leave_balance
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_talent_leave_request_updated ON talent_leave_request;
CREATE TRIGGER trg_talent_leave_request_updated BEFORE UPDATE ON talent_leave_request
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON talent_leave_type, talent_leave_balance, talent_leave_request
    TO fl_talent_app;
