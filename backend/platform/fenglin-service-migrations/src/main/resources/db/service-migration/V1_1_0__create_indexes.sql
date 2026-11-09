-- 云服索引与触发器：租户索引统一生成，业务唯一键按 deleted=0 建部分唯一索引
SET search_path TO fenglin_service, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_service'
        AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_tenant ON fenglin_service.%I (tenant_id)', t, t);
    END LOOP;
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_service'
        AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('CREATE TRIGGER trg_%s_updated BEFORE UPDATE ON fenglin_service.%I '
                       'FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
    END LOOP;
END
$$;

-- 文档 §5 关键业务索引
CREATE UNIQUE INDEX uk_ticket_no ON svc_ticket (tenant_id, ticket_no) WHERE deleted = 0;
CREATE INDEX idx_ticket_tenant_status ON svc_ticket (tenant_id, status, priority);
CREATE INDEX idx_ticket_assignee ON svc_ticket (tenant_id, assignee_id, status);
CREATE INDEX idx_ticket_requester ON svc_ticket (tenant_id, requester_id, created_at DESC);
CREATE INDEX idx_ticket_catalog_item ON svc_ticket (tenant_id, catalog_item_id, created_at DESC);
CREATE INDEX idx_ticket_sla_due ON svc_ticket (tenant_id, sla_resolve_due_at);
CREATE INDEX idx_tfl_ticket_time ON svc_ticket_flow_log (tenant_id, ticket_id, operated_at DESC);
CREATE INDEX idx_tc_ticket ON svc_ticket_comment (tenant_id, ticket_id, created_at DESC);
CREATE UNIQUE INDEX uk_ticket_rel ON svc_ticket_relation (tenant_id, ticket_id, rel_type, rel_id) WHERE deleted = 0;

CREATE UNIQUE INDEX uk_ci_code ON svc_catalog_item (tenant_id, code) WHERE deleted = 0;
CREATE INDEX idx_ci_catalog ON svc_catalog_item (tenant_id, catalog_id, status);
CREATE INDEX idx_slat_state_due ON svc_sla_timer (tenant_id, resolve_state, resolve_due_at);
CREATE UNIQUE INDEX uk_sla_timer_ticket ON svc_sla_timer (tenant_id, ticket_id) WHERE deleted = 0;
CREATE INDEX idx_slab_ticket ON svc_sla_breach (tenant_id, ticket_id, breach_type);

CREATE INDEX idx_kb_cat_status ON svc_kb_article (tenant_id, category_id, status);
CREATE INDEX idx_kb_es ON svc_kb_article (tenant_id, es_doc_id);
CREATE UNIQUE INDEX uk_kb_title_ver ON svc_kb_article (tenant_id, title, version_no) WHERE deleted = 0;
CREATE INDEX idx_csat_score ON svc_csat_survey (tenant_id, score, submitted_at DESC);
CREATE UNIQUE INDEX uk_csat_ticket ON svc_csat_survey (tenant_id, ticket_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_asset_no ON svc_it_asset (tenant_id, asset_no) WHERE deleted = 0;
CREATE INDEX idx_asset_owner ON svc_it_asset (tenant_id, owner_id, status);
CREATE UNIQUE INDEX uk_problem_no ON svc_problem (tenant_id, problem_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_change_no ON svc_change_request (tenant_id, change_no) WHERE deleted = 0;

CREATE UNIQUE INDEX uk_app_code ON svc_app_definition (tenant_id, app_code) WHERE deleted = 0;
CREATE INDEX idx_app_owner ON svc_app_definition (tenant_id, owner_id, status);
CREATE UNIQUE INDEX uk_form_key ON svc_form_definition (tenant_id, app_id, form_key, version_no) WHERE deleted = 0;
CREATE INDEX idx_form_app ON svc_form_definition (tenant_id, app_id, status);
CREATE UNIQUE INDEX uk_form_field ON svc_form_field (tenant_id, form_id, field_key) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_flow_key ON svc_flow_definition (tenant_id, app_id, flow_key, version_no) WHERE deleted = 0;
CREATE INDEX idx_flow_app ON svc_flow_definition (tenant_id, app_id, status);
CREATE INDEX idx_fi_biz ON svc_flow_instance (tenant_id, business_key);
CREATE INDEX idx_fi_init_status ON svc_flow_instance (tenant_id, initiator_id, status);
CREATE UNIQUE INDEX uk_ft_idem ON svc_flow_task (tenant_id, instance_id, task_key, assignee_id, status) WHERE deleted = 0;
CREATE INDEX idx_ft_assignee_status ON svc_flow_task (tenant_id, assignee_id, status, created_at_task DESC);
CREATE INDEX idx_ft_instance ON svc_flow_task (tenant_id, instance_id, status);
CREATE UNIQUE INDEX uk_model_key ON svc_data_model (tenant_id, app_id, model_key, version_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_model_field ON svc_data_model_field (tenant_id, model_id, field_key) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_page_key ON svc_page_schema (tenant_id, app_id, page_key, version_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_release_no ON svc_app_release (tenant_id, release_no) WHERE deleted = 0;

CREATE INDEX idx_mel_case ON svc_mining_event_log (tenant_id, source_system, case_id, event_time);
CREATE INDEX idx_mel_activity ON svc_mining_event_log (tenant_id, activity_code, event_time);
CREATE INDEX idx_mr_type_period ON svc_mining_result (tenant_id, result_type, period_end DESC);

CREATE UNIQUE INDEX uk_robot_code ON svc_robot (tenant_id, robot_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_automation_flow_code ON svc_automation_flow (tenant_id, flow_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_rt_no ON svc_robot_task (tenant_id, task_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_rt_idem ON svc_robot_task (tenant_id, idempotency_key) WHERE deleted = 0 AND idempotency_key IS NOT NULL;
CREATE INDEX idx_rt_status_prio ON svc_robot_task (tenant_id, status, priority, created_at);
CREATE INDEX idx_rt_robot ON svc_robot_task (tenant_id, robot_id, status);
CREATE INDEX idx_rt_biz ON svc_robot_task (tenant_id, business_key);
CREATE INDEX idx_rtl_task ON svc_robot_task_log (tenant_id, task_id, attempt, logged_at);

CREATE UNIQUE INDEX uk_tag ON svc_tag (tenant_id, biz_scope, name) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_tag_rel ON svc_tag_relation (tenant_id, tag_id, biz_type, biz_id) WHERE deleted = 0;
CREATE INDEX idx_attach_biz ON svc_attachment (tenant_id, biz_type, biz_id);
CREATE UNIQUE INDEX uk_tenant_key ON svc_tenant_key (tenant_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_idem ON svc_idempotency_record (tenant_id, idem_key, api_path);
