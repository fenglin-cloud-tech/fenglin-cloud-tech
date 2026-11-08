-- D1：费用报销单与明细
-- 偏差标注：字段字典未列出 claim_date；预算占用需要按费用发生年度匹配预算，
--           故补充 claim_date（默认创建日），并在接口文档 I-25 请求体中取其 item 最早费用日期。
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_expense_claim (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    claim_no varchar(32) NOT NULL,
    claim_date date NOT NULL,
    applicant_id bigint NOT NULL,
    applicant_name varchar(64),
    dept_id bigint,
    claim_type varchar(16) NOT NULL,
    currency_code varchar(3) NOT NULL,
    total_amount numeric(20,4) NOT NULL,
    tax_amount numeric(20,4) NOT NULL DEFAULT 0,
    budget_id bigint,
    budget_occupied numeric(20,4) NOT NULL DEFAULT 0,
    project_id bigint,
    status varchar(16) NOT NULL,
    current_approver_id bigint,
    payable_id bigint,
    voucher_id bigint,
    idempotency_key varchar(64),
    submitted_at timestamptz,
    paid_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_expense_claim IS '费用报销单';
COMMENT ON COLUMN fin_expense_claim.claim_type IS '类型：TRAVEL/OFFICE/ENTERTAIN/MATERIAL/OTHER';
COMMENT ON COLUMN fin_expense_claim.status IS '状态：DRAFT/SUBMITTED/APPROVING/APPROVED/REJECTED/PAID';
COMMENT ON COLUMN fin_expense_claim.budget_occupied IS '预算占用额（STRICT 强控）';

CREATE TABLE fin_expense_claim_item (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    claim_id bigint NOT NULL,
    line_no int NOT NULL,
    expense_date date NOT NULL,
    expense_category varchar(32) NOT NULL,
    subject_code varchar(32) NOT NULL,
    amount numeric(20,4) NOT NULL,
    tax_amount numeric(20,4) NOT NULL DEFAULT 0,
    invoice_id bigint,
    attachment_id bigint,
    remark varchar(255),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_expense_claim_item IS '报销明细';
