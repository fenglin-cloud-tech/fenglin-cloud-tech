-- D1：账套、会计科目、会计期间
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_ledger (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    ledger_code varchar(32) NOT NULL,
    ledger_name varchar(128) NOT NULL,
    company_name varchar(255) NOT NULL,
    base_currency varchar(3) NOT NULL,
    fiscal_year_start_month smallint NOT NULL,
    accounting_standard varchar(32) NOT NULL,
    tax_no_enc varchar(512),
    tax_no_hash varchar(64),
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_ledger IS '账套（法人主体）';
COMMENT ON COLUMN fin_ledger.accounting_standard IS '会计准则：CAS/SMALL_BUSINESS';
COMMENT ON COLUMN fin_ledger.status IS '状态：ENABLED/DISABLED';
COMMENT ON COLUMN fin_ledger.tax_no_enc IS '纳税人识别号 AES-256-GCM 密文（PII）';
COMMENT ON COLUMN fin_ledger.tax_no_hash IS '纳税人识别号 SHA-256+租户盐哈希，用于检索';

CREATE TABLE fin_accounting_subject (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    ledger_id bigint NOT NULL,
    subject_code varchar(32) NOT NULL,
    subject_name varchar(128) NOT NULL,
    parent_id bigint,
    subject_level smallint NOT NULL,
    subject_category varchar(16) NOT NULL,
    balance_direction varchar(8) NOT NULL,
    is_leaf boolean NOT NULL,
    auxiliary_types varchar(255),
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_accounting_subject IS '会计科目（≤10 级）';
COMMENT ON COLUMN fin_accounting_subject.subject_category IS '科目类别：ASSET/LIABILITY/EQUITY/COST/PROFIT_LOSS';
COMMENT ON COLUMN fin_accounting_subject.balance_direction IS '余额方向：DEBIT/CREDIT';
COMMENT ON COLUMN fin_accounting_subject.auxiliary_types IS '辅助核算，逗号分隔：dept,project,customer,supplier,employee,asset,cashflow';
COMMENT ON COLUMN fin_accounting_subject.status IS '状态：ENABLED/DISABLED';

CREATE TABLE fin_accounting_period (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    ledger_id bigint NOT NULL,
    period_year int NOT NULL,
    period_month smallint NOT NULL,
    period_code varchar(7) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status varchar(16) NOT NULL,
    closed_by bigint,
    closed_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_accounting_period IS '会计期间';
COMMENT ON COLUMN fin_accounting_period.status IS '状态：NOT_OPEN/OPEN/CLOSED/SEALED';
