-- 报价单与明细（01_丰麟云销_03 数据库设计 4.7 / 4.8）
SET search_path TO fenglin_crm, public;

CREATE TABLE fenglin_crm.crm_quotation (
    id                   bigint         PRIMARY KEY,
    uuid                 uuid           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            bigint         NOT NULL,
    quotation_no         varchar(32)    NOT NULL,
    opportunity_id       bigint,
    customer_id          bigint         NOT NULL,
    version_no           int            NOT NULL DEFAULT 1,
    total_amount         numeric(18, 2) NOT NULL,
    discount_rate        numeric(8, 4),
    tax_rate             numeric(8, 4)  NOT NULL DEFAULT 6.0000,
    currency             varchar(8)     NOT NULL DEFAULT 'CNY',
    valid_until          date,
    status               varchar(16)    NOT NULL DEFAULT 'DRAFT',
    approve_instance_id  varchar(64),
    owner_id             bigint,
    remark               text,
    created_by           bigint         NOT NULL,
    created_at           timestamptz    NOT NULL DEFAULT now(),
    updated_by           bigint         NOT NULL,
    updated_at           timestamptz    NOT NULL DEFAULT now(),
    deleted              smallint       NOT NULL DEFAULT 0,
    version              int            NOT NULL DEFAULT 0,
    CONSTRAINT ck_quo_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'CONVERTED'))
);

CREATE UNIQUE INDEX uk_quo_uuid ON fenglin_crm.crm_quotation (uuid);
CREATE UNIQUE INDEX uk_quo_no ON fenglin_crm.crm_quotation (tenant_id, quotation_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_quo_opp_version ON fenglin_crm.crm_quotation (tenant_id, opportunity_id, version_no)
    WHERE deleted = 0 AND opportunity_id IS NOT NULL;
CREATE INDEX idx_quo_customer ON fenglin_crm.crm_quotation (tenant_id, customer_id, status);

CREATE TRIGGER trg_crm_quotation_updated_at BEFORE UPDATE ON fenglin_crm.crm_quotation
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();

CREATE TABLE fenglin_crm.crm_quotation_item (
    id             bigint         PRIMARY KEY,
    uuid           uuid           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      bigint         NOT NULL,
    quotation_id   bigint         NOT NULL,
    product_code   varchar(64)    NOT NULL,
    product_name   varchar(200)   NOT NULL,
    unit           varchar(16),
    quantity       numeric(18, 4) NOT NULL,
    unit_price     numeric(18, 2) NOT NULL,
    discount_rate  numeric(8, 4),
    amount         numeric(18, 2) NOT NULL,
    line_no        int            NOT NULL,
    created_by     bigint         NOT NULL,
    created_at     timestamptz    NOT NULL DEFAULT now(),
    updated_by     bigint         NOT NULL,
    updated_at     timestamptz    NOT NULL DEFAULT now(),
    deleted        smallint       NOT NULL DEFAULT 0,
    version        int            NOT NULL DEFAULT 0,
    CONSTRAINT ck_quo_item_qty CHECK (quantity > 0),
    CONSTRAINT ck_quo_item_price CHECK (unit_price >= 0),
    CONSTRAINT ck_quo_item_discount CHECK (discount_rate IS NULL OR (discount_rate >= 0 AND discount_rate <= 100))
);

CREATE UNIQUE INDEX uk_quo_item_uuid ON fenglin_crm.crm_quotation_item (uuid);
CREATE INDEX idx_quo_item_quotation ON fenglin_crm.crm_quotation_item (tenant_id, quotation_id, line_no);

CREATE TRIGGER trg_crm_quotation_item_updated_at BEFORE UPDATE ON fenglin_crm.crm_quotation_item
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();
