-- D1：发票
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_invoice (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    invoice_code varchar(32),
    invoice_number varchar(32) NOT NULL,
    invoice_type varchar(16) NOT NULL,
    buyer_name varchar(128),
    seller_name varchar(128),
    amount numeric(20,4) NOT NULL,
    tax_rate numeric(9,6) NOT NULL,
    tax_amount numeric(20,4) NOT NULL,
    total_amount numeric(20,4) NOT NULL,
    invoice_date date NOT NULL,
    verify_status varchar(16),
    status varchar(16) NOT NULL,
    source_biz_id bigint,
    file_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_invoice IS '发票（进项/销项）';
COMMENT ON COLUMN fin_invoice.invoice_type IS '类型：INPUT（进项）/OUTPUT（销项）';
COMMENT ON COLUMN fin_invoice.verify_status IS '验真：UNVERIFIED/VERIFIED/FAILED';
COMMENT ON COLUMN fin_invoice.status IS '状态：PENDING/ISSUED/VERIFIED/RED_FLUSHED';
