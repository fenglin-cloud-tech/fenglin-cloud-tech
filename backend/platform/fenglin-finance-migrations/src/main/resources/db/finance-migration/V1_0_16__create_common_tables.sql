-- 公共：币种、汇率、附件、幂等记录、租户密钥
-- 偏差标注：fin_tenant_key 为总纲要求的 PII 租户盐存储，文档 45 表清单未列，按云才同构补充（不计入业务表）。
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_currency (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    currency_code varchar(3) NOT NULL,
    currency_name varchar(64) NOT NULL,
    symbol varchar(8),
    decimal_places smallint NOT NULL DEFAULT 2,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_currency IS '币种';

CREATE TABLE fin_exchange_rate (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    from_currency varchar(3) NOT NULL,
    to_currency varchar(3) NOT NULL,
    rate numeric(18,8) NOT NULL,
    effective_date date NOT NULL,
    source varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_exchange_rate IS '汇率（rate 保留 8 位小数）';

CREATE TABLE fin_attachment (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    biz_type varchar(32) NOT NULL,
    biz_id bigint NOT NULL,
    file_id bigint NOT NULL,
    file_name varchar(255),
    file_size bigint,
    uploaded_by bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_attachment IS '业务附件关联（文件中心 file_id）';

CREATE TABLE fin_idempotency_record (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    idempotency_key varchar(64) NOT NULL,
    api_path varchar(128) NOT NULL,
    biz_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_idempotency_record IS '接口幂等记录（租户内 key 唯一）';

CREATE TABLE fin_tenant_key (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    salt varchar(64) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_tenant_key IS '租户 PII 哈希盐（每租户一条）';
