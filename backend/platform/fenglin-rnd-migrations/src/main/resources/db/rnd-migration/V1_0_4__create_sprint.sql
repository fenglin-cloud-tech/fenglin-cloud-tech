-- E1：迭代与迭代成员
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_sprint (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    sprint_no varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    goal text,
    start_date date NOT NULL,
    end_date date NOT NULL,
    capacity_hours numeric(12,2),
    status varchar(16) NOT NULL DEFAULT 'planning',
    burndown_snapshot jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_sprint_date CHECK (end_date >= start_date)
);
COMMENT ON TABLE devops_sprint IS '迭代';
COMMENT ON COLUMN devops_sprint.status IS '状态：planning/active/completed/closed/cancelled';

CREATE TABLE devops_sprint_member (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    sprint_id bigint NOT NULL,
    user_id bigint NOT NULL,
    available_hours numeric(10,2) NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_sprint_member IS '迭代成员';
