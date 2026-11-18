-- COL-07 云协文档图片：图片元数据表
--
-- 设计要点：
-- 1) 只新建一张表，不改动 collab_document / collab_document_version，也不改平台文件服务表 hub_file。
--    正文 JSON 只存 {type:"image", fileId, width, height}，图片字节由文件服务保存为不透明密文对象，
--    本表记录文档与文件对象的归属、尺寸与摘要，用于分享校验和展示。
-- 2) 图片加密在云协服务内完成（HKDF 派生密钥 + 1 MiB 分块 AES-256-GCM），文件服务只存密文，
--    因此无需回填、也不会影响任何既有明文字节（旧附件继续按原方式读取）。
-- 3) 锁影响：仅对新表取 ACCESS EXCLUSIVE，建索引作用于空表，不触达存量表，不产生长事务等待。
-- 4) 回滚：DROP TABLE collab_doc_image;（密文对象留在 MinIO，可由文件中心按其生命周期清理）
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_doc_image (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    doc_id uuid NOT NULL,
    file_id bigint NOT NULL,
    uploader_id bigint NOT NULL,
    mime varchar(64) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 char(64) NOT NULL,
    width int NOT NULL,
    height int NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'AVAILABLE',
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_doc_image_status CHECK (status IN ('AVAILABLE', 'DELETED')),
    CONSTRAINT ck_doc_image_size CHECK (size_bytes > 0),
    CONSTRAINT ck_doc_image_dimension CHECK (width > 0 AND height > 0),
    CONSTRAINT uk_doc_image_file UNIQUE (tenant_id, doc_id, file_id)
);

CREATE INDEX idx_doc_image_doc ON collab_doc_image (tenant_id, doc_id, deleted);
CREATE INDEX idx_doc_image_file ON collab_doc_image (tenant_id, file_id, deleted);

ALTER TABLE collab_doc_image ENABLE ROW LEVEL SECURITY;
ALTER TABLE collab_doc_image FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_collab_doc_image_tenant ON collab_doc_image;
CREATE POLICY p_collab_doc_image_tenant ON collab_doc_image
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

DROP TRIGGER IF EXISTS trg_collab_doc_image_updated ON collab_doc_image;
CREATE TRIGGER trg_collab_doc_image_updated BEFORE UPDATE ON collab_doc_image
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON collab_doc_image TO fl_collab_app;

COMMENT ON TABLE collab_doc_image IS
    '文档图片（COL-07）：file_id 指向文件服务中的不透明密文对象；尺寸与 sha256 为剥离元数据后的明文摘要';
COMMENT ON COLUMN collab_doc_image.file_id IS '文件服务 hub_file.id；跨库不建外键，读取时按租户 + file_id 校验归属';
COMMENT ON COLUMN collab_doc_image.sha256 IS '剥离 EXIF/XMP 等元数据后的明文 SHA-256，用于完整性校验';
COMMENT ON COLUMN collab_doc_image.status IS 'AVAILABLE=可读；DELETED=逻辑删除，停止签发访问令牌';
