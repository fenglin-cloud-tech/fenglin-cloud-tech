-- D4：排放因子、碳活动、碳排放核算
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_emission_factor (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    factor_code varchar(32) NOT NULL,
    factor_name varchar(128) NOT NULL,
    scope varchar(16) NOT NULL,
    activity_type varchar(32) NOT NULL,
    activity_unit varchar(16) NOT NULL,
    factor_value numeric(20,8) NOT NULL,
    factor_unit varchar(32) NOT NULL,
    factor_version varchar(32) NOT NULL,
    source varchar(128),
    effective_from date NOT NULL,
    effective_to date,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_emission_factor IS '排放因子库';
COMMENT ON COLUMN fin_emission_factor.scope IS '范围：SCOPE1/SCOPE2/SCOPE3';

CREATE TABLE fin_carbon_activity (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    activity_no varchar(32) NOT NULL,
    org_id bigint,
    dept_id bigint,
    activity_type varchar(32) NOT NULL,
    scope varchar(16) NOT NULL,
    activity_date date NOT NULL,
    period_code varchar(7) NOT NULL,
    amount numeric(20,4) NOT NULL,
    unit varchar(16) NOT NULL,
    factor_id bigint,
    data_source varchar(16) NOT NULL,
    evidence_file_id bigint,
    status varchar(16) NOT NULL,
    verified_by bigint,
    verified_at timestamptz,
    locked_by bigint,
    locked_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_carbon_activity IS '碳活动数据（填报→复核→锁定，锁定后不可改）';
COMMENT ON COLUMN fin_carbon_activity.data_source IS '来源：METER/INVOICE/MANUAL';
COMMENT ON COLUMN fin_carbon_activity.status IS '状态：SUBMITTED/VERIFIED/LOCKED';

CREATE TABLE fin_carbon_emission (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    emission_no varchar(32) NOT NULL,
    batch_no varchar(32) NOT NULL,
    activity_id bigint NOT NULL,
    factor_id bigint NOT NULL,
    factor_version varchar(32) NOT NULL,
    amount numeric(20,4) NOT NULL,
    factor_value numeric(20,8) NOT NULL,
    emission_amount numeric(20,4) NOT NULL,
    unit varchar(16) NOT NULL,
    period_code varchar(7) NOT NULL,
    scope varchar(16) NOT NULL,
    is_latest boolean NOT NULL DEFAULT true,
    calculated_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_carbon_emission IS '碳排放核算结果（重算置旧记录 is_latest=false）';
COMMENT ON COLUMN fin_carbon_emission.unit IS '排放量单位：tCO2e';
