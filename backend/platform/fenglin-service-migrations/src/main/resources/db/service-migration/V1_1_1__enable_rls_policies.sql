-- 云服行级隔离：所有业务表启用 RLS（FORCE），租户取自 app.tenant_id
-- 例外：svc_catalog / svc_catalog_item / svc_sla_policy 允许读取 tenant_id=0 的平台默认目录与 SLA 模板，
--       租户自建行仍严格隔离；写入一律要求 tenant_id = current_tenant_id()。
SET search_path TO fenglin_service, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_service'
        AND tablename NOT IN ('flyway_schema_history', 'svc_catalog', 'svc_catalog_item', 'svc_sla_policy')
    LOOP
        EXECUTE format('ALTER TABLE fenglin_service.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_service.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON fenglin_service.%I '
                       'USING (tenant_id = current_tenant_id()) '
                       'WITH CHECK (tenant_id = current_tenant_id())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_service.%I TO fl_service_app', t);
    END LOOP;
END
$$;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['svc_catalog', 'svc_catalog_item', 'svc_sla_policy']
    LOOP
        EXECUTE format('ALTER TABLE fenglin_service.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_service.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON fenglin_service.%I '
                       'USING (tenant_id = current_tenant_id() OR tenant_id = 0) '
                       'WITH CHECK (tenant_id = current_tenant_id())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_service.%I TO fl_service_app', t);
    END LOOP;
END
$$;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_service TO fl_service_app;
