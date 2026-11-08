-- 线索表（01_丰麟云销_03 数据库设计 4.1）
SET search_path TO fenglin_crm, public;

CREATE TABLE fenglin_crm.crm_lead (
    id                    bigint       PRIMARY KEY,
    uuid                  uuid         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             bigint       NOT NULL,
    lead_no               varchar(32)  NOT NULL,
    name                  varchar(128) NOT NULL,
    contact_name          varchar(64),
    mobile_enc            text,
    mobile_hash           varchar(64),
    mobile_tail           varchar(4),
    email                 varchar(128),
    industry              varchar(64),
    region_code           varchar(16),
    source                varchar(32)  NOT NULL,
    channel_partner_id    bigint,
    score                 int          NOT NULL DEFAULT 0,
    grade                 varchar(4),
    status                varchar(24)  NOT NULL DEFAULT 'NEW',
    owner_id              bigint,
    dept_id               bigint,
    pool_id               bigint,
    last_activity_at      timestamptz,
    converted_customer_id bigint,
    remark                text,
    created_by            bigint       NOT NULL,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_by            bigint       NOT NULL,
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    deleted               smallint     NOT NULL DEFAULT 0,
    version               int          NOT NULL DEFAULT 0,
    CONSTRAINT ck_lead_status CHECK (status IN ('NEW', 'FOLLOWING', 'CONVERTED', 'INVALID', 'NURTURING')),
    CONSTRAINT ck_lead_source CHECK (source IN ('MANUAL', 'IMPORT', 'FORM', 'API', 'PARTNER', 'OPINION')),
    CONSTRAINT ck_lead_grade CHECK (grade IS NULL OR grade IN ('A', 'B', 'C', 'D'))
);

CREATE UNIQUE INDEX uk_lead_uuid ON fenglin_crm.crm_lead (uuid);
CREATE UNIQUE INDEX uk_lead_no ON fenglin_crm.crm_lead (tenant_id, lead_no) WHERE deleted = 0;
-- 手机号只允许以哈希列做唯一约束，禁止明文唯一索引
CREATE UNIQUE INDEX uk_lead_mobile ON fenglin_crm.crm_lead (tenant_id, mobile_hash)
    WHERE deleted = 0 AND mobile_hash IS NOT NULL;
CREATE INDEX idx_lead_tenant_status ON fenglin_crm.crm_lead (tenant_id, status, deleted);
CREATE INDEX idx_lead_owner ON fenglin_crm.crm_lead (tenant_id, owner_id, status);
CREATE INDEX idx_lead_mobile_hash ON fenglin_crm.crm_lead (tenant_id, mobile_hash);
CREATE INDEX idx_lead_last_activity ON fenglin_crm.crm_lead (tenant_id, last_activity_at);

CREATE TRIGGER trg_crm_lead_updated_at BEFORE UPDATE ON fenglin_crm.crm_lead
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();
