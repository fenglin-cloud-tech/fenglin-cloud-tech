-- C1 员工全生命周期：入职、试用期、异动、离职
-- 文档基线：03_丰麟云才_03 第 4.1.11 ~ 4.1.14 节
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_onboarding (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    offer_id bigint,
    onboard_date date NOT NULL,
    ob_status varchar(24) NOT NULL,
    checklist jsonb,
    mentor_id bigint,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_onboarding IS '入职';
COMMENT ON COLUMN talent_onboarding.ob_status IS '状态：PENDING/DONE';

CREATE TABLE talent_probation (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    plan_json jsonb,
    end_date date NOT NULL,
    evaluation text,
    prob_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_probation IS '试用期';
COMMENT ON COLUMN talent_probation.prob_status IS '状态：ONGOING/PASSED/EXTENDED/FAILED';

CREATE TABLE talent_transfer (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    transfer_type varchar(24) NOT NULL,
    before_json jsonb,
    after_json jsonb,
    effective_date date NOT NULL,
    reason text,
    tf_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_transfer IS '异动';
COMMENT ON COLUMN talent_transfer.transfer_type IS '类型：TRANSFER/PROMOTION/ADJUST_SALARY/SECONDMENT';
COMMENT ON COLUMN talent_transfer.tf_status IS '状态：APPROVING/EFFECTIVE/CANCELED';

CREATE TABLE talent_resignation (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    resign_type varchar(24) NOT NULL,
    apply_date date NOT NULL,
    last_work_date date NOT NULL,
    reason_category varchar(64),
    handover_status varchar(16) NOT NULL,
    rs_status varchar(16) NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_resignation IS '离职';
COMMENT ON COLUMN talent_resignation.resign_type IS '类型：VOLUNTARY/INVOLUNTARY/RETIRE/CONTRACT_END';
COMMENT ON COLUMN talent_resignation.handover_status IS '交接状态：PENDING/DONE';
COMMENT ON COLUMN talent_resignation.rs_status IS '状态：APPROVING/EFFECTIVE/REVOKED';
