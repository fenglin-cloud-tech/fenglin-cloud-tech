import type { MenuNode, PageResult, TokenPayload, UserInfo } from '@fenglin/shared'

export type { MenuNode, PageResult, TokenPayload, UserInfo }

export interface LoginRequest {
  tenant_code: string
  username: string
  password: string
  client_type: string
}

export interface TenantView {
  id: string
  code: string
  name: string
  status: string
  contact_name?: string
  expire_at?: string
  created_at?: string
}

export interface CreateTenantRequest {
  name: string
  code?: string
  contact_name?: string
  expire_at?: string
  package_code?: string
  trial_days?: number
  admin_username: string
  admin_mobile?: string
  admin_password?: string
}

export interface CreateTenantResult {
  tenant_id: string
  code: string
  status: string
  initial_password?: string
}

export interface UpdateTenantStatusRequest {
  action: string
  reason?: string
}

export interface UserView {
  user_id: string
  username: string
  employee_no?: string
  real_name?: string
  nickname?: string
  mobile?: string
  email?: string
  primary_org_id?: string
  primary_position_id?: string
  employee_id?: string
  status: string
  roles: string[]
  version?: number
}

export interface UpdateUserRequest {
  nickname?: string
  mobile?: string
  email?: string
  primary_org_id?: string | number
  status?: string
  employee_no?: string
  employee_id?: string | number
  unbind_employee_id?: boolean
  version?: number
}

export interface CreateUserRequest {
  real_name: string
  username: string
  employee_no?: string
  mobile: string
  email?: string
  primary_org_id: string | number
  primary_position_id?: string | number
  role_ids?: (string | number)[]
  password?: string
}

export interface CreateUserResult {
  user_id: string
  employee_no?: string
  status: string
  initial_password?: string
}

export interface OrgNode {
  id: string
  parent_id?: string
  name: string
  org_type: string
  leader_id?: string
  sort: number
  status: string
  path_code?: string
  member_count?: number
  children?: OrgNode[]
}

export interface CreateOrgRequest {
  parent_id?: string | number | null
  name: string
  org_type: string
  sort?: number
  leader_id?: string | number
}

export interface UpdateOrgRequest {
  name?: string
  sort?: number
  leader_id?: string | number
  status?: string
  version?: number
}

export interface MoveOrgRequest {
  parent_id?: string | number | null
}

export interface RoleView {
  role_id: string
  code: string
  name: string
  role_type: string
  data_scope?: string
  status: string
  permission_codes: string[]
}

export interface CreateRoleRequest {
  code: string
  name: string
  role_type?: string
  data_scope?: string
  parent_id?: number
}

export interface PermissionNode {
  code: string
  name: string
  type: string
  domain?: string
  parent_code?: string
  children?: PermissionNode[]
}

export interface DefinitionView {
  definition_id: string
  process_key: string
  name: string
  category?: string
  def_version: number
  status: string
  form_key?: string
  created_at?: string
}

export interface DeployDefinitionRequest {
  process_key: string
  name: string
  category?: string
  bpmn_xml: string
  form_key?: string
}

export interface MonitorStatsResult {
  running: number
  completed: number
  timeout: number
  avg_duration_minutes: number
  by_node: Array<{ node_key: string; avg_minutes: number; count: number }>
}

export interface TemplateView {
  code: string
  name: string
  channel: string
  title_tpl?: string
  content_tpl?: string
  variables?: string
  locale?: string
  status: string
}

export interface TemplateRequest {
  code: string
  name: string
  channel: string
  title_tpl?: string
  content_tpl?: string
  variables?: string[]
  locale?: string
  status?: string
}

export interface MessageView {
  message_id: string
  message_no: string
  channel: string
  template_code?: string
  title: string
  content: string
  biz_type?: string
  biz_id?: string
  status: string
  sent_at?: string
  read_at?: string
  created_at?: string
}

export interface MessageChannelView {
  id: number
  channel_type: string
  name: string
  config?: string
  enabled: boolean
  is_default: boolean
  remark?: string
}

