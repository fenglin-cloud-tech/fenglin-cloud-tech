-- D3：资产盘点、IT 资产台账
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_asset_stocktaking (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    stocktaking_no varchar(32) NOT NULL,
    stocktaking_name varchar(128) NOT NULL,
    scope_type varchar(16) NOT NULL,
    dept_id bigint,
    plan_start_date date,
    plan_end_date date,
    actual_end_date date,
    total_count int NOT NULL DEFAULT 0,
    normal_count int NOT NULL DEFAULT 0,
    surplus_count int NOT NULL DEFAULT 0,
    deficit_count int NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_asset_stocktaking IS '资产盘点单';
COMMENT ON COLUMN fin_asset_stocktaking.scope_type IS '范围：ALL/DEPT';
COMMENT ON COLUMN fin_asset_stocktaking.status IS '状态：DRAFT/IN_PROGRESS/COMPLETED';

CREATE TABLE fin_asset_stocktaking_item (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    stocktaking_id bigint NOT NULL,
    asset_id bigint,
    asset_no varchar(32),
    asset_name varchar(128),
    expected_location varchar(128),
    actual_location varchar(128),
    expected_custodian_id bigint,
    actual_custodian_id bigint,
    result varchar(16) NOT NULL,
    remark varchar(255),
    counted_at timestamptz,
    counted_by bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_asset_stocktaking_item IS '盘点明细';
COMMENT ON COLUMN fin_asset_stocktaking_item.result IS '结果：NORMAL/SURPLUS/DEFICIT/POSITION_CHANGED/NOT_FOUND';

CREATE TABLE fin_it_asset (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    asset_id bigint NOT NULL,
    device_type varchar(32) NOT NULL,
    brand_model varchar(128),
    serial_no varchar(64),
    ip_address varchar(64),
    mac_address varchar(64),
    os_version varchar(64),
    software_list jsonb,
    borrow_status varchar(16) NOT NULL,
    borrower_id bigint,
    borrow_date date,
    due_date date,
    cmdb_ci_id varchar(64),
    sync_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_it_asset IS 'IT 资产台账（CMDB 同步状态）';
COMMENT ON COLUMN fin_it_asset.borrow_status IS '借用状态：AVAILABLE/BORROWED/MAINTENANCE';
COMMENT ON COLUMN fin_it_asset.sync_status IS '同步状态：PENDING/SYNCED/FAILED';
