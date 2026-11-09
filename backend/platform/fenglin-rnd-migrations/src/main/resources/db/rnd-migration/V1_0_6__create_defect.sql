-- E1：缺陷
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_defect (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    defect_no varchar(32) NOT NULL,
    title varchar(256) NOT NULL,
    severity varchar(16) NOT NULL DEFAULT 'normal',
    priority varchar(8) NOT NULL DEFAULT 'P2',
    status varchar(16) NOT NULL DEFAULT 'new',
    env varchar(32),
    found_stage varchar(16) NOT NULL DEFAULT 'test',
    sprint_id bigint,
    requirement_id bigint,
    test_case_id bigint,
    assignee_id bigint,
    reporter_id bigint NOT NULL,
    reproduce_steps text,
    fix_build_id bigint,
    reopen_count int NOT NULL DEFAULT 0,
    fixed_at timestamptz,
    closed_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_defect IS '缺陷';
COMMENT ON COLUMN devops_defect.severity IS '严重级：blocker/critical/normal/minor';
COMMENT ON COLUMN devops_defect.status IS '状态：new/confirmed/fixing/fixed/verifying/closed/reopened/rejected';
COMMENT ON COLUMN devops_defect.found_stage IS '发现阶段：test/staging/prod（用于逃逸率）';
