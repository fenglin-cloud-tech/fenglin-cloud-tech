-- COL-07 云协文档富文本：正文格式标记（方案 A：正文继续全密文，格式靠新列判定）
--
-- 设计要点：
-- 1) 只加列，不改现有列、不重写表、不回填；存量数据一律视为 'PLAIN'（常量默认值，PG 12+ 不重写表）。
-- 2) 取值：PLAIN（纯文本）/ RICH_JSON（结构化富文本 JSON），语义见 COMMENT。
-- 3) 富文本 JSON 的 2 MB 上限在接口层（DocService）校验，不给 content_cipher 设长度上限
--    （AES-GCM 整串加密后 Base64 约为明文的 4/3，且密文列本就不限长）。
-- 4) CHECK 用 NOT VALID 先加，再单独 VALIDATE，避免一次长扫锁表；执行时带 lock_timeout，
--    不排长事务后面硬等。若确认无人持有长事务，可去掉 lock_timeout 重跑本文件剩余语句。
SET search_path TO fenglin_collab, public;
SET lock_timeout = '3s';

ALTER TABLE collab_document
    ADD COLUMN content_format varchar(16) NOT NULL DEFAULT 'PLAIN';

ALTER TABLE collab_document_version
    ADD COLUMN content_format varchar(16) NOT NULL DEFAULT 'PLAIN';

ALTER TABLE collab_document
    ADD CONSTRAINT ck_doc_content_format CHECK (content_format IN ('PLAIN', 'RICH_JSON')) NOT VALID;
ALTER TABLE collab_document_version
    ADD CONSTRAINT ck_doc_version_content_format CHECK (content_format IN ('PLAIN', 'RICH_JSON')) NOT VALID;

-- 与加约束分离的校验阶段：SHARE UPDATE EXCLUSIVE 锁，不阻塞读写。
ALTER TABLE collab_document VALIDATE CONSTRAINT ck_doc_content_format;
ALTER TABLE collab_document_version VALIDATE CONSTRAINT ck_doc_version_content_format;

COMMENT ON COLUMN collab_document.content_format IS
    '正文格式（COL-07）：PLAIN=纯文本；RICH_JSON=结构化富文本 JSON。正文本身仍加密存 content_cipher，本列仅标识解密后的解释方式。';
COMMENT ON COLUMN collab_document_version.content_format IS
    '版本正文格式（COL-07）：取值同 collab_document.content_format；回滚时随 content_cipher 一起复制。';

-- 回滚（手工执行，确认无新数据写入 RICH_JSON 后）：
--   SET search_path TO fenglin_collab, public;
--   ALTER TABLE collab_document DROP CONSTRAINT IF EXISTS ck_doc_content_format;
--   ALTER TABLE collab_document_version DROP CONSTRAINT IF EXISTS ck_doc_version_content_format;
--   ALTER TABLE collab_document DROP COLUMN IF EXISTS content_format;
--   ALTER TABLE collab_document_version DROP COLUMN IF EXISTS content_format;
