-- 商机与阶段流转日志（01_丰麟云销_03 数据库设计 4.4 / 4.5）
SET search_path TO fenglin_crm, public;

CREATE TABLE fenglin_crm.crm_opportunity (
    id                   bigint        PRIMARY KEY,
    uuid                 uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            bigint        NOT NULL,
    opportunity_no       varchar(32)   NOT NULL,
    name                 varchar(200)  NOT NULL,
    customer_id          bigint        NOT NULL,
    stage                varchar(16)   NOT NULL DEFAULT 'S1',
    win_rate             numeric(5, 2),
    amount               numeric(18, 2) NOT NULL DEFAULT 0,
    weighted_amount      numeric(18, 2),
    currency             varchar(8)    NOT NULL DEFAULT 'CNY',
    expected_close_date  date,
    owner_id             bigint,
    dept_id              bigint,
    source               varchar(24),
    partner_deal_reg_id  bigint,
    competitor_ids       jsonb,
    lost_reason          varchar(128),
    status               varchar(16)   NOT NULL DEFAULT 'OPEN',
    next_step            varchar(255),
    created_by           bigint        NOT NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_by           bigint        NOT NULL,
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    deleted              smallint      NOT NULL DEFAULT 0,
    version              int           NOT NULL DEFAULT 0,
    CONSTRAINT ck_opp_stage CHECK (stage IN ('S1', 'S2', 'S3', 'S4', 'WON', 'LOST')),
    CONSTRAINT ck_opp_status CHECK (status IN ('OPEN', 'WON', 'LOST')),
    CONSTRAINT ck_opp_source CHECK (source IS NULL OR source IN ('DIRECT', 'PARTNER', 'LEAD'))
);

CREATE UNIQUE INDEX uk_opp_uuid ON fenglin_crm.crm_opportunity (uuid);
CREATE UNIQUE INDEX uk_opp_no ON fenglin_crm.crm_opportunity (tenant_id, opportunity_no) WHERE deleted = 0;
CREATE INDEX idx_opp_stage ON fenglin_crm.crm_opportunity (tenant_id, stage, owner_id);
CREATE INDEX idx_opp_expected ON fenglin_crm.crm_opportunity (tenant_id, expected_close_date);
CREATE INDEX idx_opp_customer ON fenglin_crm.crm_opportunity (tenant_id, customer_id, deleted);

CREATE TRIGGER trg_crm_opportunity_updated_at BEFORE UPDATE ON fenglin_crm.crm_opportunity
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();

CREATE TABLE fenglin_crm.crm_opportunity_stage_log (
    id               bigint        PRIMARY KEY,
    uuid             uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        bigint        NOT NULL,
    opportunity_id   bigint        NOT NULL,
    from_stage       varchar(16),
    to_stage         varchar(16)   NOT NULL,
    amount_snapshot  numeric(18, 2),
    changed_by       bigint        NOT NULL,
    changed_at       timestamptz   NOT NULL,
    opinion          varchar(255),
    created_by       bigint        NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_by       bigint        NOT NULL,
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    deleted          smallint      NOT NULL DEFAULT 0,
    version          int           NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_stage_log_uuid ON fenglin_crm.crm_opportunity_stage_log (uuid);
CREATE INDEX idx_stage_log_opp ON fenglin_crm.crm_opportunity_stage_log (tenant_id, opportunity_id, changed_at DESC);
