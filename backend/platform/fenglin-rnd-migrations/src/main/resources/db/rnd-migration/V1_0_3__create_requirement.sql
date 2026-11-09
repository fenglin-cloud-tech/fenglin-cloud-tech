-- E1：需求与需求关联关系
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_requirement (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    req_no varchar(32) NOT NULL,
    title varchar(256) NOT NULL,
    req_type varchar(16) NOT NULL DEFAULT 'user',
    priority varchar(8) NOT NULL DEFAULT 'P2',
    status_id bigint NOT NULL,
    status_category varchar(16) NOT NULL DEFAULT 'todo',
    sprint_id bigint,
    parent_id bigint,
    assignee_id bigint,
    reporter_id bigint NOT NULL,
    estimate_hours numeric(10,2),
    acceptance_criteria text,
    plan_start_at timestamptz,
    plan_end_at timestamptz,
    delivered_at timestamptz,
    is_escaped smallint NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_requirement IS '需求';
COMMENT ON COLUMN devops_requirement.req_type IS '类型：business/user/tech';
COMMENT ON COLUMN devops_requirement.priority IS '优先级：P0/P1/P2/P3';
COMMENT ON COLUMN devops_requirement.status_category IS '状态类别冗余：todo/doing/done';
COMMENT ON COLUMN devops_requirement.is_escaped IS '是否交付后产生逃逸缺陷（供质量度量）';

CREATE TABLE devops_requirement_relation (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    src_requirement_id bigint NOT NULL,
    dst_requirement_id bigint NOT NULL,
    relation_type varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_requirement_relation IS '需求关联关系';
COMMENT ON COLUMN devops_requirement_relation.relation_type IS '关系：parent/child/blocks/blocked_by/clone/relate';
