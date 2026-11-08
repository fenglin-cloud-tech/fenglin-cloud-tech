-- C2 培训与知识：课程、班次、学习记录、证书、知识分类、知识文章
-- 文档基线：03_丰麟云才_03 第 4.2.1 ~ 4.2.6 节
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_training_course (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    course_code varchar(32) NOT NULL,
    title varchar(200) NOT NULL,
    category varchar(64),
    cover_file_id bigint,
    description text,
    lecturer varchar(64),
    mode varchar(16) NOT NULL,
    duration_hours numeric(6, 1) DEFAULT 0,
    credit numeric(6, 1) DEFAULT 0,
    course_status varchar(16) NOT NULL,
    target_audience jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_training_course IS '培训课程';
COMMENT ON COLUMN talent_training_course.mode IS '授课形式：ONLINE/OFFLINE/BLENDED';
COMMENT ON COLUMN talent_training_course.course_status IS '状态：DRAFT/PUBLISHED/OFFLINE';

CREATE TABLE talent_course_session (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    course_id bigint NOT NULL,
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    capacity int NOT NULL,
    enrolled int DEFAULT 0,
    enroll_deadline timestamptz,
    venue varchar(256),
    session_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_course_session IS '培训班次';
COMMENT ON COLUMN talent_course_session.session_status IS '状态：OPEN/CLOSED/DONE';

CREATE TABLE talent_learning_record (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    course_id bigint NOT NULL,
    session_id bigint,
    progress smallint DEFAULT 0,
    learn_status varchar(16) NOT NULL,
    score numeric(5, 1),
    study_seconds int DEFAULT 0,
    completed_at timestamptz,
    enroll_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_learning_record IS '学习记录';
COMMENT ON COLUMN talent_learning_record.learn_status IS '状态：NOT_STARTED/LEARNING/DONE/FAILED';
COMMENT ON COLUMN talent_learning_record.enroll_status IS '报名状态：ENROLLED/CANCELED';

CREATE TABLE talent_certificate (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    course_id bigint,
    cert_no varchar(64) NOT NULL,
    cert_name varchar(128) NOT NULL,
    issue_date date NOT NULL,
    expire_date date,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_certificate IS '证书';

CREATE TABLE talent_knowledge_category (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    parent_id bigint,
    name varchar(128) NOT NULL,
    sort_no int DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_knowledge_category IS '知识分类';

CREATE TABLE talent_knowledge_article (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    title varchar(200) NOT NULL,
    category_id bigint,
    content text,
    author_id bigint NOT NULL,
    tags jsonb,
    art_status varchar(16) NOT NULL,
    art_version int DEFAULT 1,
    view_count int DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_knowledge_article IS '知识文章（Markdown 正文）';
COMMENT ON COLUMN talent_knowledge_article.art_status IS '状态：DRAFT/REVIEW/PUBLISHED/ARCHIVED';
