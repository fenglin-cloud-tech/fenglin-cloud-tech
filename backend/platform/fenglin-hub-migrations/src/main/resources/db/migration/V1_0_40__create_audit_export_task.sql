-- P2 审计导出（HUB-API-802）
-- 文档缺口兜底：数据库设计未定义导出任务表，按接口契约的 taskId/status 语义新增 hub_audit_export_task；
--               契约未定义任务查询与文件下载接口，补充 export-tasks 查询/下载端点并在汇报中标注。
SET search_path TO fenglin_hub, public;

CREATE TABLE hub_audit_export_task (
    id          bigint       NOT NULL,
    tenant_id   bigint       NOT NULL,
    task_no     varchar(40)  NOT NULL,
    format      varchar(8)   NOT NULL DEFAULT 'CSV',
    event_type  varchar(64),
    filter_from timestamptz,
    filter_to   timestamptz,
    status      varchar(16)  NOT NULL DEFAULT 'PENDING',
    file_path   varchar(512),
    row_count   bigint,
    error_msg   varchar(512),
    created_by  bigint,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted     smallint     NOT NULL DEFAULT 0,
    version     integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_hub_audit_export_task PRIMARY KEY (id),
    CONSTRAINT uk_hub_audit_export_task_no UNIQUE (tenant_id, task_no)
);

CREATE INDEX idx_audit_export_status ON hub_audit_export_task (status, created_at);
CREATE INDEX idx_audit_export_tenant ON hub_audit_export_task (tenant_id, created_at DESC);
CREATE TRIGGER trg_hub_audit_export_task_updated_at BEFORE UPDATE ON hub_audit_export_task
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE hub_audit_export_task ENABLE ROW LEVEL SECURITY;
CREATE POLICY p_hub_audit_export_task_tenant ON hub_audit_export_task
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());
CREATE POLICY p_hub_audit_export_task_platform ON hub_audit_export_task
    FOR ALL TO fl_platform_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON hub_audit_export_task TO fl_app, fl_platform_role;

INSERT INTO hub_permission (id, code, name, type, domain, sort, deleted)
VALUES (8131, 'platform:audit:export', '审计日志导出', 'API', 'audit', 31, 0)
ON CONFLICT (code) DO NOTHING;

COMMENT ON TABLE hub_audit_export_task IS '审计日志导出任务（HUB-API-802 文档缺口兜底）';
