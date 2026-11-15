-- 平台主数据（MDM）库 fenglin_mdm 初始化：schema、应用角色、公共函数
-- 说明：独立 database/schema，不与 fenglin_hub、各业务域库混用。
-- 与业务域一致：枚举 varchar + 应用层校验；表间不建外键，引用完整性由服务层保证。

CREATE SCHEMA IF NOT EXISTS fenglin_mdm;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_mdm_app') THEN
        CREATE ROLE fl_mdm_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_mdm TO fl_mdm_app;

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- RLS 依赖：读取会话级 app.tenant_id；必须先于任何策略创建
CREATE OR REPLACE FUNCTION current_tenant_id()
RETURNS bigint
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::bigint;
$$;
