-- FIN-08：预算调整/追加/跨期结转留痕
SET search_path TO fenglin_finance, public;

CREATE TABLE fin_budget_adjustment (
    id bigint PRIMARY KEY,
    tenant_id bigint NOT NULL,
    adjust_no varchar(32) NOT NULL,
    budget_id bigint NOT NULL,
    adjust_type varchar(16) NOT NULL,
    amount numeric(20,4) NOT NULL,
    before_amount numeric(20,4) NOT NULL,
    after_amount numeric(20,4) NOT NULL,
    source_budget_id bigint,
    reason varchar(512),
    operator_id bigint NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint,
    updated_at timestamptz,
    deleted smallint NOT NULL DEFAULT 0,
    version int NOT NULL DEFAULT 0,
    CONSTRAINT ck_fin_budget_adjustment_positive CHECK (amount > 0),
    CONSTRAINT ck_fin_budget_adjustment_after CHECK (after_amount >= 0)
);
COMMENT ON TABLE fin_budget_adjustment IS '预算调整流水（追加/调减/跨期转入转出）';
COMMENT ON COLUMN fin_budget_adjustment.adjust_type IS '类型：INCREASE/INCREASE_APPEND/DECREASE/ROLLOVER_IN/ROLLOVER_OUT';

CREATE INDEX idx_fin_budget_adjustment_budget
    ON fin_budget_adjustment (tenant_id, budget_id, occurred_at DESC);
CREATE INDEX idx_fin_budget_adjustment_no
    ON fin_budget_adjustment (tenant_id, adjust_no);

-- 与 V1_1_1 一致：新表同样开启强制行级隔离并授权应用角色
ALTER TABLE fin_budget_adjustment ENABLE ROW LEVEL SECURITY;
ALTER TABLE fin_budget_adjustment FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON fin_budget_adjustment
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
GRANT SELECT, INSERT, UPDATE, DELETE ON fin_budget_adjustment TO fl_finance_app;
