-- 合同变更 / 续签（扩展：01_丰麟云销_03 4.14 仅定义主表，未定义变更单与续签关系）
SET search_path TO fenglin_crm, public;

CREATE SEQUENCE IF NOT EXISTS fenglin_crm.crm_change_no_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE fenglin_crm.crm_service_contract
    ADD COLUMN IF NOT EXISTS parent_contract_id bigint,
    ADD COLUMN IF NOT EXISTS quotation_id bigint;

CREATE INDEX IF NOT EXISTS idx_contract_parent ON fenglin_crm.crm_service_contract (tenant_id, parent_contract_id);
CREATE INDEX IF NOT EXISTS idx_contract_quotation ON fenglin_crm.crm_service_contract (tenant_id, quotation_id);

CREATE TABLE fenglin_crm.crm_contract_change (
    id                 bigint         PRIMARY KEY,
    uuid               uuid           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          bigint         NOT NULL,
    contract_id        bigint         NOT NULL,
    change_no          varchar(32)    NOT NULL,
    change_type        varchar(16)    NOT NULL,
    before_amount      numeric(18, 2),
    after_amount       numeric(18, 2),
    before_start_date  date,
    before_end_date    date,
    after_start_date   date,
    after_end_date     date,
    reason             varchar(256),
    new_contract_id    bigint,
    status             varchar(16)    NOT NULL DEFAULT 'APPLIED',
    created_by         bigint         NOT NULL,
    created_at         timestamptz    NOT NULL DEFAULT now(),
    updated_by         bigint         NOT NULL,
    updated_at         timestamptz    NOT NULL DEFAULT now(),
    deleted            smallint       NOT NULL DEFAULT 0,
    version            int            NOT NULL DEFAULT 0,
    CONSTRAINT ck_contract_change_type CHECK (change_type IN ('AMEND', 'RENEW', 'TERMINATE')),
    CONSTRAINT ck_contract_change_status CHECK (status IN ('APPLIED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uk_contract_change_uuid ON fenglin_crm.crm_contract_change (uuid);
CREATE UNIQUE INDEX uk_contract_change_no ON fenglin_crm.crm_contract_change (tenant_id, change_no) WHERE deleted = 0;
CREATE INDEX idx_contract_change_contract ON fenglin_crm.crm_contract_change (tenant_id, contract_id, created_at);

CREATE TRIGGER trg_crm_contract_change_updated_at BEFORE UPDATE ON fenglin_crm.crm_contract_change
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();

ALTER TABLE fenglin_crm.crm_contract_change ENABLE ROW LEVEL SECURITY;
ALTER TABLE fenglin_crm.crm_contract_change FORCE ROW LEVEL SECURITY;
CREATE POLICY p_crm_contract_change_tenant ON fenglin_crm.crm_contract_change
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_crm.crm_contract_change TO fl_crm_app;
GRANT USAGE, SELECT ON SEQUENCE fenglin_crm.crm_change_no_seq TO fl_crm_app;

COMMENT ON TABLE fenglin_crm.crm_contract_change IS '扩展：合同变更/续签记录（总纲未定义）';
COMMENT ON COLUMN fenglin_crm.crm_service_contract.parent_contract_id IS '扩展：续签来源合同';
COMMENT ON COLUMN fenglin_crm.crm_service_contract.quotation_id IS '扩展：由报价转合同时回填';
