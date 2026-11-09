-- F1：工单主表、流转日志、评论、工单关联
SET search_path TO fenglin_service, public;

CREATE TABLE svc_ticket (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    ticket_no varchar(32) NOT NULL,
    title varchar(255) NOT NULL,
    description text,
    ticket_type varchar(16) NOT NULL,
    catalog_id bigint,
    catalog_item_id bigint,
    priority varchar(8) NOT NULL DEFAULT 'P3',
    impact varchar(8),
    urgency varchar(8),
    source varchar(16) NOT NULL,
    requester_id bigint NOT NULL,
    requester_dept_id bigint,
    contact_enc text,
    contact_hash varchar(64),
    assignee_id bigint,
    assignee_group_id bigint,
    status varchar(16) NOT NULL DEFAULT 'NEW',
    sla_policy_id bigint,
    sla_response_due_at timestamptz,
    sla_resolve_due_at timestamptz,
    first_response_at timestamptz,
    resolved_at timestamptz,
    closed_at timestamptz,
    reopen_count int NOT NULL DEFAULT 0,
    escalation_level int NOT NULL DEFAULT 0,
    kb_article_id bigint,
    ai_category varchar(64),
    ai_confidence numeric(5,4),
    tags jsonb,
    ext_json jsonb,
    engine_ref varchar(64),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_ticket IS '工单主表';
COMMENT ON COLUMN svc_ticket.ticket_type IS '类型：INCIDENT/REQUEST/PROBLEM/CHANGE';
COMMENT ON COLUMN svc_ticket.priority IS '优先级：P1/P2/P3/P4';
COMMENT ON COLUMN svc_ticket.source IS '来源：SELF/CATALOG/EMAIL/DESK/API/RPA';
COMMENT ON COLUMN svc_ticket.status IS '状态：NEW/ASSIGNED/IN_PROGRESS/PENDING/ESCALATED/RESOLVED/CLOSED/REOPENED/CANCELLED';
COMMENT ON COLUMN svc_ticket.contact_enc IS '提单人联系方式密文 AES-256-GCM';
COMMENT ON COLUMN svc_ticket.contact_hash IS '联系方式 SHA-256(明文+租户盐)';

CREATE TABLE svc_ticket_flow_log (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    action varchar(24) NOT NULL,
    from_status varchar(16),
    to_status varchar(16) NOT NULL,
    operator_id bigint NOT NULL,
    from_assignee_id bigint,
    to_assignee_id bigint,
    reason varchar(255),
    operated_at timestamptz NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_ticket_flow_log IS '工单流转日志（文档要求月分区，此处普通表+时间索引）';

CREATE TABLE svc_ticket_comment (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    comment_type varchar(16) NOT NULL,
    content text NOT NULL,
    author_id bigint NOT NULL,
    is_internal boolean NOT NULL DEFAULT false,
    attachment_ids jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_ticket_comment IS '工单沟通记录';
COMMENT ON COLUMN svc_ticket_comment.comment_type IS '类型：REPLY/NOTE/SYSTEM';

CREATE TABLE svc_ticket_relation (
    id bigint PRIMARY KEY,
    uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    rel_type varchar(16) NOT NULL,
    rel_id bigint NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE svc_ticket_relation IS '工单关联（子工单/关联/资产/问题/变更）';
COMMENT ON COLUMN svc_ticket_relation.rel_type IS '类型：PARENT/CHILD/RELATED/ASSET/PROBLEM/CHANGE';
