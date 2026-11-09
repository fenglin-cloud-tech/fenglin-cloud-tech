-- 云研索引与触发器：租户索引统一生成，业务唯一键按 deleted=0 建部分唯一索引
SET search_path TO fenglin_devops, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_devops'
        AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_tenant ON fenglin_devops.%I (tenant_id)', t, t);
    END LOOP;
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_devops'
        AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('CREATE TRIGGER trg_%s_updated BEFORE UPDATE ON fenglin_devops.%I '
                       'FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
    END LOOP;
END
$$;

CREATE UNIQUE INDEX uk_product_code ON devops_product (tenant_id, product_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_product_member ON devops_product_member (tenant_id, product_id, user_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_req_status_code ON devops_requirement_status (tenant_id, product_id, status_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_req_no ON devops_requirement (tenant_id, req_no) WHERE deleted = 0;
CREATE INDEX idx_req_product_status ON devops_requirement (tenant_id, product_id, status_id);
CREATE INDEX idx_req_sprint ON devops_requirement (tenant_id, sprint_id);
CREATE UNIQUE INDEX uk_req_relation ON devops_requirement_relation
    (tenant_id, src_requirement_id, dst_requirement_id, relation_type) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_sprint_no ON devops_sprint (tenant_id, sprint_no) WHERE deleted = 0;
CREATE INDEX idx_sprint_product_status ON devops_sprint (tenant_id, product_id, status);
CREATE UNIQUE INDEX uk_sprint_member ON devops_sprint_member (tenant_id, sprint_id, user_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_task_no ON devops_task (tenant_id, task_no) WHERE deleted = 0;
CREATE INDEX idx_task_sprint_assignee ON devops_task (tenant_id, sprint_id, assignee_id);
CREATE INDEX idx_task_requirement ON devops_task (tenant_id, requirement_id);
CREATE INDEX idx_worklog_user_date ON devops_task_worklog (tenant_id, user_id, work_date);
CREATE INDEX idx_worklog_task ON devops_task_worklog (tenant_id, task_id);
CREATE UNIQUE INDEX uk_defect_no ON devops_defect (tenant_id, defect_no) WHERE deleted = 0;
CREATE INDEX idx_defect_product_status ON devops_defect (tenant_id, product_id, status);
CREATE INDEX idx_defect_found_stage ON devops_defect (tenant_id, found_stage, created_at);
CREATE INDEX idx_board_product ON devops_board (tenant_id, product_id);
CREATE INDEX idx_board_column_board ON devops_board_column (tenant_id, board_id, sort_no);
CREATE UNIQUE INDEX uk_case_no ON devops_test_case (tenant_id, case_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_plan_no ON devops_test_plan (tenant_id, plan_no) WHERE deleted = 0;
CREATE INDEX idx_plan_product ON devops_test_plan (tenant_id, product_id, sprint_id);
CREATE INDEX idx_execution_plan ON devops_test_execution (tenant_id, plan_id);
CREATE INDEX idx_tei_case ON devops_test_execution_item (tenant_id, case_id);
CREATE INDEX idx_tei_execution ON devops_test_execution_item (tenant_id, execution_id);
CREATE UNIQUE INDEX uk_repo_name ON devops_repo (tenant_id, product_id, repo_name) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_commit_sha ON devops_commit (tenant_id, repo_id, commit_sha) WHERE deleted = 0;
CREATE INDEX idx_commit_repo_time ON devops_commit (tenant_id, repo_id, committed_at);
CREATE INDEX idx_link_biz ON devops_commit_link (tenant_id, biz_type, biz_id);
CREATE UNIQUE INDEX uk_commit_link ON devops_commit_link (tenant_id, commit_id, biz_type, biz_id) WHERE deleted = 0;
CREATE INDEX idx_pipeline_product ON devops_pipeline (tenant_id, product_id, status);
CREATE INDEX idx_run_pipeline_status ON devops_pipeline_run (tenant_id, pipeline_id, status);
CREATE UNIQUE INDEX uk_run_no ON devops_pipeline_run (tenant_id, run_no) WHERE deleted = 0;
CREATE INDEX idx_build_run ON devops_build (tenant_id, run_id);
CREATE UNIQUE INDEX uk_build_no ON devops_build (tenant_id, build_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_release_no ON devops_release (tenant_id, release_no) WHERE deleted = 0;
CREATE INDEX idx_release_product_env ON devops_release (tenant_id, product_id, env, status);
CREATE INDEX idx_deploy_release ON devops_deployment (tenant_id, release_id);
CREATE UNIQUE INDEX uk_event_id ON devops_webhook_event (source, event_id);
CREATE INDEX idx_event_status_time ON devops_webhook_event (process_status, created_at);
CREATE UNIQUE INDEX uk_metric_config ON devops_metric_config (tenant_id, metric_code, caliber_version) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_snapshot ON devops_metric_snapshot
    (tenant_id, metric_code, scope_type, scope_id, period_type, period_key, caliber_version) WHERE deleted = 0;
CREATE INDEX idx_snapshot_scope_period ON devops_metric_snapshot
    (tenant_id, scope_type, scope_id, period_type, period_key);
CREATE UNIQUE INDEX uk_contribution ON devops_contribution
    (tenant_id, user_id, product_id, period_type, period_key) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_dora ON devops_dora_metric (tenant_id, product_id, period_type, period_key) WHERE deleted = 0;
CREATE INDEX idx_dashboard_owner ON devops_insight_dashboard (tenant_id, owner_id);
CREATE INDEX idx_alert_metric ON devops_metric_alert (tenant_id, metric_code, status);
CREATE INDEX idx_attachment_biz ON devops_attachment (tenant_id, biz_type, biz_id);
CREATE UNIQUE INDEX uk_idem ON devops_idempotency_record (tenant_id, idem_key, api_path);
CREATE UNIQUE INDEX uk_tenant_key ON devops_tenant_key (tenant_id) WHERE deleted = 0;
