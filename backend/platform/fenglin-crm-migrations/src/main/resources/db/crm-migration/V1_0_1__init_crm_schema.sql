-- 业务域库 fenglin_crm 初始化：schema、扩展、应用角色、公共函数与业务编号序列
-- 说明：CRM 使用独立库（生产为独立 database，本地/CI 至少独立 schema），不与平台库 fenglin_hub 混用。

CREATE SCHEMA IF NOT EXISTS fenglin_crm;

SET search_path TO fenglin_crm, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_crm_app') THEN
        CREATE ROLE fl_crm_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_crm TO fl_crm_app;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION current_tenant_id()
RETURNS bigint
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::bigint;
$$;

-- 线索/商机共用一个业务编号序列（01 接口文档示例中两者同为 CRM{yyyyMM}{6位} 递增口径）
CREATE SEQUENCE IF NOT EXISTS crm_biz_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_customer_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_quotation_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS crm_contract_no_seq AS bigint START WITH 1 INCREMENT BY 1;

GRANT USAGE, SELECT ON SEQUENCE crm_biz_no_seq TO fl_crm_app;
GRANT USAGE, SELECT ON SEQUENCE crm_customer_no_seq TO fl_crm_app;
GRANT USAGE, SELECT ON SEQUENCE crm_quotation_no_seq TO fl_crm_app;
GRANT USAGE, SELECT ON SEQUENCE crm_contract_no_seq TO fl_crm_app;

COMMENT ON SCHEMA fenglin_crm IS '丰麟云销（CRM）业务域库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
