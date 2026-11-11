import type { PageResult } from '@fenglin/shared'
import { download, get, post, put, request } from './http'

export type FinanceId = number

/** 云财接口金额均为字符串（后端 BigDecimal），日期为 ISO 字符串。 */
export interface FinanceLedger {
  id: FinanceId
  ledger_code: string
  ledger_name: string
  company_name: string
  base_currency: string
  accounting_standard: string
  status: string
}

export interface FinanceSubject {
  id: FinanceId
  subject_code: string
  subject_name: string
  parent_id?: FinanceId | null
  subject_level: number
  subject_category: string
  balance_direction: string
  is_leaf: boolean
  auxiliary_types?: string
  status: string
}

export interface FinancePeriod {
  id: FinanceId
  period_code: string
  period_year: number
  period_month: number
  start_date: string
  end_date: string
  status: string
}

export interface FinanceTrialBalanceLine {
  subject_code: string
  subject_name: string
  subject_category: string
  balance_direction: string
  opening_debit: string
  opening_credit: string
  period_debit: string
  period_credit: string
  closing_debit: string
  closing_credit: string
}

export interface FinanceTrialBalance {
  period_code: string
  lines: FinanceTrialBalanceLine[]
  total_debit: string
  total_credit: string
  balanced: boolean
}

export interface FinanceBalanceSheetLine {
  subject_code: string
  subject_name: string
  category: string
  amount: string
}

export interface FinanceBalanceSheet {
  period_code: string
  assets: string
  liabilities: string
  equity: string
  profit_loss: string
  total_assets: string
  total_liabilities_equity: string
  balanced: boolean
  lines: FinanceBalanceSheetLine[]
}

export interface FinanceIncomeStatementLine {
  subject_code: string
  subject_name: string
  category: string
  amount: string
}

export interface FinanceIncomeStatement {
  period_code: string
  revenue: string
  cost: string
  net_profit: string
  lines: FinanceIncomeStatementLine[]
}

export interface FinanceVoucherEntry {
  line_no: number
  summary: string
  subject_code: string
  subject_name: string
  direction: string
  amount: string
  currency_code: string
  amount_base: string
  auxiliary?: string
  quantity?: string
  price?: string
}

export interface FinanceVoucherAuditLog {
  chain_seq: number
  action: string
  operator_id: number
  operator_name: string
  occurred_at: string
  record_hash: string
  prev_hash: string
  signature?: string
}

export interface FinanceVoucher {
  id: FinanceId
  ledger_id: FinanceId
  voucher_word: string
  voucher_no: string
  period_code: string
  voucher_date: string
  summary: string
  currency_code: string
  total_debit: string
  total_credit: string
  source_type?: string
  source_biz_id?: FinanceId | null
  status: string
  audited_by?: number | null
  posted_by?: number | null
  chain_hash?: string
  entries: FinanceVoucherEntry[]
  audit_trail: FinanceVoucherAuditLog[]
}

export interface FinanceVoucherActionResult {
  id: FinanceId
  voucher_no: string
  status: string
  chain_hash?: string
  workflow_instance_id?: string
}

export interface FinanceAuditTrail {
  voucher_id: FinanceId
  chain_valid: boolean
  last_hash: string
  logs: FinanceVoucherAuditLog[]
}

export interface FinanceVoucherTemplate {
  id: FinanceId
  template_code: string
  template_name: string
  biz_type: string
  debit_subject_code: string
  credit_subject_code: string
  auxiliary_mapping?: Record<string, unknown>
  condition_expr?: string
  status: string
}

export interface FinanceReceivable {
  id: FinanceId
  ar_no: string
  customer_id?: FinanceId | null
  customer_name: string
  source_type?: string
  project_id?: FinanceId | null
  currency_code: string
  amount: string
  settled_amount: string
  outstanding: string
  bill_date: string
  due_date: string
  aging_bucket: string
  status: string
}

export interface FinancePayable {
  id: FinanceId
  ap_no: string
  supplier_id?: FinanceId | null
  supplier_name: string
  source_type?: string
  project_id?: FinanceId | null
  currency_code: string
  amount: string
  settled_amount: string
  outstanding: string
  bill_date: string
  due_date: string
  status: string
}

export interface FinanceAgingReport {
  type: string
  lines: Array<{ party_name: string; bucket: string; amount: string }>
  total: string
}

export interface FinanceWriteOffResult {
  id: FinanceId
  write_off_no: string
  source_id: FinanceId
  amount: string
}

export interface FinanceSettlementDoc {
  id: FinanceId
  doc_no: string
  status: string
  amount: string
  allocated_amount: string
  voucher_id?: FinanceId | null
  write_offs: FinanceWriteOffResult[]
}

export interface FinanceReceiptDoc {
  id: FinanceId
  receipt_no: string
  customer_id?: FinanceId | null
  currency_code: string
  amount: string
  pay_method?: string
  receipt_date?: string
  status: string
  voucher_id?: FinanceId | null
  created_at?: string
}

