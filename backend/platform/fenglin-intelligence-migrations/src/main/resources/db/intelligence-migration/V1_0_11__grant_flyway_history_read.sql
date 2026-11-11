-- 方案A：补齐应用角色对 flyway_schema_history 的只读权限（写入仍由 Flyway 管理账号完成）
SET search_path TO fenglin_intelligence, public;

DO $$
BEGIN
    IF to_regclass('fenglin_intelligence.flyway_schema_history') IS NOT NULL THEN
        EXECUTE 'GRANT SELECT ON fenglin_intelligence.flyway_schema_history TO fl_intelligence_app';
    END IF;
END
$$;