export interface MessageChannelRequest {
  id?: number
  channel_type: string
  name: string
  config?: Record<string, unknown>
  enabled?: boolean
  is_default?: boolean
  remark?: string
}

export interface MessageListResult {
  records: MessageView[]
  unread_count: number
  page: number
  size: number
  total: number
}

export interface PreferenceView {
  event_type: string
  channels: string[]
  quiet_hours_from?: string
  quiet_hours_to?: string
}

export interface FilePolicyView {
  allowed_ext: string[]
  max_size_mb: number
  watermark: { enabled: boolean; sec_level_from: number }
  storage_quota_gb: number
}

export interface FilePolicyRequest {
  allowed_ext: string[]
  max_size_mb: number
  watermark: { enabled: boolean; sec_level_from: number }
  storage_quota_gb: number
}

export interface UploadResult {
  file_id: string
  file_no: string
  status: string
  object_key: string
}

export interface PreviewResult {
  preview_type: string
  preview_url: string
  watermark_text?: string
}

export interface DownloadResult {
  download_url: string
  watermark: boolean
}

export interface VersionListResult {
  records: Array<{ ver_no: number; size: number; md5: string; created_by?: string; created_at?: string }>
}

export interface AuditLogView {
  log_id: string
  tenant_id: string
  log_no: string
  event_type: string
  module: string
  action: string
  operator_id?: string
  operator_name?: string
  target_type?: string
  target_id?: string
  request_id?: string
  trace_id?: string
  client_type?: string
  result: string
  risk_level?: string
  detail?: unknown
  occurred_at?: string
}

export interface AuditExportTaskView {
  task_id: string
  task_no: string
  format: string
  event_type?: string
  from?: string
  to?: string
  status: string
  row_count?: number
  error_msg?: string
  created_at?: string
}

export interface AlertRuleView {
  id: string
  code: string
  event_type: string
  threshold: number
  receiver_role: string
  channels: string[]
  enabled: boolean
}

export interface AlertRuleItem {
  code: string
  event_type: string
  threshold: number
  receiver_role: string
  channels: string[]
}

export interface ChainVerifyResult {
  tenant_id: string
  checked: number
  audit_log_count: number
  unchained_count: number
  valid: boolean
  /** PASS=完整；UNVERIFIABLE=仅基线前存在不可验证区间（非篡改）；FAIL=基线后缺口/断链/哈希不符 */
  status: 'PASS' | 'UNVERIFIABLE' | 'FAIL'
  broken_seq?: number
  broken_log_id?: string
  gap_from_log_id?: string
  gap_to_log_id?: string
  baseline_log_id?: string
  message?: string
}

export interface IndexTaskView {
  task_id: string
  index_name: string
  task_type: string
  status: string
  doc_count?: number
  error_msg?: string
}

export interface AnalyticsResponse {
  type: string
  items: Array<{ keyword: string; count: number }>
}

export interface OpenAppCreateResponse {
  app_id: string
  code: string
  review_status: string
}

export interface OpenApiKeyResponse {
  app_key: string
  app_secret: string
  issued_at?: string
  secret_fingerprint?: string
}

export interface OpenApiCallLogView {
  id: string
  app_id: string
  app_key: string
  api_path: string
  method: string
  http_status: number
  biz_code?: string
  latency_ms?: number
  request_id?: string
  called_at?: string
}

export interface MetricView {
  id: string
  code: string
  name: string
  metric_type: string
  formula: string
  unit?: string
  grain?: string
  dimensions?: string[]
  lineage?: unknown
  status: string
}

export interface MetricCreateRequest {
  code: string
  name: string
  metric_type: string
  formula: string
  unit?: string
  grain?: string
  dimensions?: string[]
  lineage?: Record<string, unknown>
}

export interface DatasetQueryResult {
  columns: string[]
  rows: Array<Array<unknown>>
}

export interface DashboardData {
  charts: Array<{ chart_id: string; columns: string[]; rows: Array<Array<unknown>>; degraded: boolean; error_code?: string }>
  degraded: boolean
}