export interface FinancePaymentDoc {
  id: FinanceId
  payment_no: string
  supplier_id?: FinanceId | null
  currency_code: string
  amount: string
  pay_method?: string
  payment_date?: string
  status: string
  voucher_id?: FinanceId | null
  created_at?: string
}

export interface FinancePaymentPlanLine {
  payable_id: FinanceId
  ap_no: string
  supplier_name: string
  currency_code: string
  due_date: string
  planned_pay_date?: string
  amount: string
  settled_amount: string
  outstanding: string
}

export interface FinanceCurrency {
  id: FinanceId
  currency_code: string
  currency_name: string
  symbol?: string
  decimal_places: number
  status: string
}

export interface FinanceExchangeRate {
  id: FinanceId
  from_currency: string
  to_currency: string
  rate: string
  effective_date: string
  source?: string
}

export interface FinanceBusinessRule {
  id: FinanceId
  rule_code: string
  rule_name: string
  rule_type: string
  config?: Record<string, unknown>
  status: string
}

export interface FinanceExpenseItem {
  line_no: number
  expense_date: string
  expense_category: string
  subject_code?: string
  amount: string
  tax_amount: string
  invoice_id?: FinanceId | null
  attachment_id?: FinanceId | null
  remark?: string
}

export interface FinanceExpense {
  id: FinanceId
  claim_no: string
  claim_date: string
  applicant_id: FinanceId
  applicant_name: string
  dept_id?: FinanceId | null
  claim_type: string
  currency_code: string
  total_amount: string
  tax_amount: string
  budget_id?: FinanceId | null
  budget_occupied?: string
  project_id?: FinanceId | null
  status: string
  payable_id?: FinanceId | null
  voucher_id?: FinanceId | null
  submitted_at?: string
  paid_at?: string
  items: FinanceExpenseItem[]
}

export interface FinanceExpenseActionResult {
  id: FinanceId
  claim_no: string
  status: string
  workflow_instance_id?: string
}

export interface FinanceBudget {
  id: FinanceId
  budget_no: string
  budget_name: string
  period_year: number
  dimension: string
  dimension_id?: FinanceId | null
  subject_code?: string
  budget_amount: string
  occupied_amount: string
  executed_amount: string
  available: string
  control_mode: string
  status: string
}

export interface FinanceBudgetControlResult {
  budget_id?: FinanceId | null
  control_mode?: string
  budget_amount?: string
  occupied_amount?: string
  executed_amount?: string
  available?: string
  allowed: boolean
  message?: string
}

export interface FinanceInvoice {
  id: FinanceId
  invoice_code: string
  invoice_number: string
  invoice_type: string
  buyer_name?: string
  seller_name?: string
  amount: string
  tax_rate: string
  tax_amount: string
  total_amount: string
  invoice_date: string
  verify_status: string
  status: string
  source_biz_id?: FinanceId | null
}

export interface FinanceBankAccount {
  id: FinanceId
  account_no_masked: string
  account_name: string
  bank_name: string
  currency_code: string
  account_type?: string
  subject_code?: string
  balance: string
  status: string
}

export interface FinanceBankTransaction {
  id: FinanceId
  account_id: FinanceId
  txn_no: string
  txn_date: string
  direction: string
  amount: string
  balance?: string
  counterparty?: string
  summary?: string
  reconcile_status: string
  recon_id?: FinanceId | null
}

export interface FinanceReconciliation {
  id: FinanceId
  recon_no: string
  account_id: FinanceId
  period_code: string
  book_balance: string
  bank_balance: string
  unsettled_in: string
  unsettled_out: string
  adjusted_balance: string
  status: string
  unmatched_count: number
}

export interface FinanceProject {
  id: FinanceId
  project_no: string
  project_name: string
  project_type: string
  customer_id?: FinanceId | null
  customer_name?: string
  contract_no?: string
  contract_amount: string
  budget_amount: string
  estimated_total_cost: string
  actual_cost: string
  recognized_revenue: string
  start_date?: string
  end_date?: string
  manager_id?: FinanceId | null
  dept_id?: FinanceId | null
  currency_code: string
  status: string
  description?: string
}

export interface FinanceProjectMember {
  id: FinanceId
  employee_id: FinanceId
  employee_name: string
  member_role: string
  allocation_pct: string
  cost_rate: string
  join_date: string
  status: string
}

export interface FinanceMilestone {
  id: FinanceId
  milestone_name: string
  plan_date: string
  actual_date?: string
  deliverable?: string
  weight: string
  status: string
}

export interface FinanceTimesheet {
  id: FinanceId
  timesheet_no: string
  employee_id: FinanceId
  employee_name: string
  project_id: FinanceId
  work_date: string
  hours: string
  cost_rate: string
  cost_amount: string
  status: string
  reject_reason?: string
}

