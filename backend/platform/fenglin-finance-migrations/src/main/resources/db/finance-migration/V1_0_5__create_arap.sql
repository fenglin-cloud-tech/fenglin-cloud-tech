-- D1：应收、应付、收款单、付款单、往来核销
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_receivable (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    ar_no varchar(32) NOT NULL,
    customer_id bigint,
    customer_name varchar(128),
    source_type varchar(32) NOT NULL,
    source_biz_id bigint,
    project_id bigint,
    contract_no varchar(64),
    currency_code varchar(3) NOT NULL,
    amount numeric(20,4) NOT NULL,
    settled_amount numeric(20,4) NOT NULL DEFAULT 0,
    bad_debt_provision numeric(20,4) NOT NULL DEFAULT 0,
    bill_date date NOT NULL,
    due_date date NOT NULL,
    aging_bucket varchar(16),
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_receivable IS '应收账款';
COMMENT ON COLUMN fin_receivable.source_type IS '来源：SALES/PROJECT_SETTLE/INVOICE';
COMMENT ON COLUMN fin_receivable.aging_bucket IS '账龄段：LE_30/B31_60/B61_90/B91_180/GT_180';
COMMENT ON COLUMN fin_receivable.status IS '状态：OPEN/PARTIAL/SETTLED/CLOSED';

CREATE TABLE fin_payable (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    ap_no varchar(32) NOT NULL,
    supplier_id bigint,
    supplier_name varchar(128),
    source_type varchar(32) NOT NULL,
    source_biz_id bigint,
    project_id bigint,
    currency_code varchar(3) NOT NULL,
    amount numeric(20,4) NOT NULL,
    settled_amount numeric(20,4) NOT NULL DEFAULT 0,
    bill_date date NOT NULL,
    due_date date NOT NULL,
    planned_pay_date date,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_payable IS '应付账款';
COMMENT ON COLUMN fin_payable.source_type IS '来源：PURCHASE/OUTSOURCE/EXPENSE';
COMMENT ON COLUMN fin_payable.status IS '状态：OPEN/PARTIAL/SETTLED/CLOSED';

CREATE TABLE fin_receipt (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    receipt_no varchar(32) NOT NULL,
    customer_id bigint,
    currency_code varchar(3) NOT NULL,
    amount numeric(20,4) NOT NULL,
    pay_method varchar(16) NOT NULL,
    bank_account_id bigint,
    receipt_date date NOT NULL,
    voucher_id bigint,
    status varchar(16) NOT NULL,
    idempotency_key varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_receipt IS '收款单';
COMMENT ON COLUMN fin_receipt.status IS '状态：DRAFT/CONFIRMED';

CREATE TABLE fin_payment (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    payment_no varchar(32) NOT NULL,
    supplier_id bigint,
    currency_code varchar(3) NOT NULL,
    amount numeric(20,4) NOT NULL,
    pay_method varchar(16) NOT NULL,
    bank_account_id bigint,
    payment_date date NOT NULL,
    voucher_id bigint,
    status varchar(16) NOT NULL,
    idempotency_key varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_payment IS '付款单';
COMMENT ON COLUMN fin_payment.status IS '状态：DRAFT/CONFIRMED';

CREATE TABLE fin_arap_write_off (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    write_off_no varchar(32) NOT NULL,
    biz_type varchar(16) NOT NULL,
    source_id bigint NOT NULL,
    settle_doc_type varchar(16) NOT NULL,
    settle_doc_id bigint NOT NULL,
    currency_code varchar(3) NOT NULL,
    amount numeric(20,4) NOT NULL,
    write_off_date date NOT NULL,
    operator_id bigint NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_arap_write_off IS '往来核销';
COMMENT ON COLUMN fin_arap_write_off.biz_type IS '类型：AR/AP';
COMMENT ON COLUMN fin_arap_write_off.settle_doc_type IS '结算单据：RECEIPT/PAYMENT';
