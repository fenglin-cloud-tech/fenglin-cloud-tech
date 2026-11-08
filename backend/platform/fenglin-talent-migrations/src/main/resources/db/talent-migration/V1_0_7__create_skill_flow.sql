-- C3 技能与活水：技能标签、员工技能、人才池、人才池成员、人才需求、活水申请
-- 文档基线：03_丰麟云才_03 第 4.3.1 ~ 4.3.6 节
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_skill_tag (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    name varchar(64) NOT NULL,
    category varchar(32) NOT NULL,
    parent_id bigint,
    tag_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_skill_tag IS '技能标签';
COMMENT ON COLUMN talent_skill_tag.category IS '分类：TECH/MGMT/BIZ/GENERAL';
COMMENT ON COLUMN talent_skill_tag.tag_status IS '状态：ENABLED/DISABLED';

CREATE TABLE talent_employee_skill (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    skill_id bigint NOT NULL,
    self_level smallint,
    cert_level smallint,
    years numeric(4, 1),
    evidence text,
    verified_by bigint,
    last_used_at date,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_employee_skill IS '员工技能（self_level/cert_level 取值 1-5）';

CREATE TABLE talent_talent_pool (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    pool_name varchar(128) NOT NULL,
    pool_type varchar(24) NOT NULL,
    description text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_talent_pool IS '人才池';
COMMENT ON COLUMN talent_talent_pool.pool_type IS '类型：KEY_POSITION/HIPO/SUCCESSION';

CREATE TABLE talent_talent_pool_member (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    pool_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    joined_at date NOT NULL,
    reason text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_talent_pool_member IS '人才池成员';

CREATE TABLE talent_talent_demand (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    demand_no varchar(32) NOT NULL,
    org_id bigint NOT NULL,
    position_id bigint,
    skills_required jsonb,
    headcount int NOT NULL,
    expected_date date,
    demand_type varchar(16) NOT NULL,
    publish_scope varchar(128),
    dm_status varchar(24) NOT NULL,
    owner_id bigint NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_talent_demand IS '人才需求（skills_required 存 [{skillId,level}]）';
COMMENT ON COLUMN talent_talent_demand.demand_type IS '类型：INTERNAL/EXTERNAL';
COMMENT ON COLUMN talent_talent_demand.dm_status IS '状态：DRAFT/APPROVING/PUBLIC/MATCHING/DONE/CLOSED';

CREATE TABLE talent_flow_application (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    demand_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    apply_type varchar(16) NOT NULL,
    flow_status varchar(24) NOT NULL,
    cur_manager_opinion text,
    target_opinion text,
    hr_opinion text,
    reason text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_flow_application IS '活水申请';
COMMENT ON COLUMN talent_flow_application.apply_type IS '类型：APPLY/TRANSFER/SECONDMENT';
COMMENT ON COLUMN talent_flow_application.flow_status IS '状态：PENDING/CUR_MGR_AGREE/TARGET_INTERVIEW/HR_REVIEW/PASSED/REJECTED/WITHDRAWN';