export interface FinanceProjectCost {
  id: FinanceId
  cost_no: string
  project_id: FinanceId
  cost_type: string
  source_biz_id?: FinanceId | null
  amount: string
  cost_date: string
  description?: string
}

export interface FinanceProjectSettlement {
  id: FinanceId
  settlement_no: string
  project_id: FinanceId
  settlement_amount: string
  settled_amount: string
  currency_code: string
  status: string
  receivable_id?: FinanceId | null
  voucher_id?: FinanceId | null
  settled_at?: string
}

export interface FinanceProjectDetail {
  project: FinanceProject
  members: FinanceProjectMember[]
  milestones: FinanceMilestone[]
  costs: FinanceProjectCost[]
  settlements: FinanceProjectSettlement[]
  timesheets: FinanceTimesheet[]
}

export interface FinanceProfitability {
  project_id: FinanceId
  contract_amount: string
  actual_cost: string
  recognized_revenue: string
  gross_profit: string
  gross_margin: string
  cost_budget_usage: string
  warnings: string[]
}

export interface FinanceAssetCategory {
  id: FinanceId
  category_code: string
  category_name: string
  useful_life_months: number
  residual_rate: string
  depreciation_method: string
  asset_account_code?: string
  depreciation_account_code?: string
  expense_account_code?: string
  status: string
}

export interface FinanceAssetCard {
  id: FinanceId
  asset_no: string
  asset_name: string
  category_id: FinanceId
  specification?: string
  location?: string
  dept_id?: FinanceId | null
  custodian_id?: FinanceId | null
  enable_date?: string
  original_value: string
  accumulated_depreciation: string
  net_value: string
  residual_rate?: string
  useful_life_months?: number
  depreciation_method?: string
  status: string
  it_asset_flag: boolean
}

export interface FinanceDepreciationResult {
  batch_no: string
  period_code: string
  asset_count: number
  total_amount: string
  voucher_id?: FinanceId | null
}

export interface FinanceDisposeResult {
  asset_id: FinanceId
  change_no: string
  gain_loss: string
  voucher_id?: FinanceId | null
  status: string
}

export interface FinanceStocktaking {
  id: FinanceId
  stocktaking_no: string
  stocktaking_name: string
  scope_type: string
  dept_id?: FinanceId | null
  plan_start_date?: string
  plan_end_date?: string
  actual_end_date?: string
  total_count: number
  normal_count: number
  surplus_count: number
  deficit_count: number
  status: string
}

export interface FinanceStocktakingItem {
  id: FinanceId
  asset_id?: FinanceId | null
  asset_no?: string
  asset_name?: string
  expected_location?: string
  actual_location?: string
  result?: string
  remark?: string
  counted_at?: string
}

export interface FinanceStocktakingDetail {
  stocktaking: FinanceStocktaking
  items: FinanceStocktakingItem[]
}

export interface FinanceItAsset {
  id: FinanceId
  asset_id: FinanceId
  device_type?: string
  brand_model?: string
  serial_no?: string
  ip_address?: string
  borrow_status: string
  borrower_id?: FinanceId | null
  borrow_date?: string
  due_date?: string
  cmdb_ci_id?: string
  sync_status?: string
}

export interface FinanceFactor {
  id: FinanceId
  factor_code: string
  factor_name: string
  scope: string
  activity_type: string
  activity_unit: string
  factor_value: string
  factor_unit: string
  factor_version: string
  source?: string
  effective_from?: string
  effective_to?: string
  status: string
}

export interface FinanceCarbonActivity {
  id: FinanceId
  activity_no: string
  org_id?: FinanceId | null
  dept_id?: FinanceId | null
  activity_type: string
  scope: string
  activity_date: string
  period_code: string
  amount: string
  unit: string
  factor_id?: FinanceId | null
  data_source?: string
  status: string
  verified_by?: number | null
  locked_by?: number | null
}

export interface FinanceCarbonCalculateResult {
  batch_no: string
  period_code: string
  activity_count: number
  total_emission: string
  by_scope: Record<string, string>
}

export interface FinanceCarbonTarget {
  id: FinanceId
  target_no: string
  target_name: string
  target_year: number
  base_year: number
  base_emission: string
  target_emission: string
  reduction_rate: string
  current_emission: string
  progress: string
  status: string
}

export interface FinanceEsgIndicator {
  id: FinanceId
  indicator_code: string
  indicator_name: string
  category: string
  unit: string
  period_year: number
  target_value: string
  actual_value: string
  data_source?: string
  status: string
}

export interface FinanceEsgReport {
  id: FinanceId
  report_no: string
  report_name: string
  period_year: number
  report_type: string
  report_boundary?: string
  status: string
  summary?: string
  published_at?: string
}

const BASE = '/api/finance/v1'

