-- 业务域库 fenglin_legal 初始化：schema、扩展、应用角色、公共函数、业务编号序列
-- 文档基线：07_丰麟云法_03_数据库设计文档（FL-LEGAL-DB-003）第 1、6、8 节。
-- 偏差与兜底标注：
--   1) 文档第 8 节以 V1_0_1..V1_0_15 编排；本模块沿用云研/云服同构范式合并为
--      V1_0_1（初始化）、V1_0_2..V1_0_6（建表）、V1_0_7（索引）、V1_0_8（RLS）、
--      V1_0_9（防删触发器）。公共字段、PII 密文列、哈希链列在建表时内联，
--      不再单独出 V1_0_7/V1_0_10/V1_0_11。
--   2) 文档 RLS 使用 current_setting('app.tenant_id')；平台统一 current_tenant_id()，按总纲兜底。
--   3) 文档要求 legal_dlp_event / legal_dlp_audit_log / legal_sign_record 声明式分区；
--      既有范式统一使用普通表 + 时间索引，分区与归档在部署阶段执行，此处不建分区（V1_0_13 合并说明）。
--   4) 文档应用角色为 legal_app；既有库统一 fl_*_app 命名，本库使用 fl_legal_app。
--      平台只读角色 legal_platform_ro 属部署期 DBA 授权，不在迁移脚本内建。
--   5) 文档未列租户盐表与幂等表；为 PII 哈希与写接口幂等补 legal_tenant_key / legal_idempotency_record。
--   6) 文档 V1_0_14 字典种子属平台库 fenglin_hub（hub_dict_type/hub_dict_item），
--      按云服范式归入 bootstrap 模块（步骤 B），业务库不重复建模；
--      V1_0_15 归档任务依赖 XXL-Job，部署阶段接入，本模块不建存储过程。
-- 枚举一律 varchar + 列注释，外键由应用层保证，DDL 不建 REFERENCES。

CREATE SCHEMA IF NOT EXISTS fenglin_legal;

SET search_path TO fenglin_legal, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS btree_gin WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_legal_app') THEN
        CREATE ROLE fl_legal_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_legal TO fl_legal_app;

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

CREATE SEQUENCE IF NOT EXISTS legal_template_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS legal_contract_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS legal_seal_apply_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS legal_seal_record_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS legal_sign_task_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS legal_evidence_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS legal_dlp_event_no_seq AS bigint START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS legal_watermark_no_seq AS bigint START WITH 1 INCREMENT BY 1;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_legal TO fl_legal_app;

COMMENT ON SCHEMA fenglin_legal IS '丰麟云法（Legal）业务域库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
COMMENT ON FUNCTION fl_block_delete() IS '不可删表防删除/防软删触发器函数';
