-- F2：流程定义、流程实例、流程任务
SET search_path TO fenglin_service, public;

CREATE TABLE svc_flow_definition (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    app_id bigint NOT NULL,
    flow_key varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    version_no int NOT NULL DEFAULT 1,
    bpmn_json jsonb,
    form_id bigint,
    engine_deploy_id varchar(64),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_flow_definition IS '流程定义（设计态 JSON 元数据）';
COMMENT ON COLUMN svc_flow_definition.status IS '状态：DRAFT/PUBLISHED/SUSPENDED/ARCHIVED';

CREATE TABLE svc_flow_instance (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    flow_id bigint NOT NULL,
    flow_key varchar(64) NOT NULL,
    business_key varchar(64),
    title varchar(255),
    app_id bigint,
    initiator_id bigint NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'RUNNING',
    current_node varchar(64),
    engine_instance_id varchar(64),
    started_at timestamptz,
    ended_at timestamptz,
    duration_sec bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_flow_instance IS '流程实例';
COMMENT ON COLUMN svc_flow_instance.status IS '状态：RUNNING/SUSPENDED/COMPLETED/TERMINATED';

CREATE TABLE svc_flow_task (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    instance_id bigint NOT NULL,
    task_key varchar(64),
    task_name varchar(128),
    task_type varchar(16) NOT NULL,
    assignee_id bigint,
    candidate_json jsonb,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    action varchar(16),
    comment varchar(512),
    due_at timestamptz,
    created_at_task timestamptz,
    completed_at timestamptz,
    duration_sec bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_flow_task IS '流程任务';
COMMENT ON COLUMN svc_flow_task.task_type IS '类型：APPROVE/CC/SERVICE/TIMER';
COMMENT ON COLUMN svc_flow_task.status IS '状态：PENDING/APPROVED/REJECTED/TRANSFERRED/TIMEOUT/CANCELLED';
