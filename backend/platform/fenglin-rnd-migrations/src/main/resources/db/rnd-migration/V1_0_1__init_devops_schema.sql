-- 业务域库 fenglin_devops 初始化：schema、扩展、应用角色、公共函数、业务编号序列
-- 文档基线：05_丰麟云研_03_数据库设计文档（FL-DEVOPS-DB-001）第 1、8 节。
-- 偏差与兜底标注：
--   1) 文档第 8 节以 V1_0_0 作为初始化脚本；本模块沿用云财/云才同构范式从 V1_0_1 起编，
--      建表脚本顺延一位，索引/RLS/种子为 V1_1_0 / V1_1_1 / V1_1_2。
--   2) 文档 RLS 使用 current_setting('app.tenant_id')；平台统一 current_tenant_id()，按总纲兜底。
--   3) 文档要求 devops_commit / devops_webhook_event 月分区；本地内嵌 PostgreSQL 与既有范式
--      统一使用普通表 + 时间索引，分区与归档在部署阶段执行，此处不建分区表。
--   4) 文档未列租户盐表；为 PII 哈希（作者邮箱/令牌）补 devops_tenant_key，见 V1_0_16。
-- 枚举一律 varchar + 列注释，外键由应用层保证，DDL 不建 REFERENCES。

CREATE SCHEMA IF NOT EXISTS fenglin_devops;

SET search_path TO fenglin_devops, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_devops_app') THEN
        CREATE ROLE fl_devops_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_devops TO fl_devops_app;

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

CREATE SEQUENCE IF NOT EXISTS devops_req_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS devops_task_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS devops_defect_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS devops_sprint_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS devops_case_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS devops_plan_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS devops_release_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS devops_run_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS devops_build_no_seq AS bigint START WITH 1 INCREMENT BY 1;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_devops TO fl_devops_app;

COMMENT ON SCHEMA fenglin_devops IS '丰麟云研（DevOps）业务域库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
