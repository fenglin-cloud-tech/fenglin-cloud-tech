-- 审批域：模板、实例、任务
SET search_path TO fenglin_collab, public;

CREATE TABLE collab_approval_template (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    category varchar(32) NOT NULL,
    form_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
    process_key varchar(64) NOT NULL,
    process_version int NOT NULL DEFAULT 1,
    enabled smallint NOT NULL DEFAULT 1,
    dept_scope bigint[],
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

CREATE TABLE collab_approval_instance (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    template_id uuid NOT NULL REFERENCES collab_approval_template (id),
    instance_no varchar(32) NOT NULL,
    initiator_id bigint NOT NULL,
    form_data jsonb NOT NULL DEFAULT '{}'::jsonb,
    form_data_cipher text,
    wf_instance_id varchar(64),
    status varchar(16) NOT NULL DEFAULT 'RUNNING',
    current_node varchar(64),
    finished_at timestamptz,
    idempotency_key varchar(64),
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_apv_instance_status CHECK (status IN ('RUNNING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT uk_apv_no UNIQUE (tenant_id, instance_no)
);

CREATE TABLE collab_approval_task (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    instance_id uuid NOT NULL REFERENCES collab_approval_instance (id),
    node_key varchar(64) NOT NULL,
    node_name varchar(128) NOT NULL,
    assignee_id bigint,
    assignee_type varchar(16) NOT NULL DEFAULT 'USER',
    task_type varchar(16) NOT NULL DEFAULT 'SINGLE',
    action varchar(16),
    comment_cipher text,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    handled_at timestamptz,
    created_by bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_apv_task_assignee CHECK (assignee_type IN ('USER', 'ROLE', 'DEPT_LEADER')),
    CONSTRAINT ck_apv_task_type CHECK (task_type IN ('SINGLE', 'COUNTERSIGN', 'OR_SIGN')),
    CONSTRAINT ck_apv_task_action CHECK (action IS NULL OR action IN ('APPROVE', 'REJECT', 'TRANSFER', 'ADD_SIGN')),
    CONSTRAINT ck_apv_task_status CHECK (status IN ('PENDING', 'DONE', 'SKIPPED'))
);

-- 幂等键唯一（文档 uk_apv_idem）
CREATE UNIQUE INDEX uk_apv_idem ON collab_approval_instance (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted = 0;

COMMENT ON TABLE collab_approval_instance IS '审批实例；首版节点流转由本地任务表驱动，工作流中心负责实例登记与留痕';
