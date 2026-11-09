-- E1：任务与工时日志
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_task (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    task_no varchar(32) NOT NULL,
    title varchar(256) NOT NULL,
    task_type varchar(16) NOT NULL DEFAULT 'dev',
    status varchar(16) NOT NULL DEFAULT 'todo',
    priority varchar(8) NOT NULL DEFAULT 'P2',
    sprint_id bigint,
    requirement_id bigint,
    defect_id bigint,
    parent_id bigint,
    assignee_id bigint,
    estimate_hours numeric(10,2),
    actual_hours numeric(10,2) NOT NULL DEFAULT 0,
    due_date date,
    completed_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_task IS '任务';
COMMENT ON COLUMN devops_task.task_type IS '类型：dev/test/design/other';
COMMENT ON COLUMN devops_task.status IS '状态：todo/doing/done/closed';

CREATE TABLE devops_task_worklog (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    task_id bigint,
    defect_id bigint,
    user_id bigint NOT NULL,
    work_date date NOT NULL,
    hours numeric(6,2) NOT NULL,
    remark varchar(512),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_worklog_hours CHECK (hours > 0 AND hours <= 24),
    CONSTRAINT ck_worklog_target CHECK (task_id IS NOT NULL OR defect_id IS NOT NULL)
);
COMMENT ON TABLE devops_task_worklog IS '工时日志（任务或缺陷二选一）';
