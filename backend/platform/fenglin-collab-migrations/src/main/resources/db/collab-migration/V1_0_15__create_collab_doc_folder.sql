-- COL-07 文档目录管理
-- 关键前提：collab_document 已有 folder_id 列（建表时预留，存量全部为空），
-- 因此本次只新增目录表，不改现有文档表结构、不回填任何存量数据。
-- 归置动作仅 UPDATE collab_document.folder_id。
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_doc_folder (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    parent_id uuid,
    name varchar(64) NOT NULL,
    sort_no int NOT NULL DEFAULT 0,
    owner_id bigint NOT NULL,
    created_by bigint,
    updated_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_doc_folder_name CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX uk_doc_folder_name ON collab_doc_folder
    (tenant_id, owner_id, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), name)
    WHERE deleted = 0;
CREATE INDEX idx_doc_folder_owner ON collab_doc_folder (tenant_id, owner_id, deleted);

ALTER TABLE collab_doc_folder ENABLE ROW LEVEL SECURITY;
ALTER TABLE collab_doc_folder FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_collab_doc_folder_tenant ON collab_doc_folder;
CREATE POLICY p_collab_doc_folder_tenant ON collab_doc_folder
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

DROP TRIGGER IF EXISTS trg_collab_doc_folder_updated ON collab_doc_folder;
CREATE TRIGGER trg_collab_doc_folder_updated BEFORE UPDATE ON collab_doc_folder
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON collab_doc_folder TO fl_collab_app;

COMMENT ON TABLE collab_doc_folder IS '文档目录（COL-07）：按 owner_id 隔离，支持父子层级；文档归属复用 collab_document.folder_id';
