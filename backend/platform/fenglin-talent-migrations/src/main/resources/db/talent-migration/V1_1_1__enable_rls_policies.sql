-- 全表启用 RLS、更新时间触发器与运行角色授权
-- 文档基线：03_丰麟云才_03 第 6.2 节
-- 与平台库一致：应用以 fl_talent_app 角色连接，事务内 SET LOCAL app.tenant_id 后才能读写；
-- 运行角色禁止 BYPASSRLS，平台级跨租户查询由独立受审计角色承担。
SET search_path TO fenglin_talent, public;

DO $$
DECLARE
    t text;
    tables text[] := ARRAY[
        'talent_org', 'talent_position', 'talent_grade', 'talent_headcount_plan',
        'talent_employee', 'talent_recruit_requisition', 'talent_candidate', 'talent_application',
        'talent_interview', 'talent_offer', 'talent_onboarding', 'talent_probation',
        'talent_transfer', 'talent_resignation', 'talent_training_course', 'talent_course_session',
        'talent_learning_record', 'talent_certificate', 'talent_knowledge_category', 'talent_knowledge_article',
        'talent_skill_tag', 'talent_employee_skill', 'talent_talent_pool', 'talent_talent_pool_member',
        'talent_talent_demand', 'talent_flow_application', 'talent_engagement_survey', 'talent_survey_response',
        'talent_attrition_risk', 'talent_intervention', 'talent_okr_period', 'talent_okr_objective',
        'talent_okr_key_result', 'talent_okr_alignment', 'talent_innovation_proposal', 'talent_innovation_review',
        'talent_tenant_key'
    ];
BEGIN
    FOREACH t IN ARRAY tables
    LOOP
        EXECUTE format('ALTER TABLE fenglin_talent.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE fenglin_talent.%I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS p_%s_tenant ON fenglin_talent.%I', t, t);
        EXECUTE format(
            'CREATE POLICY p_%s_tenant ON fenglin_talent.%I USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id())',
            t, t);

        EXECUTE format('DROP TRIGGER IF EXISTS trg_%s_updated ON fenglin_talent.%I', t, t);
        EXECUTE format(
            'CREATE TRIGGER trg_%s_updated BEFORE UPDATE ON fenglin_talent.%I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            t, t);
    END LOOP;
END
$$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA fenglin_talent TO fl_talent_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA fenglin_talent TO fl_talent_app;

COMMENT ON TABLE fenglin_talent.talent_employee IS '员工主数据（手机号/身份证号密文 + 租户盐哈希，禁止明文检索）';
COMMENT ON TABLE fenglin_talent.talent_candidate IS '候选人（手机号/身份证号密文 + 哈希，禁止明文检索）';
COMMENT ON TABLE fenglin_talent.talent_survey_response IS '问卷答卷（匿名问卷 employee_hash 为 NULL）';
COMMENT ON TABLE fenglin_talent.talent_tenant_key IS '租户数据密钥（DEK 密文 + 租户盐，销毁 DEK 即不可解密）';
