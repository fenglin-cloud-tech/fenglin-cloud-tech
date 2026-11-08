-- D1：凭证模板、凭证主表、分录、凭证审计链
-- 偏差标注：字段字典未列出 fin_voucher.ledger_id 与 fin_voucher_entry.period_code，
--           但第 5 节索引 uk_voucher_no(idx) 与 idx_entry_subject 均引用该列，按索引定义补齐。
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_voucher_template (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    template_code varchar(32) NOT NULL,
    template_name varchar(128) NOT NULL,
    biz_type varchar(32) NOT NULL,
    debit_subject_code varchar(32) NOT NULL,
    credit_subject_code varchar(32) NOT NULL,
    auxiliary_mapping jsonb,
    condition_expr varchar(512),
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_voucher_template IS '凭证模板（业财一体规则）';
COMMENT ON COLUMN fin_voucher_template.status IS '状态：ENABLED/DISABLED';

CREATE TABLE fin_voucher (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    ledger_id bigint NOT NULL,
    voucher_word varchar(8) NOT NULL,
    voucher_no varchar(32) NOT NULL,
    period_code varchar(7) NOT NULL,
    voucher_date date NOT NULL,
    summary varchar(255) NOT NULL,
    currency_code varchar(3) NOT NULL,
    total_debit numeric(20,4) NOT NULL,
    total_credit numeric(20,4) NOT NULL,
    attachment_count int NOT NULL DEFAULT 0,
    source_type varchar(32) NOT NULL,
    source_biz_id bigint,
    status varchar(16) NOT NULL,
    audited_by bigint,
    audited_at timestamptz,
    posted_by bigint,
    posted_at timestamptz,
    reverse_of bigint,
    reversed_by bigint,
    chain_hash varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_voucher IS '会计凭证主表（过账后写哈希链，禁止修改）';
COMMENT ON COLUMN fin_voucher.source_type IS '来源：MANUAL/BIZ_EVENT';
COMMENT ON COLUMN fin_voucher.status IS '状态：DRAFT/SUBMITTED/AUDITED/POSTED/REVERSED';
COMMENT ON COLUMN fin_voucher.chain_hash IS '过账时记录摘要（SHA-256 哈希链节点）';

CREATE TABLE fin_voucher_entry (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    voucher_id bigint NOT NULL,
    period_code varchar(7) NOT NULL,
    line_no int NOT NULL,
    summary varchar(255) NOT NULL,
    subject_code varchar(32) NOT NULL,
    subject_name varchar(128) NOT NULL,
    direction varchar(8) NOT NULL,
    amount numeric(20,4) NOT NULL,
    currency_code varchar(3) NOT NULL,
    exchange_rate numeric(18,8) NOT NULL,
    amount_base numeric(20,4) NOT NULL,
    auxiliary jsonb,
    quantity numeric(18,4),
    price numeric(20,4),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_voucher_entry IS '凭证分录';
COMMENT ON COLUMN fin_voucher_entry.direction IS '方向：DEBIT/CREDIT';

CREATE TABLE fin_voucher_audit_log (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    voucher_id bigint NOT NULL,
    chain_seq bigint NOT NULL,
    action varchar(16) NOT NULL,
    operator_id bigint NOT NULL,
    operator_name varchar(64),
    occurred_at timestamptz NOT NULL,
    record_hash varchar(64) NOT NULL,
    prev_hash varchar(64) NOT NULL,
    signature varchar(128),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_voucher_audit_log IS '凭证审计链（prev_hash + record_hash 防篡改）';
COMMENT ON COLUMN fin_voucher_audit_log.action IS '动作：CREATE/SUBMIT/AUDIT/POST/REVERSE';
