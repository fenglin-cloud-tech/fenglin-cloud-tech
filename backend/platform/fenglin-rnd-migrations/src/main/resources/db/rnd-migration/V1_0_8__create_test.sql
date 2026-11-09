-- E1：测试用例、测试计划、测试执行与明细
SET search_path TO fenglin_devops, public;

CREATE TABLE devops_test_case (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    case_no varchar(32) NOT NULL,
    title varchar(256) NOT NULL,
    module varchar(128),
    level varchar(8) NOT NULL DEFAULT 'P2',
    case_type varchar(16) NOT NULL DEFAULT 'functional',
    preconditions text,
    steps jsonb NOT NULL DEFAULT '[]'::jsonb,
    requirement_id bigint,
    review_status varchar(16) NOT NULL DEFAULT 'draft',
    status varchar(16) NOT NULL DEFAULT 'active',
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_test_case IS '测试用例';
COMMENT ON COLUMN devops_test_case.case_type IS '类型：functional/api/performance/ui';
COMMENT ON COLUMN devops_test_case.review_status IS '评审：draft/reviewing/approved/rejected';
COMMENT ON COLUMN devops_test_case.status IS '状态：active/deprecated';

CREATE TABLE devops_test_plan (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    product_id bigint NOT NULL,
    plan_no varchar(32) NOT NULL,
    name varchar(128) NOT NULL,
    sprint_id bigint,
    build_id bigint,
    status varchar(16) NOT NULL DEFAULT 'pending',
    owner_id bigint,
    start_date date,
    end_date date,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_test_plan IS '测试计划';
COMMENT ON COLUMN devops_test_plan.status IS '状态：pending/running/finished/closed';

CREATE TABLE devops_test_execution (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    plan_id bigint NOT NULL,
    executor_id bigint NOT NULL,
    build_id bigint,
    result varchar(16) NOT NULL DEFAULT 'pending',
    pass_count int NOT NULL DEFAULT 0,
    fail_count int NOT NULL DEFAULT 0,
    executed_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_test_execution IS '测试执行';
COMMENT ON COLUMN devops_test_execution.result IS '结果：pending/pass/fail/blocked/skip';

CREATE TABLE devops_test_execution_item (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    execution_id bigint NOT NULL,
    case_id bigint NOT NULL,
    result varchar(16) NOT NULL DEFAULT 'pending',
    defect_id bigint,
    remark varchar(512),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);
COMMENT ON TABLE devops_test_execution_item IS '测试执行明细';
COMMENT ON COLUMN devops_test_execution_item.result IS '结果：pending/pass/fail/blocked/skip';
