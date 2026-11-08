-- 云财索引与触发器：租户索引统一生成，业务唯一键按 deleted=0 建部分唯一索引
SET search_path TO fenglin_finance, public;

DO $$
DECLARE
    t text;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_finance'
        AND tablename NOT IN ('fin_idempotency_record', 'flyway_schema_history')
    LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_tenant ON fenglin_finance.%I (tenant_id)', t, t);
    END LOOP;
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'fenglin_finance'
        AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('CREATE TRIGGER trg_%s_updated BEFORE UPDATE ON fenglin_finance.%I '
                       'FOR EACH ROW EXECUTE FUNCTION set_updated_at()', t, t);
    END LOOP;
END
$$;

CREATE UNIQUE INDEX uk_fin_ledger_code ON fin_ledger (tenant_id, ledger_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_subject_code ON fin_accounting_subject (tenant_id, ledger_id, subject_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_period_code ON fin_accounting_period (tenant_id, ledger_id, period_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_voucher_no ON fin_voucher (tenant_id, ledger_id, voucher_no) WHERE deleted = 0;
CREATE INDEX idx_fin_voucher_date ON fin_voucher (tenant_id, ledger_id, voucher_date);
CREATE INDEX idx_fin_voucher_source ON fin_voucher (tenant_id, source_type, source_biz_id);
CREATE INDEX idx_fin_entry_voucher ON fin_voucher_entry (tenant_id, voucher_id);
CREATE INDEX idx_fin_entry_subject ON fin_voucher_entry (tenant_id, subject_code, period_code);
CREATE UNIQUE INDEX uk_fin_voucher_chain_seq ON fin_voucher_audit_log (tenant_id, chain_seq) WHERE deleted = 0;
CREATE INDEX idx_fin_voucher_chain_voucher ON fin_voucher_audit_log (tenant_id, voucher_id, chain_seq);
CREATE UNIQUE INDEX uk_fin_snapshot_period ON fin_period_close_snapshot (tenant_id, ledger_id, period_code, snapshot_type) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_ar_no ON fin_receivable (tenant_id, ar_no) WHERE deleted = 0;
CREATE INDEX idx_fin_ar_due ON fin_receivable (tenant_id, status, due_date);
CREATE UNIQUE INDEX uk_fin_ap_no ON fin_payable (tenant_id, ap_no) WHERE deleted = 0;
CREATE INDEX idx_fin_ap_due ON fin_payable (tenant_id, status, due_date);
CREATE UNIQUE INDEX uk_fin_receipt_no ON fin_receipt (tenant_id, receipt_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_payment_no ON fin_payment (tenant_id, payment_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_write_off_no ON fin_arap_write_off (tenant_id, write_off_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_invoice_number ON fin_invoice (tenant_id, invoice_number) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_claim_no ON fin_expense_claim (tenant_id, claim_no) WHERE deleted = 0;
CREATE INDEX idx_fin_claim_item ON fin_expense_claim_item (tenant_id, claim_id);
CREATE UNIQUE INDEX uk_fin_budget_dim ON fin_budget (tenant_id, period_year, dimension, dimension_id, subject_code) WHERE deleted = 0;
CREATE INDEX idx_fin_budget_control_biz ON fin_budget_control (tenant_id, biz_type, biz_id);
CREATE UNIQUE INDEX uk_fin_bank_account_hash ON fin_bank_account (tenant_id, account_no_hash) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_bank_txn ON fin_bank_transaction (tenant_id, account_id, txn_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_recon_no ON fin_reconciliation (tenant_id, recon_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_project_no ON fin_project (tenant_id, project_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_project_member ON fin_project_member (tenant_id, project_id, employee_id) WHERE deleted = 0;
CREATE INDEX idx_fin_milestone_project ON fin_project_milestone (tenant_id, project_id);
CREATE INDEX idx_fin_labor_rate ON fin_labor_rate (tenant_id, employee_id, effective_from);
CREATE UNIQUE INDEX uk_fin_timesheet_no ON fin_timesheet (tenant_id, timesheet_no) WHERE deleted = 0;
CREATE INDEX idx_fin_timesheet_day ON fin_timesheet (tenant_id, employee_id, work_date);
CREATE UNIQUE INDEX uk_fin_cost_source ON fin_cost_collection (tenant_id, project_id, cost_type, source_biz_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_settlement_no ON fin_project_settlement (tenant_id, settlement_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_asset_category_code ON fin_asset_category (tenant_id, category_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_asset_no ON fin_asset_card (tenant_id, asset_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_dep_asset_period ON fin_depreciation_schedule (tenant_id, asset_id, period_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_asset_change_no ON fin_asset_change (tenant_id, change_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_stocktaking_no ON fin_asset_stocktaking (tenant_id, stocktaking_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_stocktaking_item ON fin_asset_stocktaking_item (tenant_id, stocktaking_id, asset_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_it_asset ON fin_it_asset (tenant_id, asset_id) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_factor_version ON fin_emission_factor (tenant_id, factor_code, factor_version) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_activity_no ON fin_carbon_activity (tenant_id, activity_no) WHERE deleted = 0;
CREATE INDEX idx_fin_activity_period ON fin_carbon_activity (tenant_id, period_code, status);
CREATE UNIQUE INDEX uk_fin_emission_no ON fin_carbon_emission (tenant_id, emission_no) WHERE deleted = 0;
CREATE INDEX idx_fin_emission_latest ON fin_carbon_emission (tenant_id, period_code, scope, is_latest);
CREATE UNIQUE INDEX uk_fin_target_no ON fin_carbon_target (tenant_id, target_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_esg_indicator ON fin_esg_indicator (tenant_id, period_year, indicator_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_esg_report_no ON fin_esg_report (tenant_id, report_no) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_currency_code ON fin_currency (tenant_id, currency_code) WHERE deleted = 0;
CREATE UNIQUE INDEX uk_fin_exchange_rate ON fin_exchange_rate (tenant_id, from_currency, to_currency, effective_date) WHERE deleted = 0;
CREATE INDEX idx_fin_attachment_biz ON fin_attachment (tenant_id, biz_type, biz_id);
CREATE UNIQUE INDEX uk_fin_idempotency_key ON fin_idempotency_record (tenant_id, idempotency_key);
CREATE UNIQUE INDEX uk_fin_tenant_key ON fin_tenant_key (tenant_id) WHERE deleted = 0;
