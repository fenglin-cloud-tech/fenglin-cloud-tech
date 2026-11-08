-- 客户主档与联系人（01_丰麟云销_03 数据库设计 4.2 / 4.3）
SET search_path TO fenglin_crm, public;

CREATE TABLE fenglin_crm.crm_customer (
    id              bigint        PRIMARY KEY,
    uuid            uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       bigint        NOT NULL,
    customer_no     varchar(32)   NOT NULL,
    name            varchar(200)  NOT NULL,
    short_name      varchar(64),
    uscc            varchar(32),
    industry        varchar(64),
    scale           varchar(32),
    region_code     varchar(16),
    grade           varchar(4),
    segment         varchar(8),
    owner_id        bigint,
    csm_id          bigint,
    dept_id         bigint,
    mobile_enc      text,
    mobile_hash     varchar(64),
    mobile_tail     varchar(4),
    annual_revenue  numeric(18, 2),
    arr             numeric(18, 2),
    status          varchar(24)   NOT NULL DEFAULT 'ACTIVE',
    source_lead_id  bigint,
    tags            jsonb,
    remark          text,
    created_by      bigint        NOT NULL,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_by      bigint        NOT NULL,
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    deleted         smallint      NOT NULL DEFAULT 0,
    version         int           NOT NULL DEFAULT 0,
    CONSTRAINT ck_cust_status CHECK (status IN ('ACTIVE', 'CHURNED', 'POTENTIAL')),
    CONSTRAINT ck_cust_segment CHECK (segment IS NULL OR segment IN ('S', 'A', 'B', 'C')),
    CONSTRAINT ck_cust_grade CHECK (grade IS NULL OR grade IN ('A', 'B', 'C', 'D'))
);

CREATE UNIQUE INDEX uk_cust_uuid ON fenglin_crm.crm_customer (uuid);
CREATE UNIQUE INDEX uk_cust_no ON fenglin_crm.crm_customer (tenant_id, customer_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_cust_uscc ON fenglin_crm.crm_customer (tenant_id, uscc)
    WHERE deleted = 0 AND uscc IS NOT NULL;
CREATE INDEX idx_cust_owner ON fenglin_crm.crm_customer (tenant_id, owner_id);
CREATE INDEX idx_cust_csm ON fenglin_crm.crm_customer (tenant_id, csm_id, segment);
CREATE INDEX idx_cust_mobile_hash ON fenglin_crm.crm_customer (tenant_id, mobile_hash);

CREATE TRIGGER trg_crm_customer_updated_at BEFORE UPDATE ON fenglin_crm.crm_customer
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();

CREATE TABLE fenglin_crm.crm_contact (
    id           bigint       PRIMARY KEY,
    uuid         uuid         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    bigint       NOT NULL,
    customer_id  bigint       NOT NULL,
    name         varchar(64)  NOT NULL,
    position     varchar(64),
    role_type    varchar(24),
    mobile_enc   text,
    mobile_hash  varchar(64),
    mobile_tail  varchar(4),
    email        varchar(128),
    wechat       varchar(64),
    influence    smallint     NOT NULL DEFAULT 0,
    is_key       boolean      NOT NULL DEFAULT false,
    status       varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    created_by   bigint       NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   bigint       NOT NULL,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted      smallint     NOT NULL DEFAULT 0,
    version      int          NOT NULL DEFAULT 0,
    CONSTRAINT ck_contact_role CHECK (role_type IS NULL OR role_type IN ('DECISION', 'INFLUENCER', 'USER', 'FINANCE')),
    CONSTRAINT ck_contact_status CHECK (status IN ('ACTIVE', 'LEFT')),
    CONSTRAINT ck_contact_influence CHECK (influence BETWEEN 0 AND 5)
);

CREATE UNIQUE INDEX uk_contact_uuid ON fenglin_crm.crm_contact (uuid);
CREATE INDEX idx_contact_customer ON fenglin_crm.crm_contact (tenant_id, customer_id, deleted);
CREATE INDEX idx_contact_mobile_hash ON fenglin_crm.crm_contact (tenant_id, mobile_hash);

CREATE TRIGGER trg_crm_contact_updated_at BEFORE UPDATE ON fenglin_crm.crm_contact
    FOR EACH ROW EXECUTE FUNCTION fenglin_crm.set_updated_at();