export interface LowcodeAppView {
  id: string
  code: string
  name: string
  app_type: string
  schema?: unknown
  data_model?: unknown
  env: string
  status: string
  published_at?: string
  latest_version: number
}

export interface LowcodeVersionView {
  id: string
  version_no: number
  status: string
  process_instance_id?: string
  published_at?: string
  schema?: unknown
}

export interface AiModelView {
  id: string
  code: string
  provider_type: string
  model_type: string
  endpoint: string
  api_key_fingerprint: string
  routing_weight: number
}

export interface AiPromptView {
  id: string
  code: string
  prompt_version: number
  name: string
  scene: string
  system_prompt?: string
  user_prompt?: string
  variables?: string[]
  status: string
}

export interface AiMeteringResult {
  records: Array<{
    host_product: string
    capability: string
    mode: string
    call_count: number
    token_in: number
    token_out: number
    cost: number
    stat_date: string
  }>
}

export interface AiModeValidateResult {
  mode: string
  consistent: boolean
  issues: string[]
}

// ---------- 开放平台开发者门户（HUB-API-1001/1003/1004/1005/1006） ----------

export interface PortalAppView {
  app_id: string
  code: string
  name: string
  status: string
  review_status: string
  call_today: number
  quota_daily?: number | null
}

export interface PortalAppDetail {
  app_id: string
  code: string
  name: string
  owner_type: string
  review_status: string
  status: string
  scopes: string[]
  redirect_uris: string[]
  created_at?: string
  call_today: number
  quota_daily?: number | null
}

/** 密钥列表：AppSecret 仅返回掩码，明文只在签发响应中出现一次。 */
export interface PortalKeyView {
  id: string
  app_key: string
  secret_masked: string
  secret_fingerprint?: string
  status: string
  expire_at?: string
  created_at?: string
}

export interface PortalQuotaView {
  app_id: string
  api_path: string
  qps_limit?: number | null
  daily_limit?: number | null
  used_today: number
  used_date?: string | null
  current_qps?: number | null
  limit_status: 'NORMAL' | 'WARNING' | 'EXCEEDED'
}

/** OPEN-03：配额自助申请单（门户“我的申请”与平台审批共用）。 */
export interface PortalQuotaRequestView {
  id: string
  app_id: string
  api_path: string
  requested_qps?: number | null
  requested_daily?: number | null
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  applied_by: string
  decided_by?: string | null
  decided_at?: string | null
  decision_comment?: string | null
  created_at?: string | null
}

export interface PortalScopeView {
  code: string
  name: string
  domain: string
  subscribed: boolean
}

/** 平台已发布的开放接口文档；subscribed 表示当前应用是否已订阅 required_scope。 */
export interface PortalApiDocView {
  code: string
  method: string
  path: string
  name: string
  domain: string
  summary?: string
  description?: string
  required_scope?: string | null
  request_schema?: string | null
  response_schema?: string | null
  sample_request?: string | null
  sample_response?: string | null
  sandbox_enabled: boolean
  subscribed: boolean
}

/** 沙箱调用结果：只回显签名要素与响应，不含 AppSecret。 */
export interface SandboxCallResult {
  api_code: string
  method: string
  path: string
  app_key: string
  signature: string
  nonce: string
  timestamp: number
  request_id: string
  http_status: number
  latency_ms: number
  response_body: string
  body_truncated: boolean
}

// ---------- 云客 CRM（/api/crm/v1） ----------

/**
 * CRM 主键为雪花 ID（19 位），后端按字符串序列化以避免 JavaScript 精度丢失。
 * 前端不应对该值做数值运算，仅透传或展示。
 */
export type CrmId = string | number

export interface LeadView {
  lead_id: CrmId
  lead_no: string
  name: string
  contact_name?: string
  mobile_masked?: string
  email?: string
  industry?: string
  region_code?: string
  source?: string
  score: number
  grade?: string
  status: string
  owner_id?: string | number
  dept_id?: number
  last_activity_at?: string
  converted_customer_id?: CrmId
  created_at?: string
}

export interface LeadDetailView {
  lead: LeadView
  activities: ActivityView[]
}

