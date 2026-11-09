-- 业务域库 fenglin_intelligence 初始化：schema、扩展、应用角色、公共函数、业务编号序列
-- 文档基线：08_丰麟云智_03_数据库设计文档（FL-AI-DB-003）第 1、6、8 节。
--
-- 与平台 AI 中台（fenglin-ai-service / 平台库 fenglin_hub）的边界（按总纲兜底并逐条标注）：
--   1) 本文档 30 张表中，ai_prompt_template / ai_model_provider / ai_capability_grant /
--      ai_usage_metering 不在本库建模：提示词、模型供应商、能力授权、用量计量分别由
--      平台库 hub_ai_prompt / hub_ai_model / hub_ai_capability_grant / hub_ai_metering 承担，
--      hub_ai_* 是授权与计费的唯一源，云智不得重复实现模型调用与计费。
--      故本库业务表为 26 张，另补 ai_tenant_key（PII 盐）、ai_idempotency_record（写幂等）、
--      ai_insight_subscription（接口 AI-API-244 需持久化，文档未建模）共 29 张。
--   2) 文档以 V1_0_0..V1_0_16 编排；本模块沿用云研/云服/云法同构范式合并为
--      V1_0_1（初始化）、V1_0_2..V1_0_6（建表）、V1_0_7（索引）、V1_0_8（RLS）、
--      V1_0_9（触发器）、V1_0_10（内置指标种子）。
--   3) 文档 RLS 使用 current_setting('app.tenant_id')，平台统一 current_tenant_id()，按总纲兜底。
--   4) 文档要求 ai_search_log / ai_message / ai_usage_metering 分区；计量表由平台承担，
--      其余两表沿用既有范式建普通表 + 时间索引，分区与冷归档在部署阶段执行。
--   5) 文档应用角色未命名；既有库统一 fl_*_app，本库使用 fl_intelligence_app。
--   6) 文档 V1_0_16 字典/默认提示词归平台库 fenglin_hub，按云服/云法范式归入 bootstrap（步骤 B），
--      业务库仅保留内置指标模板（tenant_id=0 平台级只读）。
-- 枚举一律 varchar + 列注释，外键由应用层保证，DDL 不建 REFERENCES。

CREATE SCHEMA IF NOT EXISTS fenglin_intelligence;

SET search_path TO fenglin_intelligence, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS btree_gin WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_intelligence_app') THEN
        CREATE ROLE fl_intelligence_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_intelligence TO fl_intelligence_app;

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

CREATE OR REPLACE FUNCTION fl_block_delete()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'immutable table: % cannot be deleted/soft-deleted', TG_TABLE_NAME;
END;
$$;

CREATE SEQUENCE IF NOT EXISTS ai_kb_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ai_dataset_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS ai_dashboard_no_seq AS bigint START WITH 1 INCREMENT BY 1;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_intelligence TO fl_intelligence_app;

COMMENT ON SCHEMA fenglin_intelligence IS '丰麟云智（Intelligence）业务域库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
COMMENT ON FUNCTION fl_block_delete() IS '不可删表防删除/防软删触发器函数';
