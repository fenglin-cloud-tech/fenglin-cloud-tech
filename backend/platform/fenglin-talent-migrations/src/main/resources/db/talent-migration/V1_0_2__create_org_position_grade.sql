-- C1 组织与人事：组织架构、岗位、职级、编制计划
-- 文档基线：03_丰麟云才_03 第 4.1.1 ~ 4.1.4 节
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_org (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    org_code varchar(64) NOT NULL,
    org_name varchar(128) NOT NULL,
    parent_id bigint,
    path varchar(512) NOT NULL,
    level smallint NOT NULL,
    org_type varchar(32) NOT NULL,
    leader_id bigint,
    sort_no int DEFAULT 0,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_org IS '组织架构（物化路径 path + 邻接 parent_id 双写）';
COMMENT ON COLUMN talent_org.org_type IS '组织类型：COMPANY/DEPT/TEAM';
COMMENT ON COLUMN talent_org.status IS '状态：ENABLED/DISABLED';

CREATE TABLE talent_position (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    position_code varchar(64) NOT NULL,
    position_name varchar(128) NOT NULL,
    org_id bigint,
    job_family varchar(64),
    job_sequence varchar(64),
    headcount int DEFAULT 0,
    description text,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_position IS '岗位';
COMMENT ON COLUMN talent_position.status IS '状态：ENABLED/DISABLED';

CREATE TABLE talent_grade (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    grade_code varchar(32) NOT NULL,
    grade_name varchar(64) NOT NULL,
    sequence varchar(32) NOT NULL,
    grade_level smallint NOT NULL,
    band_min numeric(18, 2),
    band_max numeric(18, 2),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_grade IS '职级（sequence：P/M/T）';

CREATE TABLE talent_headcount_plan (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    plan_year smallint NOT NULL,
    org_id bigint NOT NULL,
    position_id bigint,
    budget_headcount int NOT NULL,
    used_headcount int DEFAULT 0,
    status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_headcount_plan IS '编制计划';
COMMENT ON COLUMN talent_headcount_plan.status IS '状态：DRAFT/APPROVED/CLOSED';
