-- C1 招聘域：招聘需求、候选人、应聘记录、面试、Offer
-- 文档基线：03_丰麟云才_03 第 4.1.6 ~ 4.1.10 节
-- 安全约定：候选人手机号/身份证号同样仅存密文与哈希，不建明文列与明文索引。
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_recruit_requisition (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    req_no varchar(32) NOT NULL,
    position_id bigint NOT NULL,
    org_id bigint NOT NULL,
    headcount int NOT NULL,
    salary_min numeric(18, 2),
    salary_max numeric(18, 2),
    urgency varchar(16) NOT NULL,
    reason_type varchar(16) NOT NULL,
    owner_id bigint NOT NULL,
    expect_date date,
    req_status varchar(24) NOT NULL,
    description text,
    requirements text,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_recruit_requisition IS '招聘需求（req_no 形如 TAL2026100001）';
COMMENT ON COLUMN talent_recruit_requisition.urgency IS '紧急度：LOW/MID/HIGH';
COMMENT ON COLUMN talent_recruit_requisition.reason_type IS '需求原因：NEW/REPLACE';
COMMENT ON COLUMN talent_recruit_requisition.req_status IS '状态：DRAFT/APPROVING/RECRUITING/PAUSED/DONE/CLOSED';

CREATE TABLE talent_candidate (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    name varchar(64) NOT NULL,
    phone_enc varchar(512),
    phone_hash char(64),
    id_card_enc varchar(512),
    id_card_hash char(64),
    email varchar(128),
    gender varchar(8),
    birth_date date,
    source varchar(24) NOT NULL,
    cand_status varchar(24) NOT NULL,
    experience_years numeric(4, 1),
    current_company varchar(128),
    expected_salary numeric(18, 2),
    resume_file_id bigint,
    tags jsonb,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_candidate IS '候选人（手机号/身份证号密文 + 哈希，禁止明文检索）';
COMMENT ON COLUMN talent_candidate.source IS '来源：REFERRAL/JOB_SITE/HEADHUNTER/OFFICIAL';
COMMENT ON COLUMN talent_candidate.cand_status IS '状态：NEW/SCREENING/INTERVIEWING/OFFER/ONBOARD/REJECTED';

CREATE TABLE talent_application (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    candidate_id bigint NOT NULL,
    requisition_id bigint NOT NULL,
    stage varchar(24) NOT NULL,
    app_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_application IS '应聘记录';
COMMENT ON COLUMN talent_application.app_status IS '状态：IN_PROGRESS/PASS/REJECT';

CREATE TABLE talent_interview (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    application_id bigint NOT NULL,
    round_no smallint NOT NULL,
    interviewer_id bigint NOT NULL,
    scheduled_at timestamptz,
    duration_min int DEFAULT 60,
    mode varchar(16) NOT NULL,
    location varchar(256),
    int_status varchar(16) NOT NULL,
    score numeric(4, 1),
    evaluation text,
    result varchar(16),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_interview IS '面试';
COMMENT ON COLUMN talent_interview.round_no IS '轮次：1/2/3/HR';
COMMENT ON COLUMN talent_interview.mode IS '形式：ONSITE/VIDEO/PHONE';
COMMENT ON COLUMN talent_interview.int_status IS '状态：PENDING/CONFIRMED/DONE/CANCELED/NOSHOW';
COMMENT ON COLUMN talent_interview.result IS '结果：PASS/HOLD/REJECT';

CREATE TABLE talent_offer (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    candidate_id bigint NOT NULL,
    requisition_id bigint NOT NULL,
    position_id bigint NOT NULL,
    grade_id bigint,
    base_salary numeric(18, 2) NOT NULL,
    bonus numeric(18, 2),
    offer_date date NOT NULL,
    expect_join_date date,
    offer_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_offer IS 'Offer';
COMMENT ON COLUMN talent_offer.offer_status IS '状态：APPROVING/SENT/ACCEPTED/REJECTED/EXPIRED';
