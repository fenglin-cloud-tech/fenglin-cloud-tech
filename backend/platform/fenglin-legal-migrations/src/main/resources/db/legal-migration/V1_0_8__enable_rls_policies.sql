-- 云法行级隔离：27 张业务表 + 2 张兜底表启用 RLS（FORCE），租户取自 app.tenant_id。
-- 策略命名按文档：p_legal_{table}_tenant；写入与读取均要求 tenant_id = current_tenant_id()。
-- 说明：本域无平台级只读种子（文档未定义），故不设 tenant_id=0 例外；
--       平台运营只读角色的跨租户策略（legal_platform_ro）属部署期授权，不在本脚本内创建。
SET search_path TO fenglin_legal, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables
        WHERE schemaname = 'fenglin_legal'
          AND tablename LIKE 'legal\_%' ESCAPE '\'
          AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE fenglin_legal.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_legal.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON fenglin_legal.%I '
                       'USING (tenant_id = current_tenant_id()) '
                       'WITH CHECK (tenant_id = current_tenant_id())',
                       'p_legal_' || t || '_tenant', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_legal.%I TO fl_legal_app', t);
    END LOOP;
END
$$;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_legal TO fl_legal_app;
