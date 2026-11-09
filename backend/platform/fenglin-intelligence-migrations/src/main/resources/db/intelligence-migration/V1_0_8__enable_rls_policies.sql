-- 云智行级隔离：29 张表启用 RLS（FORCE），租户取自 app.tenant_id。
-- 策略命名：p_ai_{table}_tenant。
-- 例外：ai_metric_definition 允许读取 tenant_id=0 的平台内置口径，租户自有行仍严格隔离；
--       写入一律要求 tenant_id = current_tenant_id()，租户不能伪造平台行。
SET search_path TO fenglin_intelligence, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables
        WHERE schemaname = 'fenglin_intelligence'
          AND tablename LIKE 'ai\_%' ESCAPE '\'
          AND tablename <> 'ai_metric_definition'
    LOOP
        EXECUTE format('ALTER TABLE fenglin_intelligence.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_intelligence.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON fenglin_intelligence.%I '
                       'USING (tenant_id = current_tenant_id()) '
                       'WITH CHECK (tenant_id = current_tenant_id())',
                       'p_ai_' || t || '_tenant', t);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON fenglin_intelligence.%I TO fl_intelligence_app', t);
    END LOOP;
END
$$;

ALTER TABLE ai_metric_definition ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_metric_definition FORCE ROW LEVEL SECURITY;
CREATE POLICY p_ai_metric_definition_tenant ON ai_metric_definition
    USING (tenant_id = current_tenant_id() OR tenant_id = 0)
    WITH CHECK (tenant_id = current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON ai_metric_definition TO fl_intelligence_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_intelligence TO fl_intelligence_app;
