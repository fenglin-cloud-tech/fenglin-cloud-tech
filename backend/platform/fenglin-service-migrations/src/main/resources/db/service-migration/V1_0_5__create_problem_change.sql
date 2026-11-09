-- F1：问题管理、变更请求
SET search_path TO fenglin_service, public;

CREATE TABLE svc_problem (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    problem_no varchar(32) NOT NULL,
    title varchar(255) NOT NULL,
    description text,
    root_cause text,
    workaround text,
    known_error boolean NOT NULL DEFAULT false,
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    owner_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_problem IS '问题管理';
COMMENT ON COLUMN svc_problem.status IS '状态：OPEN/ANALYZING/RESOLVED/CLOSED';

CREATE TABLE svc_change_request (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    change_no varchar(32) NOT NULL,
    title varchar(255) NOT NULL,
    change_type varchar(16),
    plan text,
    rollback_plan text,
    risk_level varchar(8),
    planned_start timestamptz,
    planned_end timestamptz,
    approval_flow_key varchar(64),
    status varchar(16) NOT NULL DEFAULT 'DRAFT',
    owner_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_change_request IS '变更请求';
COMMENT ON COLUMN svc_change_request.change_type IS '类型：STANDARD/NORMAL/EMERGENCY';
COMMENT ON COLUMN svc_change_request.status IS '状态：DRAFT/APPROVING/APPROVED/SCHEDULED/DONE/ROLLED_BACK';
