-- PLAT-21：子分区启用 RLS 并复制父表策略
-- 顺序约束：hub_api_call_log 先建 2026-11 分区并把默认分区中的历史数据搬过去，再开启 RLS，
-- 避免先开 RLS 后搬数被策略挡掉、看起来像数据丢失。
-- 只复制父表已有策略，不新增父表没有的策略。

-- 1) hub_api_call_log 2026-11 分区 + 默认分区数据迁移（先于 RLS）
DO $$
DECLARE
    v_expected bigint;
    v_after bigint;
    v_partitioned bigint;
BEGIN
    IF to_regclass('fenglin_hub.hub_api_call_log_202611') IS NULL THEN
        SELECT count(*) INTO v_expected
        FROM fenglin_hub.hub_api_call_log_default
        WHERE called_at >= '2026-11-01 00:00:00+00' AND called_at < '2026-12-01 00:00:00+00';

        IF v_expected > 0 THEN
            ALTER TABLE fenglin_hub.hub_api_call_log DETACH PARTITION fenglin_hub.hub_api_call_log_default;
            CREATE TABLE fenglin_hub.hub_api_call_log_202611 PARTITION OF fenglin_hub.hub_api_call_log
                FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');
            INSERT INTO fenglin_hub.hub_api_call_log
            SELECT * FROM fenglin_hub.hub_api_call_log_default
            WHERE called_at >= '2026-11-01 00:00:00+00' AND called_at < '2026-12-01 00:00:00+00';
            DELETE FROM fenglin_hub.hub_api_call_log_default
            WHERE called_at >= '2026-11-01 00:00:00+00' AND called_at < '2026-12-01 00:00:00+00';
            ALTER TABLE fenglin_hub.hub_api_call_log
                ATTACH PARTITION fenglin_hub.hub_api_call_log_default DEFAULT;
        ELSE
            CREATE TABLE fenglin_hub.hub_api_call_log_202611 PARTITION OF fenglin_hub.hub_api_call_log
                FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');
        END IF;

        GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_hub.hub_api_call_log_202611
            TO fl_app, fl_platform_role;

        SELECT count(*) INTO v_after
        FROM fenglin_hub.hub_api_call_log_default
        WHERE called_at >= '2026-11-01 00:00:00+00' AND called_at < '2026-12-01 00:00:00+00';
        IF v_after <> 0 THEN
            RAISE EXCEPTION '默认分区仍残留 % 条 2026-11 调用日志', v_after;
        END IF;

        SELECT count(*) INTO v_partitioned FROM fenglin_hub.hub_api_call_log_202611;
        IF v_partitioned <> v_expected THEN
            RAISE EXCEPTION '迁移行数不一致：默认分区原有 % 条，2026-11 分区现有 % 条', v_expected, v_partitioned;
        END IF;
    END IF;
END $$;

-- 2) 现有子分区逐个开启 RLS，并复制父表策略（同名策略已存在则跳过）
DO $$
DECLARE
    v_parent record;
    v_child record;
    v_policy record;
    v_cmd text;
    v_sql text;
BEGIN
    FOR v_parent IN
        SELECT c.oid, c.relname, c.relforcerowsecurity
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'fenglin_hub'
          AND c.relname IN ('hub_audit_log', 'hub_message', 'hub_api_call_log', 'hub_ai_metering', 'hub_metering')
    LOOP
        FOR v_child IN
            SELECT c.oid, c.relname
            FROM pg_inherits i
            JOIN pg_class c ON c.oid = i.inhrelid
            WHERE i.inhparent = v_parent.oid
        LOOP
            EXECUTE format('ALTER TABLE fenglin_hub.%I ENABLE ROW LEVEL SECURITY', v_child.relname);
            IF v_parent.relforcerowsecurity THEN
                EXECUTE format('ALTER TABLE fenglin_hub.%I FORCE ROW LEVEL SECURITY', v_child.relname);
            END IF;

            FOR v_policy IN
                SELECT p.polname,
                       p.polcmd,
                       p.polpermissive,
                       p.polroles,
                       pg_get_expr(p.polqual, p.polrelid) AS qual,
                       pg_get_expr(p.polwithcheck, p.polrelid) AS check_expr,
                       ARRAY(SELECT CASE WHEN r = 0 THEN 'PUBLIC'
                                         ELSE quote_ident(pg_get_userbyid(r)) END
                             FROM unnest(p.polroles) AS r) AS roles
                FROM pg_policy p
                WHERE p.polrelid = v_parent.oid
            LOOP
                -- 按语义判重：子分区可能已有同名策略，也可能有等价但命名不同的策略
                IF NOT EXISTS (SELECT 1 FROM pg_policy ep
                               WHERE ep.polrelid = v_child.oid
                                 AND ep.polcmd = v_policy.polcmd
                                 AND ep.polpermissive = v_policy.polpermissive
                                 AND ep.polroles = v_policy.polroles
                                 AND coalesce(pg_get_expr(ep.polqual, ep.polrelid), '')
                                     = coalesce(v_policy.qual, '')
                                 AND coalesce(pg_get_expr(ep.polwithcheck, ep.polrelid), '')
                                     = coalesce(v_policy.check_expr, '')) THEN
                    v_cmd := CASE v_policy.polcmd
                                 WHEN 'r' THEN 'SELECT'
                                 WHEN 'a' THEN 'INSERT'
                                 WHEN 'w' THEN 'UPDATE'
                                 WHEN 'd' THEN 'DELETE'
                                 ELSE 'ALL'
                             END;
                    v_sql := format('CREATE POLICY %I ON fenglin_hub.%I AS %s FOR %s TO %s',
                            v_policy.polname, v_child.relname,
                            CASE WHEN v_policy.polpermissive THEN 'PERMISSIVE' ELSE 'RESTRICTIVE' END,
                            v_cmd, array_to_string(v_policy.roles, ', '));
                    IF v_policy.qual IS NOT NULL THEN
                        v_sql := v_sql || ' USING (' || v_policy.qual || ')';
                    END IF;
                    IF v_policy.check_expr IS NOT NULL THEN
                        v_sql := v_sql || ' WITH CHECK (' || v_policy.check_expr || ')';
                    END IF;
                    EXECUTE v_sql;
                END IF;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;

