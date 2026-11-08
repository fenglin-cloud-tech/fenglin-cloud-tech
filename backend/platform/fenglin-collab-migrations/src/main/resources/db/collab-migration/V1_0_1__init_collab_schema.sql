-- 业务域库 fenglin_collab 初始化：schema、扩展、应用角色、公共函数
-- 说明：云协使用独立库（生产独立 database，本地/CI 至少独立 schema），不与平台库 fenglin_hub 混用。
-- 文档基线：02_丰麟云协_03_数据库设计文档（FL-COLLAB-DB-001）第 1、6、8 节。

CREATE SCHEMA IF NOT EXISTS fenglin_collab;

SET search_path TO fenglin_collab, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

-- 会议室时段排他约束依赖 btree_gist；部分嵌入式/受限环境无 contrib，此处降级为应用层锁 + 重叠校验。
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'btree_gist 不可用，会议室排他约束降级为应用层 pg_advisory_xact_lock + 时段重叠校验';
    END;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_collab_app') THEN
        CREATE ROLE fl_collab_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_collab TO fl_collab_app;

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

-- 审批实例编号序列：APV{yyyyMM}{6位}
CREATE SEQUENCE IF NOT EXISTS collab_apv_no_seq AS bigint START WITH 1 INCREMENT BY 1;
GRANT USAGE, SELECT ON SEQUENCE collab_apv_no_seq TO fl_collab_app;

COMMENT ON SCHEMA fenglin_collab IS '丰麟云协（Collaborate）业务域库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
