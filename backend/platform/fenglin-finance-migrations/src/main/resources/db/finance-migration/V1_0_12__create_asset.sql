-- D3：资产类别、资产卡片、折旧明细、资产变更（处置）
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_asset_category (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    category_code varchar(32) NOT NULL,
    category_name varchar(128) NOT NULL,
    useful_life_months int NOT NULL,
    residual_rate numeric(9,6) NOT NULL DEFAULT 0,
    depreciation_method varchar(16) NOT NULL,
    asset_account_code varchar(32) NOT NULL,
    depreciation_account_code varchar(32) NOT NULL,
    expense_account_code varchar(32) NOT NULL,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_asset_category IS '资产类别（折旧规则）';
COMMENT ON COLUMN fin_asset_category.depreciation_method IS '折旧方法：STRAIGHT_LINE/DOUBLE_DECLINING/SUM_OF_YEARS';

CREATE TABLE fin_asset_card (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    asset_no varchar(32) NOT NULL,
    asset_name varchar(128) NOT NULL,
    category_id bigint NOT NULL,
    specification varchar(255),
    location varchar(128),
    dept_id bigint,
    custodian_id bigint,
    enable_date date NOT NULL,
    original_value numeric(20,4) NOT NULL,
    accumulated_depreciation numeric(20,4) NOT NULL DEFAULT 0,
    net_value numeric(20,4) NOT NULL,
    residual_rate numeric(9,6) NOT NULL DEFAULT 0,
    useful_life_months int NOT NULL,
    depreciation_method varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    it_asset_flag boolean NOT NULL DEFAULT false,
    source varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_asset_card IS '固定资产卡片';
COMMENT ON COLUMN fin_asset_card.status IS '状态：IN_USE/IDLE/LENT/MAINTENANCE/DISPOSED';

CREATE TABLE fin_depreciation_schedule (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    batch_no varchar(32) NOT NULL,
    asset_id bigint NOT NULL,
    period_code varchar(7) NOT NULL,
    method varchar(16) NOT NULL,
    original_value numeric(20,4) NOT NULL,
    depreciation_amount numeric(20,4) NOT NULL,
    accumulated_after numeric(20,4) NOT NULL,
    net_value_after numeric(20,4) NOT NULL,
    voucher_id bigint,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_depreciation_schedule IS '折旧明细（asset_id+period_code 唯一，防重复计提）';
COMMENT ON COLUMN fin_depreciation_schedule.status IS '状态：GENERATED/POSTED';

CREATE TABLE fin_asset_change (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    change_no varchar(32) NOT NULL,
    asset_id bigint NOT NULL,
    change_type varchar(16) NOT NULL,
    before_json jsonb,
    after_json jsonb,
    gain_loss numeric(20,4),
    voucher_id bigint,
    status varchar(16) NOT NULL,
    operator_id bigint NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_asset_change IS '资产变更（处置/调拨）';
COMMENT ON COLUMN fin_asset_change.change_type IS '类型：DISPOSE/TRANSFER/IMPROVE';
COMMENT ON COLUMN fin_asset_change.status IS '状态：APPROVING/EFFECTED';
