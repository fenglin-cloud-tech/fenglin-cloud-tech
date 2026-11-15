-- FIN-03：应收催收任务（幂等：Flyway 按版本跳过；直接重跑也不会因建表/索引/策略报错）
SET search_path TO fenglin_finance, public;

CREATE TABLE IF NOT EXISTS fin_collection_task (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    task_no varchar(32) NOT NULL,
    receivable_id bigint NOT NULL,
    ar_no varchar(32) NOT NULL,
    customer_id bigint,
    customer_name varchar(128),
    outstanding_amount numeric(20,4) NOT NULL,
    overdue_days int NOT NULL DEFAULT 0,
    task_type varchar(16) NOT NULL DEFAULT 'REMINDER',
    assignee_id bigint,
    due_date date,
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    remark varchar(512),
    completed_at timestamptz,
    completed_by bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE fin_collection_task IS '应收催收任务（按应收单发起，跟进状态与催收结果）';
COMMENT ON COLUMN fin_collection_task.task_type IS '类型：REMINDER/LETTER/PHONE/VISIT/LEGAL';
COMMENT ON COLUMN fin_collection_task.status IS '状态：OPEN/IN_PROGRESS/DONE/CANCELLED';

CREATE UNIQUE INDEX IF NOT EXISTS uk_fin_collection_task_no ON fin_collection_task (tenant_id, task_no);
CREATE INDEX IF NOT EXISTS idx_fin_collection_task_status ON fin_collection_task (tenant_id, status, due_date);
CREATE INDEX IF NOT EXISTS idx_fin_collection_task_receivable ON fin_collection_task (tenant_id, receivable_id);

ALTER TABLE fin_collection_task ENABLE ROW LEVEL SECURITY;
ALTER TABLE fin_collection_task FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON fin_collection_task;
CREATE POLICY tenant_isolation ON fin_collection_task
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON fin_collection_task TO fl_finance_app;

-- 回滚：DROP TABLE fin_collection_task;（索引与策略随表删除）
