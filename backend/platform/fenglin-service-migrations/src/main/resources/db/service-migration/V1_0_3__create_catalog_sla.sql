-- F1：服务目录、目录项、SLA 策略/计时/违约
SET search_path TO fenglin_service, public;

CREATE TABLE svc_catalog (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    parent_id bigint,
    icon varchar(128),
    sort_no int NOT NULL DEFAULT 0,
    visible_scope jsonb,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_catalog IS '服务目录';
COMMENT ON COLUMN svc_catalog.status IS '状态：ACTIVE/DISABLED';

CREATE TABLE svc_catalog_item (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    catalog_id bigint NOT NULL,
    code varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    description text,
    ticket_type varchar(16) NOT NULL DEFAULT 'REQUEST',
    form_def_id bigint,
    form_engine_ref varchar(64),
    default_group_id bigint,
    sla_policy_id bigint,
    need_approval boolean NOT NULL DEFAULT false,
    approval_flow_key varchar(64),
    visible_scope jsonb,
    sort_no int NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_catalog_item IS '服务目录项';
COMMENT ON COLUMN svc_catalog_item.status IS '状态：ACTIVE/DISABLED';

CREATE TABLE svc_sla_policy (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    name varchar(128) NOT NULL,
    priority varchar(8),
    ticket_type varchar(16),
    catalog_item_id bigint,
    calendar_id varchar(32),
    response_minutes int NOT NULL,
    resolve_minutes int NOT NULL,
    pause_on_status jsonb,
    warning_threshold smallint NOT NULL DEFAULT 80,
    escalation_matrix jsonb,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_sla_policy IS 'SLA 策略（tenant_id=0 为平台默认模板）';

CREATE TABLE svc_sla_timer (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    sla_policy_id bigint NOT NULL,
    response_elapsed_sec bigint NOT NULL DEFAULT 0,
    resolve_elapsed_sec bigint NOT NULL DEFAULT 0,
    response_state varchar(16) NOT NULL DEFAULT 'RUNNING',
    resolve_state varchar(16) NOT NULL DEFAULT 'RUNNING',
    response_due_at timestamptz,
    resolve_due_at timestamptz,
    warned_response boolean NOT NULL DEFAULT false,
    warned_resolve boolean NOT NULL DEFAULT false,
    last_tick_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_sla_timer IS 'SLA 计时';
COMMENT ON COLUMN svc_sla_timer.response_state IS '状态：RUNNING/PAUSED/MET/BREACHED';

CREATE TABLE svc_sla_breach (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    sla_policy_id bigint NOT NULL,
    breach_type varchar(16) NOT NULL,
    due_at timestamptz NOT NULL,
    breached_at timestamptz NOT NULL,
    overdue_sec bigint,
    escalated_level int NOT NULL DEFAULT 0,
    escalated_to bigint,
    reason varchar(255),
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_sla_breach IS 'SLA 违约记录';
COMMENT ON COLUMN svc_sla_breach.breach_type IS '类型：RESPONSE/RESOLVE';
COMMENT ON COLUMN svc_sla_breach.status IS '状态：OPEN/HANDLED/EXEMPTED';
