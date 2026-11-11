-- ============================================================================
-- PLAT-20 DDL 身份收口（3/3）：分区表属主迁移到 fl_ddl_owner
--
-- 目标：五张分区父表及其全部现有子分区（含 _default）OWNER TO fl_ddl_owner。
-- 目的：让独立 DDL 连接（fl_login_ddl + SET ROLE fl_ddl_owner）能在不依赖超级用户、
--       也不依赖表属主为 fenglin 的前提下执行 CREATE/DETACH/ATTACH/GRANT。
--
-- 影响评估（2026-11-11 实测，均已确认）：
--   * 视图依赖 0、外键 0、表拥有序列 0；
--   * ALTER OWNER 保留既有显式授权：fl_app / fl_platform_role 的 arwd 不受影响；
--   * RLS 策略挂在表上，不随属主变化，策略数不变；
--   * fl_ddl_owner 为 NOLOGIN NOSUPERUSER NOBYPASSRLS，且 relforcerowsecurity=f。
-- 可重复执行：已属主相等的对象跳过，不做无谓 ALTER。
-- ============================================================================

SET search_path TO fenglin_hub, public;

-- 1) 前置守卫 + 幂等迁移
DO $$
DECLARE
    missing_parents text;
    migrated integer := 0;
    rec record;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fl_ddl_owner') THEN
        RAISE EXCEPTION '角色 fl_ddl_owner 不存在，无法迁移分区属主';
    END IF;

    SELECT string_agg(t, ', ')
      INTO missing_parents
      FROM unnest(ARRAY['hub_audit_log', 'hub_message', 'hub_api_call_log', 'hub_ai_metering', 'hub_metering']) AS t
     WHERE to_regclass('fenglin_hub.' || t) IS NULL;
    IF missing_parents IS NOT NULL THEN
        RAISE EXCEPTION '缺少分区父表: %', missing_parents;
    END IF;

    FOR rec IN
        WITH RECURSIVE parents AS (
            SELECT c.oid
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'fenglin_hub'
               AND c.relkind = 'p'
               AND c.relname IN ('hub_audit_log', 'hub_message', 'hub_api_call_log',
                                 'hub_ai_metering', 'hub_metering')
        ), tree AS (
            SELECT oid FROM parents
            UNION
            SELECT i.inhrelid
              FROM pg_inherits i
              JOIN tree t ON t.oid = i.inhparent
        )
        SELECT c.oid, c.oid::regclass::text AS qualified
          FROM pg_class c
          JOIN tree ON tree.oid = c.oid
         WHERE c.relkind IN ('r', 'p')
         ORDER BY c.oid::regclass::text
    LOOP
        IF (SELECT relowner FROM pg_class WHERE oid = rec.oid)
           <> (SELECT oid FROM pg_roles WHERE rolname = 'fl_ddl_owner') THEN
            EXECUTE format('ALTER TABLE %s OWNER TO fl_ddl_owner', rec.qualified);
            migrated := migrated + 1;
        END IF;
    END LOOP;

    RAISE NOTICE '分区属主迁移完成，本次变更 % 个对象', migrated;
END
$$;

-- 2) 迁移内自检：五张父表与全部子分区必须 owner = fl_ddl_owner
DO $$
DECLARE
    offender_count integer;
    offenders text;
BEGIN
    WITH RECURSIVE parents AS (
        SELECT c.oid
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'fenglin_hub'
           AND c.relkind = 'p'
           AND c.relname IN ('hub_audit_log', 'hub_message', 'hub_api_call_log',
                             'hub_ai_metering', 'hub_metering')
    ), tree AS (
        SELECT oid FROM parents
        UNION
        SELECT i.inhrelid
          FROM pg_inherits i
          JOIN tree t ON t.oid = i.inhparent
    )
    SELECT count(*), string_agg(c.oid::regclass::text, ', ' ORDER BY c.oid::regclass::text)
      INTO offender_count, offenders
      FROM pg_class c
      JOIN tree ON tree.oid = c.oid
     WHERE c.relkind IN ('r', 'p')
       AND c.relowner <> (SELECT oid FROM pg_roles WHERE rolname = 'fl_ddl_owner');

    IF offender_count > 0 THEN
        RAISE EXCEPTION '属主自检失败：% 个对象仍非 fl_ddl_owner：%', offender_count, offenders;
    END IF;

    RAISE NOTICE '属主自检通过：五张父表与全部子分区 owner = fl_ddl_owner';
END
$$;

-- 3) 授权自检：fl_app / fl_platform_role 的既有 DML 权限不因属主迁移而丢失
DO $$
DECLARE
    loss_count integer;
    losses text;
BEGIN
    WITH targets AS (
        SELECT c.oid
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'fenglin_hub'
           AND c.relname IN ('hub_audit_log', 'hub_message', 'hub_api_call_log',
                             'hub_ai_metering', 'hub_metering')
    )
    SELECT count(*), string_agg(c.oid::regclass::text || ':' || r.rolname, ', ')
      INTO loss_count, losses
      FROM targets t
      JOIN pg_class c ON c.oid = t.oid
      CROSS JOIN (VALUES ('fl_app'), ('fl_platform_role')) AS r(rolname)
     WHERE NOT has_table_privilege(r.rolname, c.oid, 'SELECT')
        OR NOT has_table_privilege(r.rolname, c.oid, 'INSERT')
        OR NOT has_table_privilege(r.rolname, c.oid, 'UPDATE')
        OR NOT has_table_privilege(r.rolname, c.oid, 'DELETE');

    IF loss_count > 0 THEN
        RAISE EXCEPTION '授权自检失败：属主迁移后权限缺失：%', losses;
    END IF;

    RAISE NOTICE '授权自检通过：fl_app / fl_platform_role 对五张父表的 DML 权限保留';
END
$$;
