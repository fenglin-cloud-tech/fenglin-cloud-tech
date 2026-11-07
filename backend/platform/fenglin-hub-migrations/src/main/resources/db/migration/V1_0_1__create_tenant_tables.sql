-- 首批平台表：租户、套餐、订阅、计量、账单、License
-- 与《数据库设计文档》差异：hub_tenant 增加 hash_salt_enc/hash_version（C-18，待文档同步）

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_tenant (
    id                 bigint       PRIMARY KEY,
    code               varchar(64)  NOT NULL,
    uuid               uuid         NOT NULL DEFAULT gen_random_uuid(),
    name               varchar(128) NOT NULL,
    status             varchar(32)  NOT NULL DEFAULT 'ACTIVE',
    contact_name       varchar(64),
    contact_phone_enc  text,
    contact_phone_hash varchar(64),
    hash_salt_enc      text         NOT NULL,
    hash_version       integer      NOT NULL DEFAULT 1,
    expire_at          timestamptz,
    created_by         bigint,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         bigint,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    deleted            smallint     NOT NULL DEFAULT 0,
    version            integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_tenant_code UNIQUE (code),
    CONSTRAINT uk_hub_tenant_uuid UNIQUE (uuid)
);
CREATE INDEX idx_hub_tenant_phone_hash ON hub_tenant (contact_phone_hash);
CREATE INDEX idx_hub_tenant_status ON hub_tenant (status);
CREATE TRIGGER trg_hub_tenant_updated_at BEFORE UPDATE ON hub_tenant
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_package (
    id          bigint        PRIMARY KEY,
    code        varchar(64)   NOT NULL,
    name        varchar(128)  NOT NULL,
    description varchar(512),
    status      varchar(32)   NOT NULL DEFAULT 'ACTIVE',
    price_month numeric(18,2) NOT NULL DEFAULT 0,
    price_year  numeric(18,2) NOT NULL DEFAULT 0,
    currency    char(3)       NOT NULL DEFAULT 'CNY',
    created_by  bigint,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    deleted     smallint      NOT NULL DEFAULT 0,
    version     integer       NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_package_code UNIQUE (code)
);
CREATE TRIGGER trg_hub_package_updated_at BEFORE UPDATE ON hub_package
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_package_item (
    id         bigint       PRIMARY KEY,
    package_id bigint       NOT NULL REFERENCES hub_package (id),
    item_code  varchar(64)  NOT NULL,
    item_name  varchar(128) NOT NULL,
    quota      bigint       NOT NULL DEFAULT 0,
    unit       varchar(32)  NOT NULL DEFAULT 'COUNT',
    created_by bigint,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz  NOT NULL DEFAULT now(),
    deleted    smallint     NOT NULL DEFAULT 0,
    version    integer      NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_package_item UNIQUE (package_id, item_code)
);
CREATE INDEX idx_hub_package_item_package ON hub_package_item (package_id);
CREATE TRIGGER trg_hub_package_item_updated_at BEFORE UPDATE ON hub_package_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_subscription (
    id         bigint      PRIMARY KEY,
    tenant_id  bigint      NOT NULL,
    package_id bigint      NOT NULL REFERENCES hub_package (id),
    status     varchar(32) NOT NULL DEFAULT 'ACTIVE',
    trial      smallint    NOT NULL DEFAULT 0,
    auto_renew smallint    NOT NULL DEFAULT 0,
    start_at   timestamptz NOT NULL DEFAULT now(),
    end_at     timestamptz,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted    smallint    NOT NULL DEFAULT 0,
    version    integer     NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_hub_subscription_active
    ON hub_subscription (tenant_id) WHERE status = 'ACTIVE' AND deleted = 0;
CREATE INDEX idx_hub_subscription_tenant ON hub_subscription (tenant_id);
CREATE TRIGGER trg_hub_subscription_updated_at BEFORE UPDATE ON hub_subscription
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_metering (
    id          bigint        NOT NULL,
    tenant_id   bigint        NOT NULL,
    metric_code varchar(64)   NOT NULL,
    quantity    numeric(18,4) NOT NULL DEFAULT 0,
    source_type varchar(32),
    source_id   varchar(64),
    occurred_at timestamptz   NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE TABLE hub_metering_default PARTITION OF hub_metering DEFAULT;

CREATE INDEX idx_hub_metering_tenant_time ON hub_metering (tenant_id, occurred_at);
CREATE INDEX idx_hub_metering_metric_time ON hub_metering (tenant_id, metric_code, occurred_at);

CREATE TABLE hub_usage_daily (
    id          bigint        PRIMARY KEY,
    tenant_id   bigint        NOT NULL,
    metric_code varchar(64)   NOT NULL,
    usage_date  date          NOT NULL,
    quantity    numeric(18,4) NOT NULL DEFAULT 0,
    created_by  bigint,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    deleted     smallint      NOT NULL DEFAULT 0,
    version     integer       NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_usage_daily UNIQUE (tenant_id, metric_code, usage_date)
);
CREATE TRIGGER trg_hub_usage_daily_updated_at BEFORE UPDATE ON hub_usage_daily
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_bill (
    id           bigint        PRIMARY KEY,
    tenant_id    bigint        NOT NULL,
    bill_no      varchar(64)   NOT NULL,
    period_start date          NOT NULL,
    period_end   date          NOT NULL,
    status       varchar(32)   NOT NULL DEFAULT 'PENDING',
    amount       numeric(18,2) NOT NULL DEFAULT 0,
    currency     char(3)       NOT NULL DEFAULT 'CNY',
    due_at       timestamptz,
    paid_at      timestamptz,
    created_by   bigint,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_by   bigint,
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    deleted      smallint      NOT NULL DEFAULT 0,
    version      integer       NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_bill_no UNIQUE (tenant_id, bill_no)
);
CREATE INDEX idx_hub_bill_tenant_period ON hub_bill (tenant_id, period_start);
CREATE TRIGGER trg_hub_bill_updated_at BEFORE UPDATE ON hub_bill
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_bill_item (
    id         bigint        PRIMARY KEY,
    tenant_id  bigint        NOT NULL,
    bill_id    bigint        NOT NULL REFERENCES hub_bill (id),
    item_code  varchar(64)   NOT NULL,
    item_name  varchar(128)  NOT NULL,
    quantity   numeric(18,4) NOT NULL DEFAULT 0,
    unit_price numeric(18,4) NOT NULL DEFAULT 0,
    amount     numeric(18,2) NOT NULL DEFAULT 0,
    created_by bigint,
    created_at timestamptz   NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz   NOT NULL DEFAULT now(),
    deleted    smallint      NOT NULL DEFAULT 0,
    version    integer       NOT NULL DEFAULT 0
);
CREATE INDEX idx_hub_bill_item_bill ON hub_bill_item (bill_id);
CREATE TRIGGER trg_hub_bill_item_updated_at BEFORE UPDATE ON hub_bill_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hub_license (
    id               bigint      PRIMARY KEY,
    tenant_id        bigint      NOT NULL,
    license_key_hash varchar(64) NOT NULL,
    license_type     varchar(32) NOT NULL DEFAULT 'SUBSCRIPTION',
    seats            integer     NOT NULL DEFAULT 0,
    status           varchar(32) NOT NULL DEFAULT 'ACTIVE',
    issued_at        timestamptz NOT NULL DEFAULT now(),
    expire_at        timestamptz,
    created_by       bigint,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_by       bigint,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted          smallint    NOT NULL DEFAULT 0,
    version          integer     NOT NULL DEFAULT 0,
    CONSTRAINT uk_hub_license_key UNIQUE (tenant_id, license_key_hash)
);
CREATE TRIGGER trg_hub_license_updated_at BEFORE UPDATE ON hub_license
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON
    hub_tenant, hub_package, hub_package_item, hub_subscription, hub_metering,
    hub_usage_daily, hub_bill, hub_bill_item, hub_license
    TO fl_app, fl_platform_role;

COMMENT ON TABLE hub_tenant IS '租户主表（平台跨租户）';
COMMENT ON COLUMN hub_tenant.hash_salt_enc IS '租户级 PII 检索盐（AES-GCM 加密），待文档同步';
COMMENT ON TABLE hub_metering IS '计量明细，按 occurred_at 月分区，默认分区兜底';
