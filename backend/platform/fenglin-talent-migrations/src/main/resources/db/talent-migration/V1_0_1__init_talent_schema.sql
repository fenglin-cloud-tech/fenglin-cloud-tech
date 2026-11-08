-- 业务域库 fenglin_talent 初始化：schema、扩展、应用角色、公共函数、业务编号序列
-- 说明：云才使用独立库（生产独立 database，本地/CI 至少独立 schema），不与平台库 fenglin_hub 混用。
-- 文档基线：03_丰麟云才_03_数据库设计文档（FL-TALENT-DB-001）第 1、6、8 节。
-- 偏差标注：文档第 8 节以 V1_0_0 作为初始化脚本；本模块沿用云协同构范式从 V1_0_1 起编，
--           建表脚本顺延一位（V1_0_2 ~ V1_0_10），索引/RLS/字典保持 V1_1_0 / V1_1_1 / V1_1_2。
-- 枚举一律 varchar + 列注释，校验在应用层（文档 P9）；外键由应用层保证，DDL 不建 REFERENCES。

CREATE SCHEMA IF NOT EXISTS fenglin_talent;

SET search_path TO fenglin_talent, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_talent_app') THEN
        CREATE ROLE fl_talent_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_talent TO fl_talent_app;

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

-- 业务编号序列（雪花 ID 之外的对外可读编号）
-- 招聘需求 TAL{yyyyMM}{6位}；入职 ONB{yyyyMM}{6位}；创新提案 IDE{yyyyMM}{6位}
-- 问卷 SUR{yyyyMM}{6位}；人才需求 DMD{yyyyMM}{6位}
CREATE SEQUENCE IF NOT EXISTS talent_req_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS talent_onboard_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS talent_proposal_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS talent_survey_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS talent_demand_no_seq AS bigint START WITH 1 INCREMENT BY 1;

GRANT USAGE, SELECT ON SEQUENCE talent_req_no_seq TO fl_talent_app;
GRANT USAGE, SELECT ON SEQUENCE talent_onboard_no_seq TO fl_talent_app;
GRANT USAGE, SELECT ON SEQUENCE talent_proposal_no_seq TO fl_talent_app;
GRANT USAGE, SELECT ON SEQUENCE talent_survey_no_seq TO fl_talent_app;
GRANT USAGE, SELECT ON SEQUENCE talent_demand_no_seq TO fl_talent_app;

COMMENT ON SCHEMA fenglin_talent IS '丰麟云才（Talent）业务域库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
