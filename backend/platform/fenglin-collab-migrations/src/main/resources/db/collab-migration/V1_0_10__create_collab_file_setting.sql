-- 公共文件附件与租户级配置
-- 扩展：collab_setting 承载敏感词策略、文档密级策略等以 JSON 存储的租户级配置（文档未定义配置表）。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_file (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(256) NOT NULL,
    ext varchar(16),
    size bigint NOT NULL DEFAULT 0,
    mime varchar(128),
    storage_key varchar(512) NOT NULL,
    sha256 varchar(64) NOT NULL,
    owner_id bigint,
    biz_type varchar(32),
    biz_id varchar(64),
    dlp_status varchar(16) NOT NULL DEFAULT 'PASS',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_file_dlp CHECK (dlp_status IN ('PASS', 'BLOCKED', 'PENDING'))
);

CREATE TABLE collab_setting (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    setting_key varchar(64) NOT NULL,
    content jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT uk_collab_setting UNIQUE (tenant_id, setting_key)
);

COMMENT ON TABLE collab_setting IS '扩展：租户级配置（敏感词、文档策略等），文档未定义配置表';
