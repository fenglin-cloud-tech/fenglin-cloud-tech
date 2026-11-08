-- 销售活动/跟进（01_丰麟云销_03 数据库设计 4.6）
SET search_path TO fenglin_crm, public;

CREATE TABLE fenglin_crm.crm_activity (
    id             bigint        PRIMARY KEY,
    uuid           uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      bigint        NOT NULL,
    biz_type       varchar(24)   NOT NULL,
    biz_id         bigint        NOT NULL,
    activity_type  varchar(24)   NOT NULL,
    subject        varchar(200),
    content        text,
    duration_min   int,
    result         varchar(24),
    next_plan      varchar(255),
    occurred_at    timestamptz   NOT NULL,
    owner_id       bigint,
    dept_id        bigint,
    created_by     bigint        NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     bigint        NOT NULL,
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    deleted        smallint      NOT NULL DEFAULT 0,
    version        int           NOT NULL DEFAULT 0,
    CONSTRAINT ck_act_biz_type CHECK (biz_type IN ('LEAD', 'CUSTOMER', 'OPPORTUNITY', 'RENEWAL', 'PARTNER')),
    CONSTRAINT ck_act_type CHECK (activity_type IN ('CALL', 'EMAIL', 'VISIT', 'WECHAT', 'MEETING'))
);

CREATE UNIQUE INDEX uk_activity_uuid ON fenglin_crm.crm_activity (uuid);
CREATE INDEX idx_act_biz ON fenglin_crm.crm_activity (tenant_id, biz_type, biz_id, occurred_at DESC);
CREATE INDEX idx_act_owner ON fenglin_crm.crm_activity (tenant_id, owner_id, occurred_at DESC);

CREATE TRIGGER trg_crm_activity_updated_at BEFORE UPDATE ON fenglin_crm.crm_activity
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();
