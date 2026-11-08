-- 租户行级隔离（RLS）与运行角色授权
-- 与平台库一致：应用以 fl_crm_app 角色连接，事务内 SET LOCAL app.tenant_id 后才能读写
SET search_path TO fenglin_crm, public;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'crm_lead', 'crm_customer', 'crm_contact', 'crm_opportunity', 'crm_opportunity_stage_log',
        'crm_activity', 'crm_quotation', 'crm_quotation_item', 'crm_service_contract', 'crm_payment_plan'
    ]
    LOOP
        EXECUTE format('ALTER TABLE fenglin_crm.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_crm.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS p_%s_tenant ON fenglin_crm.%I', t, t);
        EXECUTE format(
            'CREATE POLICY p_%s_tenant ON fenglin_crm.%I USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id())',
            t, t);
    END LOOP;
END
$$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA fenglin_crm TO fl_crm_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_crm TO fl_crm_app;

COMMENT ON TABLE fenglin_crm.crm_lead IS '线索主表（手机号密文 + 租户盐哈希，禁止明文唯一索引）';
COMMENT ON TABLE fenglin_crm.crm_customer IS '客户主档';
COMMENT ON TABLE fenglin_crm.crm_contact IS '联系人';
COMMENT ON TABLE fenglin_crm.crm_opportunity IS '商机';
COMMENT ON TABLE fenglin_crm.crm_opportunity_stage_log IS '商机阶段流转日志';
COMMENT ON TABLE fenglin_crm.crm_activity IS '销售活动/跟进记录';
COMMENT ON TABLE fenglin_crm.crm_quotation IS '报价单';
COMMENT ON TABLE fenglin_crm.crm_quotation_item IS '报价单明细';
COMMENT ON TABLE fenglin_crm.crm_service_contract IS '服务合同';
COMMENT ON TABLE fenglin_crm.crm_payment_plan IS '扩展：合同回款计划（总纲未定义，按最小可用语义实现）';
