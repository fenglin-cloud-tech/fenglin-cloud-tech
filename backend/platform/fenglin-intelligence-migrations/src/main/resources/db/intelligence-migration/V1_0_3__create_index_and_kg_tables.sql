-- H1 索引与图谱域：向量索引元数据、索引任务、知识图谱实体/关系、检索日志
SET search_path TO fenglin_intelligence, public;

CREATE TABLE ai_vector_index_meta (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    collection_name varchar(64) NOT NULL,
    embedding_model varchar(64) NOT NULL,
    dimension int NOT NULL,
    metric_type varchar(16) NOT NULL DEFAULT 'COSINE',
    index_type varchar(16) NOT NULL DEFAULT 'HNSW',
    index_params jsonb,
    vector_count bigint NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'BUILDING',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_vector_index_meta IS '向量索引元数据（不存储向量本体）';
COMMENT ON COLUMN ai_vector_index_meta.metric_type IS 'COSINE/IP/L2';
COMMENT ON COLUMN ai_vector_index_meta.index_type IS 'HNSW/IVF_FLAT';
COMMENT ON COLUMN ai_vector_index_meta.status IS 'BUILDING/READY/FAILED/REBUILDING';

CREATE TABLE ai_index_task (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    document_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    task_type varchar(16) NOT NULL DEFAULT 'FULL',
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    progress int NOT NULL DEFAULT 0,
    priority smallint NOT NULL DEFAULT 5,
    retry_count int NOT NULL DEFAULT 0,
    error_msg text,
    started_at timestamptz,
    finished_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_task_progress CHECK (progress BETWEEN 0 AND 100)
);
COMMENT ON TABLE ai_index_task IS '索引任务（解析/分块/向量化/入库）';
COMMENT ON COLUMN ai_index_task.task_type IS 'FULL/INCREMENT/REBUILD';
COMMENT ON COLUMN ai_index_task.status IS 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED';

CREATE TABLE ai_kg_entity (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    knowledge_base_id bigint NOT NULL,
    entity_type varchar(32) NOT NULL,
    name varchar(256) NOT NULL,
    normalized_name varchar(256) NOT NULL,
    aliases varchar(512),
    source_document_id bigint,
    confidence numeric(5,4) NOT NULL DEFAULT 1.0,
    properties jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_kg_ent_conf CHECK (confidence >= 0 AND confidence <= 1)
);
COMMENT ON TABLE ai_kg_entity IS '知识图谱实体';
COMMENT ON COLUMN ai_kg_entity.entity_type IS 'PERSON/ORG/PRODUCT/POLICY/PROJECT/CONCEPT';

CREATE TABLE ai_kg_relation (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    knowledge_base_id bigint NOT NULL,
    source_entity_id bigint NOT NULL,
    target_entity_id bigint NOT NULL,
    relation_type varchar(32) NOT NULL,
    weight numeric(8,4) NOT NULL DEFAULT 1.0,
    source_document_id bigint,
    confidence numeric(5,4) NOT NULL DEFAULT 1.0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_kg_rel_conf CHECK (confidence >= 0 AND confidence <= 1)
);
COMMENT ON TABLE ai_kg_relation IS '知识图谱关系';
COMMENT ON COLUMN ai_kg_relation.relation_type IS 'BELONGS_TO/RELATED_TO/CAUSES/DEPENDS_ON/OWNS';

CREATE TABLE ai_search_log (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    user_id bigint,
    query_text text NOT NULL,
    query_type varchar(16) NOT NULL DEFAULT 'HYBRID',
    knowledge_base_ids bigint[],
    result_count int NOT NULL DEFAULT 0,
    top_score numeric(8,4),
    latency_ms int NOT NULL DEFAULT 0,
    clicked_doc_ids bigint[],
    client_type varchar(8) NOT NULL DEFAULT 'PORTAL',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_search_log IS '检索日志（不可删，用于召回评估与审计）';
COMMENT ON COLUMN ai_search_log.query_type IS 'KEYWORD/VECTOR/HYBRID';
COMMENT ON COLUMN ai_search_log.client_type IS 'PORTAL/EMBED/OPENAPI';
