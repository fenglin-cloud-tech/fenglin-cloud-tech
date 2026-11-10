-- 云才绩效：周期、目标、评估、结果
-- 设计约定：
--   1) 周期按租户维护，状态 DRAFT → GOAL_SETTING → EVALUATING → CLOSED；
--   2) 目标归属（周期, 员工），权重合计不超过 100，目标先提交后审批；
--   3) 评估归属（周期, 员工）唯一，提交即发起工作流审批，审批通过才计入结果；
--   4) 结果按评估分数计算等级、排名与奖金系数，发布后周期才可关闭；
--   5) RLS 与审计列沿用考勤/薪酬范式。
SET search_path TO fenglin_talent, public;

CREATE TABLE talent_perf_cycle (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    cycle_code varchar(32) NOT NULL,
    cycle_name varchar(64) NOT NULL,
    cycle_type varchar(16) NOT NULL DEFAULT 'QUARTER',
    start_date date NOT NULL,
    end_date date NOT NULL,
    cycle_status varchar(16) NOT NULL DEFAULT 'DRAFT',
    evaluation_count int NOT NULL DEFAULT 0,
    avg_score numeric(5, 2) NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_perf_cycle IS '绩效周期（如 2026-Q4）';
COMMENT ON COLUMN talent_perf_cycle.cycle_type IS '周期类型：ANNUAL 年度 / HALF_YEAR 半年 / QUARTER 季度 / MONTH 月度';
COMMENT ON COLUMN talent_perf_cycle.cycle_status IS
    '周期状态：DRAFT 草稿 / GOAL_SETTING 目标制定 / EVALUATING 评估中 / CLOSED 已关闭';
COMMENT ON COLUMN talent_perf_cycle.evaluation_count IS '已通过评估人数';
COMMENT ON COLUMN talent_perf_cycle.avg_score IS '周期平均分';

CREATE UNIQUE INDEX uk_perf_cycle_code ON talent_perf_cycle (tenant_id, cycle_code) WHERE deleted = 0;
CREATE INDEX idx_perf_cycle_status ON talent_perf_cycle (tenant_id, cycle_status);

CREATE TABLE talent_perf_goal (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    cycle_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    goal_name varchar(128) NOT NULL,
    goal_desc varchar(1000),
    weight numeric(5, 2) NOT NULL DEFAULT 0,
    measure_type varchar(16) NOT NULL DEFAULT 'QUANT',
    target_value varchar(255),
    goal_status varchar(16) NOT NULL DEFAULT 'DRAFT',
    approved_by bigint,
    approved_at timestamptz,
    approve_opinion varchar(500),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_perf_goal IS '绩效目标（周期内按员工制定）';
COMMENT ON COLUMN talent_perf_goal.weight IS '目标权重（百分比，0~100）';
COMMENT ON COLUMN talent_perf_goal.measure_type IS '衡量方式：QUANT 量化 / QUAL 定性';
COMMENT ON COLUMN talent_perf_goal.goal_status IS '目标状态：DRAFT 草稿 / SUBMITTED 待审批 / APPROVED 已通过 / REJECTED 已驳回';

CREATE UNIQUE INDEX uk_perf_goal_name ON talent_perf_goal (tenant_id, cycle_id, employee_id, goal_name)
    WHERE deleted = 0;
CREATE INDEX idx_perf_goal_employee ON talent_perf_goal (tenant_id, cycle_id, employee_id);

CREATE TABLE talent_perf_evaluation (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    cycle_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    evaluator_id bigint NOT NULL,
    review_type varchar(16) NOT NULL DEFAULT 'MANAGER',
    eval_status varchar(16) NOT NULL DEFAULT 'DRAFT',
    score numeric(5, 2),
    grade varchar(8),
    summary varchar(1000),
    process_instance_id varchar(64),
    approved_by bigint,
    approved_at timestamptz,
    approve_opinion varchar(500),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_perf_evaluation IS '绩效评估（一个周期一人一条，提交即发起工作流审批）';
COMMENT ON COLUMN talent_perf_evaluation.review_type IS '评估类型：SELF 自评 / MANAGER 上级评估 / CROSS 交叉评估';
COMMENT ON COLUMN talent_perf_evaluation.eval_status IS
    '评估状态：DRAFT 草稿 / APPROVING 审批中 / APPROVED 已通过 / REJECTED 已驳回';
COMMENT ON COLUMN talent_perf_evaluation.score IS '评估分数（0~100）';
COMMENT ON COLUMN talent_perf_evaluation.grade IS '评估等级：S/A/B/C/D';

CREATE UNIQUE INDEX uk_perf_eval_employee ON talent_perf_evaluation (tenant_id, cycle_id, employee_id)
    WHERE deleted = 0;
CREATE INDEX idx_perf_eval_status ON talent_perf_evaluation (tenant_id, cycle_id, eval_status);

CREATE TABLE talent_perf_result (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    cycle_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    final_score numeric(5, 2) NOT NULL DEFAULT 0,
    grade varchar(8),
    rank_no int NOT NULL DEFAULT 0,
    bonus_coefficient numeric(6, 2) NOT NULL DEFAULT 1.00,
    result_status varchar(16) NOT NULL DEFAULT 'CALCULATED',
    published_at timestamptz,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0
);

COMMENT ON TABLE talent_perf_result IS '绩效结果（周期内按员工汇总，发布后对员工可见）';
COMMENT ON COLUMN talent_perf_result.rank_no IS '周期内排名（分数降序，从 1 开始）';
COMMENT ON COLUMN talent_perf_result.bonus_coefficient IS '奖金系数：S 1.50 / A 1.30 / B 1.00 / C 0.80 / D 0.50';
COMMENT ON COLUMN talent_perf_result.result_status IS '结果状态：CALCULATED 已计算 / PUBLISHED 已发布';

CREATE UNIQUE INDEX uk_perf_result_employee ON talent_perf_result (tenant_id, cycle_id, employee_id)
    WHERE deleted = 0;
CREATE INDEX idx_perf_result_status ON talent_perf_result (tenant_id, cycle_id, result_status);

ALTER TABLE talent_perf_cycle ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_perf_cycle FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_perf_cycle_tenant ON talent_perf_cycle;
CREATE POLICY p_talent_perf_cycle_tenant ON talent_perf_cycle
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE talent_perf_goal ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_perf_goal FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_perf_goal_tenant ON talent_perf_goal;
CREATE POLICY p_talent_perf_goal_tenant ON talent_perf_goal
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE talent_perf_evaluation ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_perf_evaluation FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_perf_evaluation_tenant ON talent_perf_evaluation;
CREATE POLICY p_talent_perf_evaluation_tenant ON talent_perf_evaluation
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE talent_perf_result ENABLE ROW LEVEL SECURITY;
ALTER TABLE talent_perf_result FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS p_talent_perf_result_tenant ON talent_perf_result;
CREATE POLICY p_talent_perf_result_tenant ON talent_perf_result
    USING (tenant_id = current_tenant_id()) WITH CHECK (tenant_id = current_tenant_id());

DROP TRIGGER IF EXISTS trg_talent_perf_cycle_updated ON talent_perf_cycle;
CREATE TRIGGER trg_talent_perf_cycle_updated BEFORE UPDATE ON talent_perf_cycle
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_talent_perf_goal_updated ON talent_perf_goal;
CREATE TRIGGER trg_talent_perf_goal_updated BEFORE UPDATE ON talent_perf_goal
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_talent_perf_evaluation_updated ON talent_perf_evaluation;
CREATE TRIGGER trg_talent_perf_evaluation_updated BEFORE UPDATE ON talent_perf_evaluation
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS trg_talent_perf_result_updated ON talent_perf_result;
CREATE TRIGGER trg_talent_perf_result_updated BEFORE UPDATE ON talent_perf_result
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

GRANT SELECT, INSERT, UPDATE, DELETE ON talent_perf_cycle, talent_perf_goal, talent_perf_evaluation,
    talent_perf_result TO fl_talent_app;
