-- 平台库 fenglin_hub 初始化：schema、扩展、角色、公共函数
-- 说明：角色为集群级对象，生产环境应由 DBA 预创建；此处使用幂等 DO 块保证本地与 CI 空库可执行。

CREATE SCHEMA IF NOT EXISTS fenglin_hub;

SET search_path TO fenglin_hub, public;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_app') THEN
        CREATE ROLE fl_app NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_platform_role') THEN
        CREATE ROLE fl_platform_role NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA fenglin_hub TO fl_app, fl_platform_role;

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

CREATE OR REPLACE FUNCTION current_user_id()
RETURNS bigint
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(current_setting('app.user_id', true), '')::bigint;
$$;

CREATE OR REPLACE FUNCTION utc_now()
RETURNS timestamptz
LANGUAGE sql
STABLE
AS $$
    SELECT now() AT TIME ZONE 'UTC';
$$;

-- 数据库侧雪花兜底（应用侧仍以 MyBatis-Plus 雪花为主）：毫秒时间戳 41 位 + 序列 22 位
CREATE SEQUENCE IF NOT EXISTS hub_snowflake_seq AS bigint START WITH 1 INCREMENT BY 1;

CREATE OR REPLACE FUNCTION next_snowflake_id()
RETURNS bigint
LANGUAGE sql
VOLATILE
AS $$
    SELECT (((extract(epoch FROM clock_timestamp()) * 1000)::bigint) << 22)
           | (nextval('fenglin_hub.hub_snowflake_seq') & 4194303);
$$;

COMMENT ON SCHEMA fenglin_hub IS '丰麟云枢平台库 schema';
COMMENT ON FUNCTION current_tenant_id() IS '当前事务租户，来源于 SET LOCAL app.tenant_id';
COMMENT ON FUNCTION next_snowflake_id() IS '数据库侧雪花 ID 兜底，单实例内单调递增';
