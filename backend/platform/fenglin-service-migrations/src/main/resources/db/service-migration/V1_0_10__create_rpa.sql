-- F3：机器人、凭据、自动化编排、机器人任务、调度计划、任务日志
SET search_path TO fenglin_service, public;

CREATE TABLE svc_robot (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    robot_code varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    robot_type varchar(16) NOT NULL,
    host varchar(128),
    capabilities jsonb,
    max_concurrency int NOT NULL DEFAULT 1,
    status varchar(16) NOT NULL DEFAULT 'OFFLINE',
    last_heartbeat_at timestamptz,
    agent_version varchar(32),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_robot IS '机器人';
COMMENT ON COLUMN svc_robot.robot_type IS '类型：ATTENDED/UNATTENDED';
COMMENT ON COLUMN svc_robot.status IS '状态：ONLINE/OFFLINE/BUSY/DISABLED';

CREATE TABLE svc_robot_credential (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    cred_type varchar(24) NOT NULL,
    target_system varchar(128),
    secret_enc text NOT NULL,
    kms_key_id varchar(128) NOT NULL,
    kms_version varchar(16),
    rotate_due_at timestamptz,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    last_used_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_robot_credential IS '机器人凭据（secret_enc 禁止出现在查询投影与日志）';
COMMENT ON COLUMN svc_robot_credential.status IS '状态：ACTIVE/ROTATING/DISABLED';

CREATE TABLE svc_automation_flow (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    app_id bigint,
    flow_code varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    version_no int NOT NULL DEFAULT 1,
    nodes_json jsonb NOT NULL,
    params_schema jsonb,
    target_whitelist jsonb NOT NULL,
    timeout_sec int NOT NULL DEFAULT 600,
    retry_policy jsonb,
    source_candidate_id bigint,
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_automation_flow IS '自动化任务编排';
COMMENT ON COLUMN svc_automation_flow.status IS '状态：DRAFT/PUBLISHED/DISABLED';

CREATE TABLE svc_robot_task (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    task_no varchar(32) NOT NULL,
    automation_flow_id bigint NOT NULL,
    robot_id bigint,
    trigger_type varchar(16) NOT NULL,
    priority smallint NOT NULL DEFAULT 5,
    input_json jsonb,
    output_json jsonb,
    status varchar(16) NOT NULL DEFAULT 'QUEUED',
    retry_count int NOT NULL DEFAULT 0,
    max_retry int NOT NULL DEFAULT 3,
    next_retry_at timestamptz,
    started_at timestamptz,
    ended_at timestamptz,
    duration_sec bigint,
    error_code varchar(32),
    error_msg varchar(512),
    business_key varchar(64),
    idempotency_key varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_robot_task IS '机器人任务';
COMMENT ON COLUMN svc_robot_task.trigger_type IS '触发：MANUAL/SCHEDULE/EVENT/QUEUE';
COMMENT ON COLUMN svc_robot_task.status IS '状态：QUEUED/DISPATCHED/RUNNING/SUCCEEDED/FAILED/RETRYING/MANUAL_REQUIRED/CANCELLED';

CREATE TABLE svc_robot_schedule (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    automation_flow_id bigint NOT NULL,
    trigger_type varchar(16) NOT NULL,
    cron_expr varchar(64),
    event_topic varchar(64),
    robot_selector jsonb,
    concurrency_limit int NOT NULL DEFAULT 10,
    timeout_sec int NOT NULL DEFAULT 600,
    enabled boolean NOT NULL DEFAULT true,
    last_fire_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_robot_schedule IS '调度计划';
COMMENT ON COLUMN svc_robot_schedule.trigger_type IS '触发：CRON/EVENT/QUEUE/MANUAL';

CREATE TABLE svc_robot_task_log (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    task_id bigint NOT NULL,
    attempt int NOT NULL DEFAULT 1,
    log_level varchar(8),
    step varchar(64),
    message text,
    screenshot_file_id varchar(64),
    logged_at timestamptz NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_robot_task_log IS '任务执行日志（文档要求月分区，此处普通表+时间索引）';