export interface LeadCreateRequest {
  name: string
  contact_name?: string
  mobile?: string
  email?: string
  industry?: string
  region_code?: string
  source?: string
  owner_id?: string | number
  grade?: string
  remark?: string
}

export interface LeadAssignRequest {
  owner_id?: string | number
  dept_id?: string | number
  reason?: string
}

export interface LeadConvertRequest {
  create_customer?: boolean
  create_contact?: boolean
  create_opportunity?: boolean
  opportunity_name?: string
  amount?: number
  stage?: string
}

export interface LeadConvertResult {
  lead_id: CrmId
  customer_id: CrmId
  contact_id?: CrmId
  opportunity_id?: CrmId
  opportunity_no?: string
}

export interface CustomerView {
  customer_id: CrmId
  customer_no: string
  name: string
  short_name?: string
  uscc?: string
  industry?: string
  scale?: string
  region_code?: string
  grade?: string
  segment?: string
  owner_id?: string | number
  csm_id?: number
  dept_id?: number
  mobile_masked?: string
  annual_revenue?: number
  arr?: number
  status: string
  created_at?: string
}

export interface ContactView {
  contact_id: CrmId
  customer_id: CrmId
  name: string
  position?: string
  role_type?: string
  mobile_masked?: string
  email?: string
  wechat?: string
  influence: number
  is_key: boolean
}

export interface CustomerDetailView {
  customer: CustomerView
  contacts: ContactView[]
  opportunities: OpportunityView[]
  activities: ActivityView[]
}

export interface CustomerCreateRequest {
  name: string
  short_name?: string
  uscc?: string
  industry?: string
  scale?: string
  region_code?: string
  grade?: string
  segment?: string
  owner_id?: string | number
  csm_id?: number
  mobile?: string
  annual_revenue?: number
  arr?: number
  remark?: string
  source_lead_id?: CrmId
}

export interface CustomerOwnerRequest {
  owner_id?: string | number
  reason?: string
}

export interface ContactCreateRequest {
  customer_id: CrmId
  name: string
  position?: string
  role_type?: string
  mobile?: string
  email?: string
  wechat?: string
  influence?: number
  is_key?: boolean
}

export interface OpportunityView {
  opportunity_id: CrmId
  opportunity_no: string
  name: string
  customer_id: CrmId
  stage: string
  win_rate?: number
  amount?: number
  weighted_amount?: number
  expected_close_date?: string
  owner_id?: string | number
  status: string
  next_step?: string
  lost_reason?: string
  created_at?: string
}

export interface OpportunityCreateRequest {
  name: string
  customer_id: CrmId
  amount?: number
  stage?: string
  expected_close_date?: string
  source?: string
  next_step?: string
  win_rate?: number
  owner_id?: string | number
}

export interface StageChangeRequest {
  to_stage: string
  opinion?: string
  amount?: number
  expected_close_date?: string
  lost_reason?: string
  next_step?: string
}

export interface StageLogView {
  from_stage?: string
  to_stage: string
  amount_snapshot?: number
  changed_by?: number
  changed_at?: string
  opinion?: string
}

export interface PipelineStage {
  stage: string
  count: number
  amount?: number
  weighted_amount?: number
}

export interface PipelineView {
  stages: PipelineStage[]
  total_weighted_amount?: number
  open_count: number
}

export interface QuotationItemRequest {
  product_code: string
  product_name?: string
  unit?: string
  quantity: number
  unit_price: number
  discount_rate?: number
}

export interface QuotationItemView extends QuotationItemRequest {
  line_no: number
  amount?: number
}

export interface QuotationCreateRequest {
  opportunity_id?: CrmId
  customer_id: CrmId
  tax_rate?: number
  valid_until?: string
  remark?: string
  items: QuotationItemRequest[]
}

export interface QuotationView {
  quotation_id: CrmId
  quotation_no: string
  opportunity_id?: CrmId
  customer_id: CrmId
  version_no: number
  total_amount?: number
  discount_rate?: number
  tax_rate?: number
  valid_until?: string
  status: string
  approve_instance_id?: string
  items: QuotationItemView[]
  created_at?: string
}