/** 云财（Finance）全部经网关 /api/finance/v1，权限由网关注入 X-Permissions。 */
export const financeApi = {
  ledgers: {
    list() {
      return get<FinanceLedger[]>(`${BASE}/ledgers`)
    },
    create(body: {
      ledger_code: string
      ledger_name: string
      company_name: string
      base_currency?: string
      fiscal_year_start_month?: number
      accounting_standard?: string
      tax_no?: string
    }) {
      return post<FinanceLedger>(`${BASE}/ledgers`, body)
    },
    subjects(ledgerId: FinanceId) {
      return get<FinanceSubject[]>(`${BASE}/ledgers/${ledgerId}/subjects`)
    },
    createSubject(
      ledgerId: FinanceId,
      body: {
        subject_code: string
        subject_name: string
        parent_id?: FinanceId | null
        subject_category: string
        balance_direction: string
        is_leaf?: boolean
        auxiliary_types?: string
      },
    ) {
      return post<FinanceSubject>(`${BASE}/ledgers/${ledgerId}/subjects`, body)
    },
    updateSubject(
      ledgerId: FinanceId,
      id: FinanceId,
      body: {
        subject_name?: string
        subject_category?: string
        balance_direction?: string
        is_leaf?: boolean
        auxiliary_types?: string
      },
    ) {
      return put<FinanceSubject>(`${BASE}/ledgers/${ledgerId}/subjects/${id}`, body)
    },
    disableSubject(ledgerId: FinanceId, id: FinanceId) {
      return post<FinanceSubject>(`${BASE}/ledgers/${ledgerId}/subjects/${id}/disable`)
    },
    periods(ledgerId: FinanceId) {
      return get<FinancePeriod[]>(`${BASE}/ledgers/${ledgerId}/periods`)
    },
    closePeriod(ledgerId: FinanceId, periodCode: string) {
      return post<{
        period_code: string
        status: string
        trial_debit: string
        trial_credit: string
        record_hash: string
        closed_at: string
      }>(`${BASE}/ledgers/${ledgerId}/periods/${periodCode}/close`)
    },
    reopenPeriod(ledgerId: FinanceId, periodCode: string) {
      return post<FinancePeriod>(`${BASE}/ledgers/${ledgerId}/periods/${periodCode}/reopen`)
    },
    sealPeriod(ledgerId: FinanceId, periodCode: string) {
      return post<FinancePeriod>(`${BASE}/ledgers/${ledgerId}/periods/${periodCode}/seal`)
    },
    trialBalance(ledgerId: FinanceId, periodCode: string) {
      return get<FinanceTrialBalance>(`${BASE}/ledgers/${ledgerId}/periods/${periodCode}/trial-balance`)
    },
    generalLedger(ledgerId: FinanceId, subjectCode: string, periodCode: string) {
      return get<Record<string, unknown>>(
        `${BASE}/ledgers/${ledgerId}/subjects/${subjectCode}/general-ledger`,
        { periodCode },
      )
    },
  },
  vouchers: {
    list(params: { ledgerId: FinanceId; periodCode?: string; status?: string; page?: number; size?: number }) {
      return get<PageResult<FinanceVoucher>>(`${BASE}/vouchers`, params)
    },
    detail(id: FinanceId) {
      return get<FinanceVoucher>(`${BASE}/vouchers/${id}`)
    },
    create(body: {
      ledger_id: FinanceId
      voucher_word?: string
      voucher_date: string
      summary?: string
      currency_code?: string
      source_type?: string
      source_biz_id?: FinanceId | null
      entries: Array<{
        summary?: string
        subject_code: string
        direction: string
        amount: string
        auxiliary?: Record<string, unknown>
        quantity?: string
        price?: string
      }>
    }) {
      return post<FinanceVoucher>(`${BASE}/vouchers`, body)
    },
    submit(id: FinanceId) {
      return post<FinanceVoucherActionResult>(`${BASE}/vouchers/${id}/submit`)
    },
    audit(id: FinanceId) {
      return post<FinanceVoucherActionResult>(`${BASE}/vouchers/${id}/audit`)
    },
    post(id: FinanceId) {
      return post<FinanceVoucherActionResult>(`${BASE}/vouchers/${id}/post`)
    },
    reverse(id: FinanceId) {
      return post<FinanceVoucherActionResult>(`${BASE}/vouchers/${id}/reverse`)
    },
    auditTrail(id: FinanceId) {
      return get<FinanceAuditTrail>(`${BASE}/vouchers/${id}/audit-trail`)
    },
    templates() {
      return get<FinanceVoucherTemplate[]>(`${BASE}/voucher-templates`)
    },
    createTemplate(body: {
      template_code: string
      template_name: string
      biz_type: string
      debit_subject_code: string
      credit_subject_code: string
      auxiliary_mapping?: Record<string, unknown>
      condition_expr?: string
    }) {
      return post<FinanceVoucherTemplate>(`${BASE}/voucher-templates`, body)
    },
    importVouchers(body: {
      ledger_id: FinanceId
      vouchers: Array<Record<string, unknown>>
    }) {
      return post<Record<string, unknown>>(`${BASE}/vouchers/import`, body)
    },
  },
  reports: {
    balanceSheet(ledgerId: FinanceId, periodCode: string) {
      return get<FinanceBalanceSheet>(`${BASE}/reports/balance-sheet`, { ledgerId, periodCode })
    },
    incomeStatement(ledgerId: FinanceId, periodCode: string) {
      return get<FinanceIncomeStatement>(`${BASE}/reports/income-statement`, { ledgerId, periodCode })
    },
  },
  arap: {
    receivables(params: { status?: string; page?: number; size?: number } = {}) {
      return get<FinanceReceivable[]>(`${BASE}/receivables`, params)
    },
    payables(params: { status?: string; page?: number; size?: number } = {}) {
      return get<FinancePayable[]>(`${BASE}/payables`, params)
    },
    receipts(params: { status?: string; from?: string; to?: string; limit?: number } = {}) {
      return get<FinanceReceiptDoc[]>(`${BASE}/receipts`, params)
    },
    payments(params: { status?: string; from?: string; to?: string; limit?: number } = {}) {
      return get<FinancePaymentDoc[]>(`${BASE}/payments`, params)
    },
    receive(body: {
      customer_id?: FinanceId | null
      customer_name?: string
      currency_code?: string
      amount: string
      pay_method?: string
      bank_account_id?: FinanceId | null
      receipt_date: string
      receivable_ids?: FinanceId[]
    }) {
      return post<FinanceSettlementDoc>(`${BASE}/receipts`, body)
    },
    pay(body: {
      supplier_id?: FinanceId | null
      supplier_name?: string
      currency_code?: string
      amount: string
      pay_method?: string
      bank_account_id?: FinanceId | null
      payment_date: string
      payable_ids?: FinanceId[]
    }) {
      return post<FinanceSettlementDoc>(`${BASE}/payments`, body)
    },
    writeOff(bizType: 'AR' | 'AP', sourceId: FinanceId, body: { amount: string; write_off_date: string }) {
      return post<FinanceWriteOffResult>(`${BASE}/arap/write-off?bizType=${bizType}&sourceId=${sourceId}`, body)
    },
    aging(type: 'AR' | 'AP' = 'AR') {
      return get<FinanceAgingReport>(`${BASE}/arap/aging`, { type })
    },
    paymentPlan(limit = 100) {
      return get<FinancePaymentPlanLine[]>(`${BASE}/payment-plan`, { limit })
    },
    currencies() {
      return get<FinanceCurrency[]>(`${BASE}/currencies`)
    },
    createCurrency(body: {
      currency_code: string
      currency_name: string
      symbol?: string
      decimal_places?: number
    }) {
      return post<FinanceCurrency>(`${BASE}/currencies`, body)
    },
    exchangeRates(limit = 100) {
      return get<FinanceExchangeRate[]>(`${BASE}/exchange-rates`, { limit })
    },
    createExchangeRate(body: {
      from_currency: string
      to_currency: string
      rate: string
      effective_date: string
      source?: string
    }) {
      return post<FinanceExchangeRate>(`${BASE}/exchange-rates`, body)
    },
    rules() {
      return get<FinanceBusinessRule[]>(`${BASE}/rules`)
    },
  },
  expenses: {
    list(params: { status?: string; page?: number; size?: number } = {}) {
      return get<FinanceExpense[]>(`${BASE}/expenses`, params)
    },
    my(params: { status?: string; page?: number; size?: number } = {}) {
      return get<FinanceExpense[]>(`${BASE}/expenses/my`, params)
    },
    detail(id: FinanceId) {
      return get<FinanceExpense>(`${BASE}/expenses/${id}`)
    },
    create(body: {
      // 雪花 ID 以字符串下发，后端 Long 兼容字符串入参
      applicant_id: string | number
      applicant_name: string
      dept_id?: FinanceId | null
      claim_type: string
      currency_code?: string
      project_id?: FinanceId | null
      claim_date: string
      items: Array<{
        expense_date: string
        expense_category: string
        subject_code?: string
        amount: string
        tax_amount?: string
        invoice_id?: FinanceId | null
        attachment_id?: FinanceId | null
        remark?: string
      }>
    }) {
      return post<FinanceExpense>(`${BASE}/expenses`, body)
    },
    submit(id: FinanceId) {
      return post<FinanceExpenseActionResult>(`${BASE}/expenses/${id}/submit`)
    },
    approve(id: FinanceId) {
      return post<FinanceExpenseActionResult>(`${BASE}/expenses/${id}/approve`)
    },
    reject(id: FinanceId, reason?: string) {
      return post<FinanceExpenseActionResult>(`${BASE}/expenses/${id}/reject`, { reason })
    },
    pay(id: FinanceId, body?: { payment_date?: string; pay_method?: string; bank_account_id?: FinanceId | null }) {
      return post<FinanceExpenseActionResult>(`${BASE}/expenses/${id}/pay`, body ?? {})
    },
    budgets(params: { periodYear?: number; dimension?: string; page?: number; size?: number } = {}) {
      return get<FinanceBudget[]>(`${BASE}/budgets`, params)
    },
    createBudget(body: {
      budget_no: string
      budget_name: string
      period_year: number
      dimension: string
      dimension_id?: FinanceId | null
      subject_code?: string
      budget_amount: string
      control_mode?: string
      currency_code?: string
    }) {
      return post<FinanceBudget>(`${BASE}/budgets`, body)
    },
    releaseBudget(id: FinanceId) {
      return post<FinanceBudget>(`${BASE}/budgets/${id}/release`)
    },
    checkBudget(body: {
      period_year: number
      dimension: string
      dimension_id?: FinanceId | null
      subject_code?: string
      amount: string
    }) {
      return post<FinanceBudgetControlResult>(`${BASE}/budgets/control-check`, body)
    },
    budgetAvailable(params: {
      periodYear?: number
      dimension?: string
      dimensionId?: FinanceId
      subjectCode?: string
    } = {}) {
      return get<Record<string, unknown>>(`${BASE}/budgets/available`, params)
    },
    invoices(params: { invoiceType?: string; page?: number; size?: number } = {}) {
      return get<FinanceInvoice[]>(`${BASE}/invoices`, params)
    },
    createInvoice(body: {
      invoice_code: string
      invoice_number: string
      invoice_type: string
      buyer_name?: string
      seller_name?: string
      amount: string
      tax_rate: string
      invoice_date: string
      source_biz_id?: FinanceId | null
      file_id?: FinanceId | null
    }) {
      return post<FinanceInvoice>(`${BASE}/invoices`, body)
    },
    verifyInvoice(id: FinanceId) {
      return post<FinanceInvoice>(`${BASE}/invoices/${id}/verify`)
    },
  },
  bank: {
    accounts() {
      return get<FinanceBankAccount[]>(`${BASE}/bank/accounts`)
    },
    createAccount(body: {
      account_no: string
      account_name: string
      bank_name: string
      bank_code?: string
      currency_code?: string
      account_type?: string
      subject_code?: string
      balance?: string
    }) {
      return post<FinanceBankAccount>(`${BASE}/bank/accounts`, body)
    },
    importTransactions(body: {
      account_id: FinanceId
      transactions: Array<{
        txn_no: string
        txn_date: string
        direction: string
        amount: string
        balance?: string
        counterparty?: string
        summary?: string
      }>
    }) {
      return post<{ account_id: FinanceId; imported: number; skipped: number }>(
        `${BASE}/bank/transactions/import`,
        body,
      )
    },
    transactions(accountId: FinanceId, params: { page?: number; size?: number } = {}) {
      return get<FinanceBankTransaction[]>(`${BASE}/bank/accounts/${accountId}/transactions`, params)
    },
    reconcile(body: { account_id: FinanceId; period_code: string }) {
      return post<FinanceReconciliation>(`${BASE}/bank/reconciliations`, body)
    },
    reconciliations(params: { page?: number; size?: number } = {}) {
      return get<FinanceReconciliation[]>(`${BASE}/bank/reconciliations`, params)
    },
  },
  projects: {
    list(params: { status?: string; page?: number; size?: number } = {}) {
      return get<FinanceProject[]>(`${BASE}/projects`, params)
    },
    detail(id: FinanceId) {
      return get<FinanceProjectDetail>(`${BASE}/projects/${id}`)
    },
    create(body: {
      project_name: string
      project_type: string
      customer_id?: FinanceId | null
      customer_name?: string
      contract_no?: string
      contract_amount?: string
      budget_amount?: string
      estimated_total_cost?: string
      start_date?: string
      end_date?: string
      manager_id?: FinanceId | null
      dept_id?: FinanceId | null
      currency_code?: string
      description?: string
    }) {
      return post<FinanceProject>(`${BASE}/projects`, body)
    },
    approve(id: FinanceId) {
      return post<{ id: FinanceId; project_no: string; status: string; workflow_instance_id?: string }>(
        `${BASE}/projects/${id}/approve`,
      )
    },
    members(id: FinanceId) {
      return get<FinanceProjectMember[]>(`${BASE}/projects/${id}/members`)
    },
    addMember(
      id: FinanceId,
      body: {
        employee_id: FinanceId
        employee_name: string
        member_role?: string
        allocation_pct?: string
        cost_rate?: string
        join_date?: string
      },
    ) {
      return post<FinanceProjectMember>(`${BASE}/projects/${id}/members`, body)
    },
    milestones(id: FinanceId) {
      return get<FinanceMilestone[]>(`${BASE}/projects/${id}/milestones`)
    },
    addMilestone(
      id: FinanceId,
      body: { milestone_name: string; plan_date: string; deliverable?: string; weight?: string },
    ) {
      return post<FinanceMilestone>(`${BASE}/projects/${id}/milestones`, body)
    },
    timesheets(id: FinanceId, params: { status?: string; page?: number; size?: number } = {}) {
      return get<FinanceTimesheet[]>(`${BASE}/projects/${id}/timesheets`, params)
    },
    myTimesheets(params: { status?: string; page?: number; size?: number } = {}) {
      return get<FinanceTimesheet[]>(`${BASE}/projects/timesheets/my`, params)
    },
    createTimesheet(
      id: FinanceId,
      body: {
        employee_id: string | number
        employee_name: string
        milestone_id?: FinanceId | null
        work_date: string
        hours: string
        task_desc?: string
      },
    ) {
      return post<FinanceTimesheet>(`${BASE}/projects/${id}/timesheets`, body)
    },
    approveTimesheets(timesheetIds: FinanceId[]) {
      return post<{
        approved_count: number
        rejected_count: number
        cost_total: string
        workflow_instance_id?: string
      }>(`${BASE}/projects/timesheets/approve`, { timesheet_ids: timesheetIds })
    },
    rejectTimesheet(timesheetId: FinanceId, reason?: string) {
      return post<FinanceTimesheet>(`${BASE}/projects/timesheets/${timesheetId}/reject`, { reason })
    },
    costs(id: FinanceId) {
      return get<FinanceProjectCost[]>(`${BASE}/projects/${id}/costs`)
    },
    collectCost(
      id: FinanceId,
      body: {
        cost_type: string
        source_biz_id?: FinanceId | null
        subject_code?: string
        amount: string
        cost_date: string
        description?: string
      },
    ) {
      return post<FinanceProjectCost>(`${BASE}/projects/${id}/costs`, body)
    },
    recognizeRevenue(id: FinanceId, body?: { period_code?: string; amount?: string }) {
      return post<{
        project_id: FinanceId
        method: string
        recognized_revenue: string
        delta: string
        voucher_id?: FinanceId | null
      }>(`${BASE}/projects/${id}/revenue`, body ?? {})
    },
    createSettlement(
      id: FinanceId,
      body: { settlement_amount: string; currency_code?: string; remark?: string },
    ) {
      return post<FinanceProjectSettlement>(`${BASE}/projects/${id}/settlements`, body)
    },
    approveSettlement(settlementId: FinanceId) {
      return post<FinanceProjectSettlement>(`${BASE}/projects/settlements/${settlementId}/approve`)
    },
    profitability(id: FinanceId) {
      return get<FinanceProfitability>(`${BASE}/projects/${id}/profitability`)
    },
  },
  assets: {
    categories() {
      return get<FinanceAssetCategory[]>(`${BASE}/assets/categories`)
    },
    createCategory(body: {
      category_code: string
      category_name: string
      useful_life_months: number
      residual_rate?: string
      depreciation_method?: string
      asset_account_code?: string
      depreciation_account_code?: string
      expense_account_code?: string
    }) {
      return post<FinanceAssetCategory>(`${BASE}/assets/categories`, body)
    },
    cards(params: { status?: string; page?: number; size?: number } = {}) {
      return get<FinanceAssetCard[]>(`${BASE}/assets/cards`, params)
    },
    createCard(body: {
      asset_name: string
      category_id: FinanceId
      specification?: string
      location?: string
      dept_id?: FinanceId | null
      custodian_id?: FinanceId | null
      enable_date?: string
      original_value: string
      it_asset_flag?: boolean
      source?: string
      device_type?: string
      brand_model?: string
      serial_no?: string
      ip_address?: string
      mac_address?: string
      os_version?: string
      software_list?: string[]
    }) {
      return post<FinanceAssetCard>(`${BASE}/assets/cards`, body)
    },
    runDepreciation(body: { ledger_id: FinanceId; period_code: string }) {
      return post<FinanceDepreciationResult>(`${BASE}/assets/depreciation/run`, body)
    },
    dispose(
      assetId: FinanceId,
      body?: { disposal_income?: string; disposal_date?: string; reason?: string },
    ) {
      return post<FinanceDisposeResult>(`${BASE}/assets/${assetId}/dispose`, body ?? {})
    },
    stocktakings(params: { page?: number; size?: number } = {}) {
      return get<FinanceStocktaking[]>(`${BASE}/assets/stocktakings`, params)
    },
    createStocktaking(body: {
      stocktaking_name: string
      scope_type: string
      dept_id?: FinanceId | null
      plan_start_date?: string
      plan_end_date?: string
    }) {
      return post<FinanceStocktaking>(`${BASE}/assets/stocktakings`, body)
    },
    stocktakingDetail(id: FinanceId) {
      return get<FinanceStocktakingDetail>(`${BASE}/assets/stocktakings/${id}`)
    },
    countStocktaking(
      id: FinanceId,
      body: {
        asset_id: FinanceId
        actual_location?: string
        actual_custodian_id?: FinanceId | null
        result?: string
        remark?: string
      },
    ) {
      return post<FinanceStocktakingDetail>(`${BASE}/assets/stocktakings/${id}/count`, body)
    },
    completeStocktaking(id: FinanceId) {
      return post<FinanceStocktaking>(`${BASE}/assets/stocktakings/${id}/complete`)
    },
    itAssets(params: { page?: number; size?: number } = {}) {
      return get<FinanceItAsset[]>(`${BASE}/assets/it`, params)
    },
    borrowIt(id: FinanceId, body: { borrower_id: FinanceId; due_date?: string }) {
      return post<FinanceItAsset>(`${BASE}/assets/it/${id}/borrow`, body)
    },
    returnIt(id: FinanceId) {
      return post<FinanceItAsset>(`${BASE}/assets/it/${id}/return`)
    },
    syncIt(id: FinanceId, body: { cmdb_ci_id?: string; success?: boolean }) {
      return post<FinanceItAsset>(`${BASE}/assets/it/${id}/sync`, body)
    },
  },
  carbon: {
    factors(params: { scope?: string; activityType?: string } = {}) {
      return get<FinanceFactor[]>(`${BASE}/carbon/factors`, params)
    },
    createFactor(body: {
      factor_code: string
      factor_name: string
      scope: string
      activity_type: string
      activity_unit: string
      factor_value: string
      factor_unit: string
      factor_version?: string
      source?: string
      effective_from?: string
      effective_to?: string
    }) {
      return post<FinanceFactor>(`${BASE}/carbon/factors`, body)
    },
    activities(params: {
      periodCode?: string
      scope?: string
      status?: string
      page?: number
      size?: number
    } = {}) {
      return get<FinanceCarbonActivity[]>(`${BASE}/carbon/activities`, params)
    },
    myActivities(params: {
      periodCode?: string
      scope?: string
      status?: string
      page?: number
      size?: number
    } = {}) {
      return get<FinanceCarbonActivity[]>(`${BASE}/carbon/activities/my`, params)
    },
    createActivity(body: {
      org_id?: FinanceId | null
      dept_id?: FinanceId | null
      activity_type: string
      scope: string
      activity_date: string
      amount: string
      unit: string
      factor_id?: FinanceId | null
      data_source?: string
      evidence_file_id?: FinanceId | null
    }) {
      return post<FinanceCarbonActivity>(`${BASE}/carbon/activities`, body)
    },
    verifyActivity(id: FinanceId) {
      return post<FinanceCarbonActivity>(`${BASE}/carbon/activities/${id}/verify`)
    },
    lockActivity(id: FinanceId) {
      return post<FinanceCarbonActivity>(`${BASE}/carbon/activities/${id}/lock`)
    },
    calculate(body: { org_id?: FinanceId | null; period_code: string; scope?: string; recalc?: boolean }) {
      return post<FinanceCarbonCalculateResult>(`${BASE}/carbon/calculate`, body)
    },
    targets() {
      return get<FinanceCarbonTarget[]>(`${BASE}/carbon/targets`)
    },
    createTarget(body: {
      target_name: string
      target_year: number
      base_year: number
      base_emission: string
      target_emission: string
      owner_id?: FinanceId | null
    }) {
      return post<FinanceCarbonTarget>(`${BASE}/carbon/targets`, body)
    },
    indicators(params: { periodYear?: number } = {}) {
      return get<FinanceEsgIndicator[]>(`${BASE}/esg/indicators`, params)
    },
    createIndicator(body: {
      indicator_code: string
      indicator_name: string
      category: string
      unit: string
      period_year: number
      target_value?: string
      actual_value?: string
      data_source?: string
    }) {
      return post<FinanceEsgIndicator>(`${BASE}/esg/indicators`, body)
    },
    reports(params: { page?: number; size?: number } = {}) {
      return get<FinanceEsgReport[]>(`${BASE}/esg/reports`, params)
    },
    createReport(body: {
      report_name: string
      period_year: number
      report_type: string
      report_boundary?: string
    }) {
      return post<FinanceEsgReport>(`${BASE}/esg/reports`, body)
    },
    publishReport(id: FinanceId) {
      return post<FinanceEsgReport>(`${BASE}/esg/reports/${id}/publish`)
    },
    exportReport(id: FinanceId) {
      return download(`${BASE}/esg/reports/${id}/export`, `esg-report-${id}.txt`)
    },
  },
}

// 统一导出的 request 便于页面按需发起一次性调用。
export { request }
