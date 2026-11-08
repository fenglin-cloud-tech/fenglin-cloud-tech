-- 回款登记与核销（扩展：总纲 32 表与接口文档均未定义回款明细，按最小可用语义实现）
-- 回款计划补充催收标记字段；回款记录用于登记与核销，计划余额由 paid_amount 维护。
SET search_path TO fenglin_crm, public;

ALTER TABLE fenglin_crm.crm_payment_plan
    ADD COLUMN IF NOT EXISTS collection_flag varchar(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS collection_note varchar(256),
    ADD COLUMN IF NOT EXISTS collection_updated_at timestamptz;

ALTER TABLE fenglin_crm.crm_payment_plan
    DROP CONSTRAINT IF EXISTS ck_payment_plan_collection;
ALTER TABLE fenglin_crm.crm_payment_plan
    ADD CONSTRAINT ck_payment_plan_collection
        CHECK (collection_flag IN ('NONE', 'REMINDED', 'PROMISED', 'ESCALATED'));

COMMENT ON COLUMN fenglin_crm.crm_payment_plan.collection_flag IS '催收标记：NONE/REMINDED/PROMISED/ESCALATED';

CREATE TABLE fenglin_crm.crm_payment_record (
    id            bigint         PRIMARY KEY,
    uuid          uuid           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     bigint         NOT NULL,
    contract_id   bigint         NOT NULL,
    plan_id       bigint         NOT NULL,
    amount        numeric(18, 2) NOT NULL,
    received_date date           NOT NULL,
    method        varchar(16)    NOT NULL DEFAULT 'BANK',
    voucher_no    varchar(64),
    remark        varchar(256),
    status        varchar(16)    NOT NULL DEFAULT 'RECORDED',
    created_by    bigint         NOT NULL,
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_by    bigint         NOT NULL,
    updated_at    timestamptz    NOT NULL DEFAULT now(),
    deleted       smallint       NOT NULL DEFAULT 0,
    version       int            NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_record_status CHECK (status IN ('RECORDED', 'VOID')),
    CONSTRAINT ck_payment_record_method CHECK (method IN ('BANK', 'TRANSFER', 'ONLINE', 'CASH', 'OTHER')),
    CONSTRAINT ck_payment_record_amount CHECK (amount > 0)
);

CREATE UNIQUE INDEX uk_payment_record_uuid ON fenglin_crm.crm_payment_record (uuid);
CREATE INDEX idx_payment_record_contract ON fenglin_crm.crm_payment_record (tenant_id, contract_id, received_date);
CREATE INDEX idx_payment_record_plan ON fenglin_crm.crm_payment_record (tenant_id, plan_id);

CREATE TRIGGER trg_crm_payment_record_updated_at BEFORE UPDATE ON fenglin_crm.crm_payment_record
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();

ALTER TABLE fenglin_crm.crm_payment_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE fenglin_crm.crm_payment_record FORCE ROW LEVEL SECURITY;
CREATE POLICY p_crm_payment_record_tenant ON fenglin_crm.crm_payment_record
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_crm.crm_payment_record TO fl_crm_app;

COMMENT ON TABLE fenglin_crm.crm_payment_record IS '扩展：回款登记/核销记录（总纲未定义）';