export interface QuotationListItemView {
  quotation_id: CrmId
  quotation_no: string
  opportunity_id?: CrmId
  customer_id: CrmId
  customer_name?: string
  version_no: number
  total_amount?: number
  status: string
  owner_id?: string | number
  created_at?: string
}

export interface QuotationSubmitResult {
  quotation_id: CrmId
  status: string
  approve_instance_id?: string
  approval_required: boolean
}

export interface PaymentPlanRequest {
  period_no?: number
  due_date: string
  amount: number
}

export interface PaymentPlanView {
  plan_id: CrmId
  period_no: number
  due_date?: string
  amount?: number
  paid_amount?: number
  remaining_amount?: number
  status: string
  overdue_days: number
  collection_flag?: string
  collection_note?: string
}

export interface ContractCreateRequest {
  customer_id: CrmId
  sla_level?: string
  service_package?: string
  amount?: number
  start_date: string
  end_date: string
  payment_plans?: PaymentPlanRequest[]
}

export interface ContractView {
  contract_id: CrmId
  contract_no: string
  customer_id: CrmId
  sla_level?: string
  service_package?: string
  amount?: number
  start_date?: string
  end_date?: string
  status: string
  payment_plans: PaymentPlanView[]
  created_at?: string
}

export interface ContractListItemView {
  contract_id: CrmId
  contract_no: string
  customer_id: CrmId
  customer_name?: string
  sla_level?: string
  service_package?: string
  amount?: number
  start_date?: string
  end_date?: string
  status: string
  owner_id?: string | number
  created_at?: string
}

export interface ContractAmendRequest {
  amount?: number
  sla_level?: string
  service_package?: string
  end_date?: string
  reason?: string
  payment_plans?: PaymentPlanRequest[]
}

export interface ContractRenewRequest {
  start_date: string
  end_date: string
  amount?: number
  sla_level?: string
  service_package?: string
  reason?: string
  payment_plans?: PaymentPlanRequest[]
}

export interface ContractChangeView {
  change_id: CrmId
  change_no: string
  contract_id: CrmId
  change_type: string
  before_amount?: number
  after_amount?: number
  before_start_date?: string
  before_end_date?: string
  after_start_date?: string
  after_end_date?: string
  reason?: string
  new_contract_id?: CrmId
  status: string
  created_at?: string
}

export interface QuotationConvertRequest {
  sla_level?: string
  service_package?: string
  start_date: string
  end_date: string
  reason?: string
  payment_plans?: PaymentPlanRequest[]
}

export interface PaymentRegisterRequest {
  plan_id?: CrmId
  amount: number
  received_date?: string
  method?: string
  voucher_no?: string
  remark?: string
}

export interface PaymentRecordView {
  payment_id: CrmId
  contract_id: CrmId
  plan_id: CrmId
  amount?: number
  received_date?: string
  method?: string
  voucher_no?: string
  remark?: string
  status: string
  created_at?: string
}

export interface OverduePlanView {
  plan_id: CrmId
  contract_id: CrmId
  contract_no: string
  customer_id: CrmId
  customer_name?: string
  period_no: number
  due_date?: string
  amount?: number
  paid_amount?: number
  remaining_amount?: number
  overdue_days: number
  collection_flag?: string
  collection_note?: string
}

export interface PaymentReminderView extends OverduePlanView {
  state: string
}

export interface CollectionFlagRequest {
  collection_flag: string
  note?: string
}

export interface ActivityView {
  activity_id: CrmId
  biz_type: string
  biz_id: CrmId
  activity_type: string
  subject?: string
  content?: string
  duration_min?: number
  result?: string
  next_plan?: string
  occurred_at?: string
  owner_id?: string | number
}

export interface ActivityCreateRequest {
  biz_type: string
  biz_id: CrmId
  activity_type: string
  subject?: string
  content?: string
  duration_min?: number
  result?: string
  next_plan?: string
  occurred_at?: string
}
