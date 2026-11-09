-- H1 知识域：知识库、ACL、数据源、数据源凭据、文档、文档分块
SET search_path TO fenglin_intelligence, public;

CREATE TABLE ai_knowledge_base (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    code varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    description text,
    scope_type varchar(16) NOT NULL DEFAULT 'TENANT',
    default_sec_level smallint NOT NULL DEFAULT 2,
    embedding_model varchar(64) NOT NULL DEFAULT 'bge-large-zh',
    vector_collection varchar(64) NOT NULL,
    es_index varchar(64) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_kb_sec_level CHECK (default_sec_level BETWEEN 1 AND 4)
);
COMMENT ON TABLE ai_knowledge_base IS '知识库';
COMMENT ON COLUMN ai_knowledge_base.scope_type IS '可见范围：TENANT/DEPT/CUSTOM/PRIVATE';
COMMENT ON COLUMN ai_knowledge_base.default_sec_level IS '默认密级：1 公开/2 内部/3 秘密/4 机密';
COMMENT ON COLUMN ai_knowledge_base.status IS '状态：ENABLED/DISABLED';

CREATE TABLE ai_knowledge_acl (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    knowledge_base_id bigint NOT NULL,
    subject_type varchar(16) NOT NULL,
    subject_id bigint,
    permission varchar(16) NOT NULL DEFAULT 'READ',
    sec_level_max smallint NOT NULL DEFAULT 2,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_acl_sec_level CHECK (sec_level_max BETWEEN 1 AND 4)
);
COMMENT ON TABLE ai_knowledge_acl IS '知识库访问控制';
COMMENT ON COLUMN ai_knowledge_acl.subject_type IS '主体类型：USER/ROLE/DEPT/ALL';
COMMENT ON COLUMN ai_knowledge_acl.permission IS '权限：READ/WRITE/ADMIN';

CREATE TABLE ai_data_source (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    code varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    category varchar(24) NOT NULL,
    connector_type varchar(32) NOT NULL,
    endpoint varchar(512),
    config_json jsonb,
    sync_mode varchar(16) NOT NULL DEFAULT 'FULL',
    sync_cron varchar(32),
    status varchar(16) NOT NULL DEFAULT 'ENABLED',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_data_source IS '数据源（知识源与 BI 源）';
COMMENT ON COLUMN ai_data_source.category IS 'FILE/OBJECT_STORAGE/CLOUD_DOC/DB/HTTP/APP';
COMMENT ON COLUMN ai_data_source.sync_mode IS 'FULL/INCREMENT/CDC';
COMMENT ON COLUMN ai_data_source.status IS 'ENABLED/DISABLED/ERROR';

CREATE TABLE ai_data_source_credential (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    data_source_id bigint NOT NULL,
    credential_cipher text NOT NULL,
    credential_hash varchar(64) NOT NULL,
    key_version int NOT NULL DEFAULT 1,
    last_rotated_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE ai_data_source_credential IS '数据源凭据（AES-256-GCM 密文，独立成表）';
COMMENT ON COLUMN ai_data_source_credential.credential_cipher IS '凭据密文 AES-256-GCM（含 IV/Tag）';
COMMENT ON COLUMN ai_data_source_credential.credential_hash IS '凭据指纹 SHA-256+租户盐';

CREATE TABLE ai_document (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    knowledge_base_id bigint NOT NULL,
    data_source_id bigint,
    title varchar(512) NOT NULL,
    source_uri varchar(1024),
    file_id varchar(64),
    doc_type varchar(24) NOT NULL DEFAULT 'TXT',
    size_bytes bigint,
    sec_level smallint NOT NULL DEFAULT 2,
    owner_dept_id bigint,
    tags varchar(512),
    review_status varchar(16) NOT NULL DEFAULT 'DRAFT',
    index_status varchar(16) NOT NULL DEFAULT 'PENDING',
    chunk_count int NOT NULL DEFAULT 0,
    checksum varchar(64),
    published_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_doc_sec_level CHECK (sec_level BETWEEN 1 AND 4)
);
COMMENT ON TABLE ai_document IS '知识文档';
COMMENT ON COLUMN ai_document.doc_type IS 'PDF/WORD/EXCEL/PPT/TXT/MD/WEB';
COMMENT ON COLUMN ai_document.review_status IS 'DRAFT/PENDING/APPROVED/REJECTED';
COMMENT ON COLUMN ai_document.index_status IS 'PENDING/PARSING/CHUNKING/EMBEDDING/INDEXING/INDEXED/FAILED';

CREATE TABLE ai_document_chunk (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    host_product varchar(32),
    document_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    seq_no int NOT NULL,
    content text NOT NULL,
    token_count int NOT NULL DEFAULT 0,
    page_no int,
    section_path varchar(512),
    char_offset int,
    vector_id varchar(64),
    es_doc_id varchar(64),
    sec_level smallint NOT NULL DEFAULT 2,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_chunk_sec_level CHECK (sec_level BETWEEN 1 AND 4)
);
COMMENT ON TABLE ai_document_chunk IS '文档分块（检索/引用最小单元；PG 为权威源，ES/向量为派生）';
COMMENT ON COLUMN ai_document_chunk.vector_id IS '向量库主键（与 id 对齐）';
COMMENT ON COLUMN ai_document_chunk.es_doc_id IS 'ES 文档 ID（与 id 对齐）';
