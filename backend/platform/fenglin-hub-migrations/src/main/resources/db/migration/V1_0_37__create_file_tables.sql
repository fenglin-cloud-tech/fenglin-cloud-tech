-- P2 文件中心：文件主表与版本表（数据库设计 4.35/4.36）。

SET search_path TO fenglin_hub, public;

CREATE TABLE hub_file (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    file_no VARCHAR(40) NOT NULL,
    name VARCHAR(256) NOT NULL,
    ext VARCHAR(16),
    size BIGINT NOT NULL,
    md5 VARCHAR(64) NOT NULL,
    storage_type VARCHAR(16) NOT NULL DEFAULT 'MINIO',
    bucket VARCHAR(128) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    biz_type VARCHAR(64),
    biz_id VARCHAR(64),
    sec_level INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    virus_scanned SMALLINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_file PRIMARY KEY (id),
    CONSTRAINT uk_hub_file_no UNIQUE (tenant_id, file_no)
);

CREATE INDEX idx_file_md5 ON hub_file (tenant_id, md5, size);
CREATE INDEX idx_file_biz ON hub_file (tenant_id, biz_type, biz_id);
CREATE INDEX idx_file_status ON hub_file (tenant_id, status);

CREATE TABLE hub_file_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    ver_no INTEGER NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    size BIGINT NOT NULL,
    md5 VARCHAR(64) NOT NULL,
    remark VARCHAR(256),
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_file_version PRIMARY KEY (id),
    CONSTRAINT uk_hub_file_version UNIQUE (tenant_id, file_id, ver_no)
);

CREATE INDEX idx_hub_file_version_file ON hub_file_version (tenant_id, file_id);

ALTER TABLE hub_file ENABLE ROW LEVEL SECURITY;
ALTER TABLE hub_file_version ENABLE ROW LEVEL SECURITY;

CREATE POLICY p_hub_file_tenant ON hub_file
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_file_version_tenant ON hub_file_version
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_file TO fl_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_file_version TO fl_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_file TO fl_platform_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON hub_file_version TO fl_platform_role;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8401, 'biz:file:upload', '文件上传', 'API', 'file', 1, 0),
       (8402, 'biz:file:download', '文件下载', 'API', 'file', 2, 0),
       (8403, 'biz:file:preview', '文件预览', 'API', 'file', 3, 0),
       (8404, 'tenant:file:policy:manage', '文件策略管理', 'API', 'file', 4, 0),
       (8405, 'tenant:file:quota:manage', '文件配额管理', 'API', 'file', 5, 0)
ON CONFLICT (code) DO NOTHING;
