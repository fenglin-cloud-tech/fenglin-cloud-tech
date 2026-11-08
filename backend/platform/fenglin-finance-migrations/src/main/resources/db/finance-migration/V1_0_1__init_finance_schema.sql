-- 业务域库 fenglin_finance 初始化：schema、扩展、应用角色、公共函数、业务编号序列
-- 说明：云财使用独立库（生产独立 database，本地/CI 至少独立 schema），不与平台库 fenglin_hub 混用。
-- 文档基线：04_丰麟云财_03_数据库设计文档（FL-FIN-DB-001）第 1、5、8 节。
-- 偏差标注：
--   1) 文档第 8 节以 V1_0_0 作为初始化脚本；本模块沿用云协/云才同构范式从 V1_0_1 起编，
--      建表脚本顺延一位（V1_0_2 ~ V1_0_16），索引/RLS 为 V1_1_0 / V1_1_1。
--   2) 文档 RLS 使用 current_setting('fl.tenant_id')；平台统一使用 app.tenant_id + current_tenant_id()，
--      故按总纲兜底为 current_tenant_id()。
--   3) 文档要求凭证分录/折旧明细/碳活动按年声明式分区；本地内嵌 PostgreSQL 与项目既有范式统一使用
--      普通表 + period_code 索引，分区与 WORM 归档在部署阶段执行，此处不建分区表。
-- 枚举一律 varchar + 列注释，校验在应用层；外键由应用层保证，DDL 不建 REFERENCES。

CREATE SCHEMA IF NOT EXISTS fenglin_finance;

SET search_path TO fenglin_finance, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_finance_app') THEN
        CREATE ROLE fl_finance_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_finance TO fl_finance_app;

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

-- 对外可读业务编号序列
CREATE SEQUENCE IF NOT EXISTS fin_project_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_voucher_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_receipt_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_payment_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_claim_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_ar_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_ap_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_write_off_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_settlement_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_cost_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_timesheet_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_asset_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_dep_batch_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_stocktaking_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_recon_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_activity_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_emission_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_target_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_esg_report_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS fin_chain_seq AS bigint START WITH 1 INCREMENT BY 1;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_finance TO fl_finance_app;

COMMENT ON SCHEMA fenglin_finance IS '丰麟云财（Finance）业务域库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
