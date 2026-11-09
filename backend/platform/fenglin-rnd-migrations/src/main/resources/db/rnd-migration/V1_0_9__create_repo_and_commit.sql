-- E1：代码库、代码提交、提交-工作项映射
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_repo (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    repo_name varchar(128) NOT NULL,
    vcs_type varchar(16) NOT NULL,
    repo_url varchar(512) NOT NULL,
    external_id varchar(128),
    default_branch varchar(128) NOT NULL DEFAULT 'main',
    access_token_cipher text NOT NULL,
    access_token_hash varchar(128),
    webhook_secret_cipher text,
    sync_status varchar(16) NOT NULL DEFAULT 'pending',
    last_sync_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_repo IS '代码库';
COMMENT ON COLUMN devops_repo.vcs_type IS '类型：gitlab/github';
COMMENT ON COLUMN devops_repo.access_token_cipher IS '访问令牌 AES-256-GCM 密文（PII/凭据）';
COMMENT ON COLUMN devops_repo.access_token_hash IS '令牌 SHA-256+租户盐哈希';
COMMENT ON COLUMN devops_repo.sync_status IS '同步状态：pending/ok/failed';

CREATE TABLE devops_commit (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    repo_id bigint NOT NULL,
    commit_sha varchar(64) NOT NULL,
    branch varchar(256),
    author_name varchar(128) NOT NULL,
    author_email_hash varchar(128),
    author_user_id bigint,
    message text,
    add_lines int NOT NULL DEFAULT 0,
    del_lines int NOT NULL DEFAULT 0,
    files_changed int NOT NULL DEFAULT 0,
    is_merge smallint NOT NULL DEFAULT 0,
    is_excluded smallint NOT NULL DEFAULT 0,
    committed_at timestamptz NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_commit IS '代码提交（文档要求月分区，按既有范式以普通表+时间索引实现，部署阶段做分区归档）';
COMMENT ON COLUMN devops_commit.author_email_hash IS '作者邮箱 SHA-256+租户盐，不存明文';

CREATE TABLE devops_commit_link (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    commit_id bigint NOT NULL,
    biz_type varchar(16) NOT NULL,
    biz_id bigint NOT NULL,
    link_type varchar(16) NOT NULL DEFAULT 'keyword',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_commit_link IS '提交-工作项映射';
COMMENT ON COLUMN devops_commit_link.biz_type IS '工作项类型：requirement/task/defect';
COMMENT ON COLUMN devops_commit_link.link_type IS '关联方式：keyword/manual/mr';
