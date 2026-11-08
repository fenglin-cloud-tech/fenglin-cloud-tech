-- 云财行级隔离：所有业务表启用 RLS（FORCE），租户取自 app.tenant_id
SET search_path TO fenglin_finance, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_finance'
        AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE fenglin_finance.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_finance.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON fenglin_finance.%I '
                       'USING (tenant_id = current_tenant_id()) '
                       'WITH CHECK (tenant_id = current_tenant_id())', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_finance.%I TO fl_finance_app', t);
    END LOOP;
END
$$;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_finance TO fl_finance_app;
