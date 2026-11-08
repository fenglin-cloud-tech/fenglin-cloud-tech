-- 业务索引与唯一约束
-- 文档基线：03_丰麟云才_03 第 5 节；唯一约束统一使用部分唯一索引（WHERE deleted = 0），
-- 逻辑删除后允许重建同名实体；PII 仅对 *_hash 建唯一索引，禁止明文列索引（P5/P6）。
SET search_path TO fenglin_talent, public;

-- ---------- 5 节索引 ----------
CREATE INDEX IF NOT EXISTS idx_org_parent ON talent_org (tenant_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_org_path ON talent_org (tenant_id, path varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_emp_org ON talent_employee (tenant_id, org_id);
CREATE INDEX IF NOT EXISTS idx_emp_status ON talent_employee (tenant_id, emp_status);
CREATE INDEX IF NOT EXISTS idx_emp_manager ON talent_employee (tenant_id, manager_id);
CREATE INDEX IF NOT EXISTS idx_cand_status ON talent_candidate (tenant_id, cand_status);
CREATE INDEX IF NOT EXISTS idx_intv_app ON talent_interview (application_id);
CREATE INDEX IF NOT EXISTS idx_intv_time ON talent_interview (tenant_id, scheduled_at);
CREATE INDEX IF NOT EXISTS idx_lr_emp ON talent_learning_record (tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_lr_course ON talent_learning_record (tenant_id, course_id);
CREATE INDEX IF NOT EXISTS idx_skill_skill ON talent_employee_skill (tenant_id, skill_id);
CREATE INDEX IF NOT EXISTS idx_flow_emp ON talent_flow_application (tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_sr_survey ON talent_survey_response (tenant_id, survey_id);
CREATE INDEX IF NOT EXISTS idx_risk_emp ON talent_attrition_risk (tenant_id, employee_id, scored_at);
CREATE INDEX IF NOT EXISTS idx_risk_level ON talent_attrition_risk (tenant_id, risk_level);
CREATE INDEX IF NOT EXISTS idx_obj_period ON talent_okr_objective (tenant_id, period_id);
CREATE INDEX IF NOT EXISTS idx_obj_owner ON talent_okr_objective (tenant_id, owner_id);
CREATE INDEX IF NOT EXISTS idx_align_child ON talent_okr_alignment (child_objective_id);
CREATE INDEX IF NOT EXISTS idx_align_parent ON talent_okr_alignment (parent_objective_id);
CREATE INDEX IF NOT EXISTS idx_prop_status ON talent_innovation_proposal (tenant_id, prop_status);

-- ---------- 补充常用查询索引 ----------
CREATE INDEX IF NOT EXISTS idx_org_leader ON talent_org (tenant_id, leader_id);
CREATE INDEX IF NOT EXISTS idx_position_org ON talent_position (tenant_id, org_id);
CREATE INDEX IF NOT EXISTS idx_hc_plan_org ON talent_headcount_plan (tenant_id, plan_year, org_id);
CREATE INDEX IF NOT EXISTS idx_req_status ON talent_recruit_requisition (tenant_id, req_status);
CREATE INDEX IF NOT EXISTS idx_req_org ON talent_recruit_requisition (tenant_id, org_id, owner_id);
CREATE INDEX IF NOT EXISTS idx_app_candidate ON talent_application (tenant_id, candidate_id);
CREATE INDEX IF NOT EXISTS idx_app_req ON talent_application (tenant_id, requisition_id, stage);
CREATE INDEX IF NOT EXISTS idx_intv_interviewer ON talent_interview (tenant_id, interviewer_id, scheduled_at DESC);
CREATE INDEX IF NOT EXISTS idx_offer_candidate ON talent_offer (tenant_id, candidate_id);
CREATE INDEX IF NOT EXISTS idx_offer_status ON talent_offer (tenant_id, offer_status);
CREATE INDEX IF NOT EXISTS idx_onboard_emp ON talent_onboarding (tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_probation_emp ON talent_probation (tenant_id, employee_id, prob_status);
CREATE INDEX IF NOT EXISTS idx_transfer_emp ON talent_transfer (tenant_id, employee_id, effective_date DESC);
CREATE INDEX IF NOT EXISTS idx_resignation_emp ON talent_resignation (tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_course_status ON talent_training_course (tenant_id, course_status);
CREATE INDEX IF NOT EXISTS idx_session_course ON talent_course_session (tenant_id, course_id, start_at);
CREATE INDEX IF NOT EXISTS idx_certificate_emp ON talent_certificate (tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_article_cat ON talent_knowledge_article (tenant_id, category_id, art_status);
CREATE INDEX IF NOT EXISTS idx_skill_tag_cat ON talent_skill_tag (tenant_id, category);
CREATE INDEX IF NOT EXISTS idx_pool_member_pool ON talent_talent_pool_member (tenant_id, pool_id);
CREATE INDEX IF NOT EXISTS idx_pool_member_emp ON talent_talent_pool_member (tenant_id, employee_id);
CREATE INDEX IF NOT EXISTS idx_demand_status ON talent_talent_demand (tenant_id, dm_status);
CREATE INDEX IF NOT EXISTS idx_flow_status ON talent_flow_application (tenant_id, flow_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_survey_status ON talent_engagement_survey (tenant_id, survey_status, start_at DESC);
CREATE INDEX IF NOT EXISTS idx_intervention_emp ON talent_intervention (tenant_id, employee_id, iv_status);
CREATE INDEX IF NOT EXISTS idx_kr_objective ON talent_okr_key_result (tenant_id, objective_id);
CREATE INDEX IF NOT EXISTS idx_review_proposal ON talent_innovation_review (tenant_id, proposal_id);

-- ---------- 部分唯一索引 ----------
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_code ON talent_org (tenant_id, org_code) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_no ON talent_employee (tenant_id, emp_no) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_phone_hash ON talent_employee (tenant_id, phone_hash)
    WHERE deleted = 0 AND phone_hash IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_idcard_hash ON talent_employee (tenant_id, id_card_hash)
    WHERE deleted = 0 AND id_card_hash IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cand_phone_hash ON talent_candidate (tenant_id, phone_hash)
    WHERE deleted = 0 AND phone_hash IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_req_no ON talent_recruit_requisition (tenant_id, req_no) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_skill ON talent_employee_skill (tenant_id, employee_id, skill_id) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_pool_name ON talent_talent_pool (tenant_id, pool_name) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_skill_tag_name ON talent_skill_tag (tenant_id, name) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_course_code ON talent_training_course (tenant_id, course_code) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_demand_no ON talent_talent_demand (tenant_id, demand_no) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_survey_no ON talent_engagement_survey (tenant_id, survey_no) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_proposal_no ON talent_innovation_proposal (tenant_id, proposal_no) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cert_no ON talent_certificate (tenant_id, cert_no) WHERE deleted = 0;
