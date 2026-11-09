-- 业务域库 fenglin_service 初始化：schema、扩展、应用角色、公共函数、业务编号序列
-- 文档基线：06_丰麟云服_03_数据库设计文档（FL-SVC-DB-003）第 1、8 节。
-- 偏差与兜底标注：
--   1) 文档第 8 节以 V1.0.1 起编；本模块沿用云研同构范式，建表 V1_0_2..V1_0_11，
--      索引/RLS/种子为 V1_1_0 / V1_1_1 / V1_1_2。
--   2) 文档 RLS 使用 current_setting('app.tenant_id')；平台统一 current_tenant_id()，按总纲兜底。
--   3) 文档要求 svc_ticket_flow_log / svc_mining_event_log / svc_robot_task_log 月分区；
--      既有范式统一使用普通表 + 时间索引，分区与归档在部署阶段执行，此处不建分区表。
--   4) 文档未列租户盐表与幂等表；为 PII 哈希与写接口幂等补 svc_tenant_key / svc_idempotency_record。
--   5) 文档第 8 节 V1.0.18 的机器人凭据 KMS 列已并入 V1_0_10 建表，不单独追加脚本。
-- 枚举一律 varchar + 列注释，外键由应用层保证，DDL 不建 REFERENCES。

CREATE SCHEMA IF NOT EXISTS fenglin_service;

SET search_path TO fenglin_service, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_service_app') THEN
        CREATE ROLE fl_service_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_service TO fl_service_app;

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

CREATE SEQUENCE IF NOT EXISTS svc_ticket_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS svc_problem_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS svc_change_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS svc_release_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS svc_robot_task_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS svc_app_code_seq AS bigint START WITH 1 INCREMENT BY 1;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_service TO fl_service_app;

COMMENT ON SCHEMA fenglin_service IS '丰麟云服（Service）业务域库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
