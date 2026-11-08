-- D1：银行账户、银行流水、银企对账
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_bank_account (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    account_no_enc varchar(512) NOT NULL,
    account_no_hash varchar(64) NOT NULL,
    account_name varchar(128) NOT NULL,
    bank_name varchar(128) NOT NULL,
    bank_code varchar(32),
    currency_code varchar(3) NOT NULL,
    account_type varchar(16) NOT NULL,
    subject_code varchar(32) NOT NULL,
    balance numeric(20,4) NOT NULL,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_bank_account IS '银行账户（账号 AES-256-GCM 密文 + 租户盐哈希）';
COMMENT ON COLUMN fin_bank_account.account_type IS '账户类型：BASIC/GENERAL/SPECIAL';

CREATE TABLE fin_bank_transaction (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    account_id bigint NOT NULL,
    txn_no varchar(64) NOT NULL,
    txn_date date NOT NULL,
    direction varchar(8) NOT NULL,
    amount numeric(20,4) NOT NULL,
    balance numeric(20,4),
    counterparty varchar(128),
    summary varchar(255),
    reconcile_status varchar(16) NOT NULL,
    recon_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_bank_transaction IS '银行流水（txn_no + account_id 去重）';
COMMENT ON COLUMN fin_bank_transaction.direction IS '方向：IN/OUT';
COMMENT ON COLUMN fin_bank_transaction.reconcile_status IS '对账状态：UNMATCHED/MATCHED';

CREATE TABLE fin_reconciliation (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    recon_no varchar(32) NOT NULL,
    account_id bigint NOT NULL,
    period_code varchar(7) NOT NULL,
    book_balance numeric(20,4) NOT NULL,
    bank_balance numeric(20,4) NOT NULL,
    unsettled_in numeric(20,4) NOT NULL DEFAULT 0,
    unsettled_out numeric(20,4) NOT NULL DEFAULT 0,
    adjusted_balance numeric(20,4) NOT NULL,
    status varchar(16) NOT NULL,
    operator_id bigint NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_reconciliation IS '银企对账（余额调节表）';
COMMENT ON COLUMN fin_reconciliation.status IS '状态：DRAFT/BALANCED';
