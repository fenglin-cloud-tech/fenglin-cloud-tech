-- 文档域：文档、版本、协作者、评论
-- 扩展：collab_document_version 增加 content_cipher（版本正文快照密文）以支持 API-DOC-007 回滚；
--       数据库文档仅定义 snapshot_file_id，无内容快照则回滚无法实现，此处按最小语义补齐并标注。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_document (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    title varchar(256) NOT NULL,
    doc_type varchar(16) NOT NULL DEFAULT 'RICH',
    owner_id bigint NOT NULL,
    folder_id uuid,
    content_cipher text,
    content_hash varchar(64),
    current_version int NOT NULL DEFAULT 1,
    secret_level varchar(16) NOT NULL DEFAULT 'INTERNAL',
    watermark_enabled smallint NOT NULL DEFAULT 0,
    share_scope varchar(16) NOT NULL DEFAULT 'PRIVATE',
    link_token varchar(64),
    link_expire_at timestamptz,
    tags varchar(64)[],
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_doc_type CHECK (doc_type IN ('RICH', 'SHEET', 'KANBAN')),
    CONSTRAINT ck_doc_secret CHECK (secret_level IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL')),
    CONSTRAINT ck_doc_scope CHECK (share_scope IN ('PRIVATE', 'MEMBER', 'DEPT', 'LINK')),
    CONSTRAINT ck_doc_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE TABLE collab_document_version (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    document_id uuid NOT NULL REFERENCES collab_document (id),
    version_no int NOT NULL,
    snapshot_file_id uuid,
    content_cipher text,
    ops_count int NOT NULL DEFAULT 0,
    editor_id bigint,
    version_type varchar(16) NOT NULL DEFAULT 'AUTO',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_doc_version_type CHECK (version_type IN ('AUTO', 'MANUAL')),
    CONSTRAINT uk_doc_version UNIQUE (tenant_id, document_id, version_no)
);

CREATE TABLE collab_document_collaborator (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    document_id uuid NOT NULL REFERENCES collab_document (id),
    subject_type varchar(16) NOT NULL DEFAULT 'USER',
    subject_id bigint NOT NULL,
    permission varchar(16) NOT NULL DEFAULT 'READ',
    granted_by bigint,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_doc_collab_subject CHECK (subject_type IN ('USER', 'DEPT', 'ROLE')),
    CONSTRAINT ck_doc_collab_permission CHECK (permission IN ('READ', 'COMMENT', 'EDIT', 'MANAGE'))
);

CREATE TABLE collab_document_comment (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    document_id uuid NOT NULL REFERENCES collab_document (id),
    parent_id bigint,
    author_id bigint NOT NULL,
    content_cipher text NOT NULL,
    anchor jsonb,
    mention_ids bigint[],
    resolved smallint NOT NULL DEFAULT 0,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON COLUMN collab_document_version.content_cipher IS '扩展：版本正文快照密文，用于版本回滚（文档未定义）';
