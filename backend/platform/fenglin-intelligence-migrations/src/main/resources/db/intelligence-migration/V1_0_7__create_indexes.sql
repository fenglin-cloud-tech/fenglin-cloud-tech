-- 索引：唯一 uuid、业务唯一键、高频查询与租户内名称模糊检索
SET search_path TO fenglin_intelligence, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables
        WHERE schemaname = 'fenglin_intelligence' AND tablename LIKE 'ai\_%' ESCAPE '\'
    LOOP
        EXECUTE format('CREATE UNIQUE INDEX %I ON fenglin_intelligence.%I (uuid)',
                       'uk_ai_uuid_' || t, t);
    END LOOP;
END
$$;

CREATE UNIQUE INDEX uk_kb_code ON ai_knowledge_base (tenant_id, code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_acl ON ai_knowledge_acl
    (tenant_id, knowledge_base_id, subject_type, COALESCE(subject_id, 0), permission) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_source_code ON ai_data_source (tenant_id, code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_cred_source ON ai_data_source_credential (tenant_id, data_source_id) WHERE deleted = 0;

CREATE INDEX idx_doc_kb ON ai_document (tenant_id, knowledge_base_id, index_status) WHERE deleted = 0;
CREATE INDEX idx_doc_checksum ON ai_document (tenant_id, checksum) WHERE deleted = 0;
CREATE INDEX idx_doc_title_trgm ON ai_document USING gin (title gin_trgm_ops);
CREATE INDEX idx_chunk_doc ON ai_document_chunk (document_id, seq_no) WHERE deleted = 0;
CREATE INDEX idx_chunk_kb ON ai_document_chunk (tenant_id, knowledge_base_id) WHERE deleted = 0;

CREATE UNIQUE INDEX uk_vec_collection ON ai_vector_index_meta (tenant_id, collection_name) WHERE deleted = 0;
CREATE INDEX idx_task_status ON ai_index_task (tenant_id, status, priority) WHERE deleted = 0;
CREATE INDEX idx_task_doc ON ai_index_task (document_id) WHERE deleted = 0;

CREATE INDEX idx_kg_ent_name ON ai_kg_entity (tenant_id, knowledge_base_id, normalized_name) WHERE deleted = 0;
CREATE INDEX idx_kg_rel_src ON ai_kg_relation (source_entity_id) WHERE deleted = 0;
CREATE INDEX idx_kg_rel_tgt ON ai_kg_relation (target_entity_id) WHERE deleted = 0;

CREATE INDEX idx_search_user_time ON ai_search_log (tenant_id, user_id, created_at DESC);

CREATE INDEX idx_conv_user ON ai_conversation (tenant_id, user_id, status) WHERE deleted = 0;
CREATE INDEX idx_msg_conv ON ai_message (conversation_id, created_at);
CREATE INDEX idx_cite_msg ON ai_message_citation (message_id);
CREATE UNIQUE INDEX uk_agent_code_ver ON ai_agent_definition (tenant_id, code, version_no) WHERE deleted = 0;
CREATE INDEX idx_agexec_agent ON ai_agent_execution (tenant_id, agent_id, status);

CREATE UNIQUE INDEX uk_metric_code ON ai_metric_definition (tenant_id, code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_dataset_code ON ai_dataset (tenant_id, code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_dash_code ON ai_dashboard (tenant_id, code) WHERE deleted = 0;
CREATE INDEX idx_insight_status ON ai_insight_task (tenant_id, status, trigger_type) WHERE deleted = 0;
CREATE INDEX idx_insight_result_task ON ai_insight_result (task_id, created_at DESC);
CREATE INDEX idx_attr_metric ON ai_attribution_analysis (tenant_id, metric_id);
CREATE INDEX idx_perm_subject ON ai_data_permission (tenant_id, subject_type, subject_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_insight_sub ON ai_insight_subscription (tenant_id, task_id, frequency) WHERE deleted = 0;

CREATE UNIQUE INDEX uk_tenant_key ON ai_tenant_key (tenant_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_idem ON ai_idempotency_record (tenant_id, idem_key, api_path) WHERE deleted = 0;
