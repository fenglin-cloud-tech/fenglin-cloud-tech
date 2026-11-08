-- C4 敬业度与离职预测：问卷、答卷、离职风险评分、干预跟进
-- 文档基线：03_丰麟云才_03 第 4.4.1 ~ 4.4.4 节
-- 匿名约束：匿名问卷 employee_hash 置 NULL，禁止存储可反推个人的标识。
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_engagement_survey (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    survey_no varchar(32) NOT NULL,
    title varchar(200) NOT NULL,
    survey_type varchar(24) NOT NULL,
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    anonymous boolean NOT NULL DEFAULT true,
    questions jsonb NOT NULL,
    dimensions jsonb,
    target_scope jsonb,
    survey_status varchar(16) NOT NULL,
    response_count int DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_engagement_survey IS '敬业度问卷';
COMMENT ON COLUMN talent_engagement_survey.survey_type IS '类型：QUARTERLY/ENPS/PULSE';
COMMENT ON COLUMN talent_engagement_survey.survey_status IS '状态：DRAFT/PUBLISHED/CLOSED';

CREATE TABLE talent_survey_response (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    survey_id bigint NOT NULL,
    employee_hash char(64),
    dept_id bigint,
    answers jsonb NOT NULL,
    score_total numeric(6, 2),
    dim_scores jsonb,
    submitted_at timestamptz NOT NULL,
    duration_sec int,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_survey_response IS '问卷答卷（匿名问卷 employee_hash 为 NULL）';

CREATE TABLE talent_attrition_risk (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    risk_score smallint NOT NULL,
    risk_level varchar(8) NOT NULL,
    factors jsonb NOT NULL,
    model_version varchar(32) NOT NULL,
    scored_at timestamptz NOT NULL,
    trend smallint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_attrition_risk IS '离职风险评分快照（risk_score 0-100，trend 为环比变化）';
COMMENT ON COLUMN talent_attrition_risk.risk_level IS '风险等级：LOW/MID/HIGH';

CREATE TABLE talent_intervention (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    risk_id bigint,
    measure_type varchar(24) NOT NULL,
    content text,
    owner_id bigint NOT NULL,
    follow_up text,
    iv_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_intervention IS '干预跟进';
COMMENT ON COLUMN talent_intervention.measure_type IS '措施类型：TALK/SALARY/TRANSFER/TRAINING';
COMMENT ON COLUMN talent_intervention.iv_status IS '状态：OPEN/FOLLOWING/CLOSED';
