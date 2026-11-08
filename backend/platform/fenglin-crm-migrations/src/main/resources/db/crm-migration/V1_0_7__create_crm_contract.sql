-- 服务合同与回款计划（01_丰麟云销_03 数据库设计 4.14；回款计划为总纲缺表，按最小可用语义扩展）
SET search_path TO fenglin_crm, public;

CREATE TABLE fenglin_crm.crm_service_contract (
    id               bigint         PRIMARY KEY,
    uuid             uuid           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        bigint         NOT NULL,
    contract_no      varchar(32)    NOT NULL,
    customer_id      bigint         NOT NULL,
    sla_level        varchar(16),
    service_package  varchar(64),
    amount           numeric(18, 2),
    start_date       date           NOT NULL,
    end_date         date           NOT NULL,
    status           varchar(16)    NOT NULL DEFAULT 'ACTIVE',
    owner_id         bigint,
    created_by       bigint         NOT NULL,
    created_at       timestamptz    NOT NULL DEFAULT now(),
    updated_by       bigint         NOT NULL,
    updated_at       timestamptz    NOT NULL DEFAULT now(),
    deleted          smallint       NOT NULL DEFAULT 0,
    version          int            NOT NULL DEFAULT 0,
    CONSTRAINT ck_contract_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'TERMINATED')),
    CONSTRAINT ck_contract_sla CHECK (sla_level IS NULL OR sla_level IN ('P1', 'P2', 'P3')),
    CONSTRAINT ck_contract_period CHECK (end_date >= start_date)
);

CREATE UNIQUE INDEX uk_contract_uuid ON fenglin_crm.crm_service_contract (uuid);
CREATE UNIQUE INDEX uk_contract_no ON fenglin_crm.crm_service_contract (tenant_id, contract_no) WHERE deleted = 0;
CREATE INDEX idx_contract_customer ON fenglin_crm.crm_service_contract (tenant_id, customer_id, status);
CREATE INDEX idx_contract_end ON fenglin_crm.crm_service_contract (tenant_id, end_date, status);

CREATE TRIGGER trg_crm_service_contract_updated_at BEFORE UPDATE ON fenglin_crm.crm_service_contract
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();

-- 扩展：总纲 32 张表中未定义回款计划表；本表仅覆盖"合同创建 + 回款计划"最小闭环所需字段
CREATE TABLE fenglin_crm.crm_payment_plan (
    id           bigint         PRIMARY KEY,
    uuid         uuid           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    bigint         NOT NULL,
    contract_id  bigint         NOT NULL,
    period_no    int            NOT NULL,
    due_date     date           NOT NULL,
    amount       numeric(18, 2) NOT NULL,
    paid_amount  numeric(18, 2) NOT NULL DEFAULT 0,
    status       varchar(16)    NOT NULL DEFAULT 'PENDING',
    created_by   bigint         NOT NULL,
    created_at   timestamptz    NOT NULL DEFAULT now(),
    updated_by   bigint         NOT NULL,
    updated_at   timestamptz    NOT NULL DEFAULT now(),
    deleted      smallint       NOT NULL DEFAULT 0,
    version      int            NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_plan_status CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'OVERDUE')),
    CONSTRAINT ck_payment_plan_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_plan_paid CHECK (paid_amount >= 0)
);

CREATE UNIQUE INDEX uk_payment_plan_uuid ON fenglin_crm.crm_payment_plan (uuid);
CREATE UNIQUE INDEX uk_payment_plan_period ON fenglin_crm.crm_payment_plan (tenant_id, contract_id, period_no)
    WHERE deleted = 0;
CREATE INDEX idx_payment_plan_due ON fenglin_crm.crm_payment_plan (tenant_id, due_date, status);

CREATE TRIGGER trg_crm_payment_plan_updated_at BEFORE UPDATE ON fenglin_crm.crm_payment_plan
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();
