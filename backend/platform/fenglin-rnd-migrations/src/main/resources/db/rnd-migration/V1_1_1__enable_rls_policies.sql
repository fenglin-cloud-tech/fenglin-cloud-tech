-- 云研行级隔离：所有业务表启用 RLS（FORCE），租户取自 app.tenant_id
-- 例外：devops_metric_config 允许读取 tenant_id=0 的平台内置口径，租户覆盖行仍严格隔离。
SET search_path TO fenglin_devops, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_devops'
        AND tablename NOT IN ('flyway_schema_history', 'devops_metric_config')
    LOOP
        EXECUTE format('ALTER TABLE fenglin_devops.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_devops.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON fenglin_devops.%I '
                       'USING (tenant_id = current_tenant_id()) '
                       'WITH CHECK (tenant_id = current_tenant_id())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_devops.%I TO fl_devops_app', t);
    END LOOP;
END
$$;

ALTER TABLE devops_metric_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE devops_metric_config FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON devops_metric_config
    USING (tenant_id = current_tenant_id() OR tenant_id = 0)
    WITH CHECK (tenant_id = current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON devops_metric_config TO fl_devops_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_devops TO fl_devops_app;
