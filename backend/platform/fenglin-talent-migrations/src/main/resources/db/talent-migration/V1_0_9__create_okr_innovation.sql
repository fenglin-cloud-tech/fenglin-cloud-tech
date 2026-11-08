-- C5 创新与 OKR：周期、目标、关键结果、对齐关系、创新提案、提案评审
-- 文档基线：03_丰麟云才_03 第 4.5.1 ~ 4.5.6 节
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_okr_period (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    period_name varchar(32) NOT NULL,
    period_type varchar(16) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    review_start date,
    review_end date,
    period_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_okr_period IS 'OKR 周期（period_name 如 2026 Q1）';
COMMENT ON COLUMN talent_okr_period.period_type IS '类型：YEAR/QUARTER/MONTH';
COMMENT ON COLUMN talent_okr_period.period_status IS '状态：PLANNING/ACTIVE/REVIEWING/CLOSED';

CREATE TABLE talent_okr_objective (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    period_id bigint NOT NULL,
    owner_id bigint NOT NULL,
    org_id bigint,
    obj_level varchar(16) NOT NULL,
    objective varchar(500) NOT NULL,
    progress smallint DEFAULT 0,
    confidence varchar(8),
    parent_objective_id bigint,
    visibility varchar(16) DEFAULT 'PUBLIC',
    obj_status varchar(16) NOT NULL,
    final_score numeric(3, 2),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_okr_objective IS 'OKR 目标（O）';
COMMENT ON COLUMN talent_okr_objective.obj_level IS '层级：COMPANY/DEPT/PERSONAL';
COMMENT ON COLUMN talent_okr_objective.confidence IS '信心指数：HIGH/MID/LOW';
COMMENT ON COLUMN talent_okr_objective.visibility IS '可见性：PUBLIC/PRIVATE';
COMMENT ON COLUMN talent_okr_objective.obj_status IS '状态：DRAFT/ALIGNED/ACTIVE/DONE/CLOSED';

CREATE TABLE talent_okr_key_result (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    objective_id bigint NOT NULL,
    title varchar(300) NOT NULL,
    metric_type varchar(16) NOT NULL,
    start_value numeric(18, 2),
    target_value numeric(18, 2),
    current_value numeric(18, 2),
    weight smallint DEFAULT 0,
    progress smallint DEFAULT 0,
    kr_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_okr_key_result IS 'OKR 关键结果（KR）';
COMMENT ON COLUMN talent_okr_key_result.metric_type IS '度量类型：NUMBER/PERCENT/MILESTONE';
COMMENT ON COLUMN talent_okr_key_result.kr_status IS '状态：NOT_STARTED/ON_TRACK/AT_RISK/DONE';

CREATE TABLE talent_okr_alignment (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    child_objective_id bigint NOT NULL,
    parent_objective_id bigint NOT NULL,
    align_type varchar(16) NOT NULL,
    weight smallint DEFAULT 100,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_okr_alignment IS 'OKR 对齐关系';
COMMENT ON COLUMN talent_okr_alignment.align_type IS '类型：ALIGN/SUPPORT/COLLAB';

CREATE TABLE talent_innovation_proposal (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    proposal_no varchar(32) NOT NULL,
    title varchar(200) NOT NULL,
    category varchar(64),
    content text NOT NULL,
    proposer_id bigint NOT NULL,
    team_members jsonb,
    expected_benefit text,
    attachment_ids jsonb,
    votes int DEFAULT 0,
    prop_status varchar(24) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_innovation_proposal IS '创新提案';
COMMENT ON COLUMN talent_innovation_proposal.prop_status IS '状态：DRAFT/REVIEWING/SCORING/INCUBATING/LANDED/REJECTED';

CREATE TABLE talent_innovation_review (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    proposal_id bigint NOT NULL,
    reviewer_id bigint NOT NULL,
    round_no smallint NOT NULL,
    score numeric(4, 1),
    comment text,
    rv_result varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_innovation_review IS '提案评审';
COMMENT ON COLUMN talent_innovation_review.rv_result IS '结果：PASS/HOLD/REJECT';