-- 3) 迁移内自检：子分区 RLS 全开、策略数与父表一致、无父表没有的增量策略
DO $$
DECLARE
    v_bad bigint;
    v_parent record;
    v_child record;
    v_parent_policies bigint;
    v_child_policies bigint;
BEGIN
    SELECT count(*) INTO v_bad
    FROM pg_inherits i
    JOIN pg_class p ON p.oid = i.inhparent
    JOIN pg_class c ON c.oid = i.inhrelid
    JOIN pg_namespace n ON n.oid = p.relnamespace
    WHERE n.nspname = 'fenglin_hub'
      AND p.relname IN ('hub_audit_log', 'hub_message', 'hub_api_call_log', 'hub_ai_metering', 'hub_metering')
      AND c.relrowsecurity = false;
    IF v_bad > 0 THEN
        RAISE EXCEPTION '自检失败：% 个子分区未开启 RLS', v_bad;
    END IF;

    FOR v_parent IN
        SELECT c.oid, c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'fenglin_hub'
          AND c.relname IN ('hub_audit_log', 'hub_message', 'hub_api_call_log', 'hub_ai_metering', 'hub_metering')
    LOOP
        SELECT count(*) INTO v_parent_policies FROM pg_policy WHERE polrelid = v_parent.oid;
        FOR v_child IN
            SELECT c.oid, c.relname
            FROM pg_inherits i
            JOIN pg_class c ON c.oid = i.inhrelid
            WHERE i.inhparent = v_parent.oid
        LOOP
            SELECT count(*) INTO v_child_policies FROM pg_policy WHERE polrelid = v_child.oid;
            IF v_child_policies <> v_parent_policies THEN
                RAISE EXCEPTION '自检失败：%.% 策略数 % 与父表 % 不一致',
                        v_parent.relname, v_child.relname, v_child_policies, v_parent_policies;
            END IF;
        END LOOP;
    END LOOP;

    -- 等价性自检：子分区每条策略都必须在父表有语义等价的对应策略（含 roles/qual/check 一致）
    SELECT count(*) INTO v_bad
    FROM pg_inherits i
    JOIN pg_class p ON p.oid = i.inhparent
    JOIN pg_namespace n ON n.oid = p.relnamespace
    JOIN pg_class c ON c.oid = i.inhrelid
    JOIN pg_policy cp ON cp.polrelid = c.oid
    WHERE n.nspname = 'fenglin_hub'
      AND p.relname IN ('hub_audit_log', 'hub_message', 'hub_api_call_log', 'hub_ai_metering', 'hub_metering')
      AND NOT EXISTS (SELECT 1 FROM pg_policy pp
                      WHERE pp.polrelid = p.oid
                        AND pp.polcmd = cp.polcmd
                        AND pp.polpermissive = cp.polpermissive
                        AND pp.polroles = cp.polroles
                        AND coalesce(pg_get_expr(pp.polqual, pp.polrelid), '')
                            = coalesce(pg_get_expr(cp.polqual, cp.polrelid), '')
                        AND coalesce(pg_get_expr(pp.polwithcheck, pp.polrelid), '')
                            = coalesce(pg_get_expr(cp.polwithcheck, cp.polrelid), ''));
    IF v_bad > 0 THEN
        RAISE EXCEPTION '自检失败：子分区存在 % 条父表没有的增量策略', v_bad;
    END IF;
END $$;
