-- F1：知识库、满意度、IT 资产
SET search_path TO fenglin_service, public;

CREATE TABLE svc_kb_category (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    parent_id bigint,
    sort_no int NOT NULL DEFAULT 0,
    visible_scope jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_kb_category IS '知识分类';

CREATE TABLE svc_kb_article (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    category_id bigint,
    title varchar(255) NOT NULL,
    summary varchar(512),
    content text NOT NULL,
    keywords jsonb,
    version_no int NOT NULL DEFAULT 1,
    visibility varchar(16) NOT NULL DEFAULT 'PUBLIC',
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    author_id bigint,
    reviewer_id bigint,
    published_at timestamptz,
    view_count bigint NOT NULL DEFAULT 0,
    helpful_count bigint NOT NULL DEFAULT 0,
    es_doc_id varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_kb_article IS '知识文章';
COMMENT ON COLUMN svc_kb_article.visibility IS '可见性：PUBLIC/DEPT/ROLE/PRIVATE';
COMMENT ON COLUMN svc_kb_article.status IS '状态：DRAFT/REVIEW/PUBLISHED/ARCHIVED';

CREATE TABLE svc_kb_feedback (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    article_id bigint NOT NULL,
    user_id bigint NOT NULL,
    helpful boolean NOT NULL,
    reason varchar(255),
    ticket_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_kb_feedback IS '知识有用性反馈';

CREATE TABLE svc_csat_survey (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    score smallint NOT NULL,
    tags jsonb,
    comment text,
    respondent_id bigint,
    channel varchar(16) NOT NULL DEFAULT 'PORTAL',
    submitted_at timestamptz,
    follow_up_task_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_csat_survey IS '满意度评价（每工单一次）';

CREATE TABLE svc_it_asset (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    asset_no varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    asset_type varchar(24),
    owner_id bigint,
    dept_id bigint,
    status varchar(16) NOT NULL DEFAULT 'IN_USE',
    borrow_due_at timestamptz,
    ext_ref varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_it_asset IS 'IT 资产登记';
COMMENT ON COLUMN svc_it_asset.asset_type IS '类型：DEVICE/SOFTWARE/ACCOUNT/LICENSE';
COMMENT ON COLUMN svc_it_asset.status IS '状态：IN_USE/IDLE/BORROWED/RETIRED';
