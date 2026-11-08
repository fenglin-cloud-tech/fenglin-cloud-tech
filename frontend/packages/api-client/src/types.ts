import type { MenuNode, PageResult, TokenPayload, UserInfo } from '@fenglin/shared'

export type { MenuNode, PageResult, TokenPayload, UserInfo }

export interface LoginRequest {
  tenant_code: string
  username: string
  password: string
  client_type: string
}

export interface TenantView {
  id: number
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
  tenant_id: number
  code: string
  status: string
  initial_password?: string
}

export interface UpdateTenantStatusRequest {
  action: string
  reason?: string
}

export interface UserView {
  user_id: number
  username: string
  employee_no?: string
  real_name?: string
  primary_org_id?: number
  primary_position_id?: number
  status: string
  roles: string[]
}

export interface CreateUserRequest {
  real_name: string
  username: string
  employee_no?: string
  mobile: string
  email?: string
  primary_org_id: number
  primary_position_id?: number
  role_ids?: number[]
  password?: string
}

export interface CreateUserResult {
  user_id: number
  employee_no?: string
  status: string
  initial_password?: string
}

export interface RoleView {
  role_id: number
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
  definition_id: number
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
  message_id: number
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
  file_id: number
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
  records: Array<{ ver_no: number; size: number; md5: string; created_by?: number; created_at?: string }>
}

export interface AuditLogView {
  log_id: number
  tenant_id: number
  log_no: string
  event_type: string
  module: string
  action: string
  operator_id?: number
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
  task_id: number
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
  id: number
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
  tenant_id: number
  checked: number
  valid: boolean
  broken_seq?: number
  broken_log_id?: number
  message?: string
}

export interface IndexTaskView {
  task_id: number
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
  app_id: number
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
  id: number
  app_id: number
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
  id: number
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
  charts: Array<{ chart_id: number; columns: string[]; rows: Array<Array<unknown>>; degraded: boolean; error_code?: string }>
  degraded: boolean
}

export interface LowcodeAppView {
  id: number
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
  id: number
  version_no: number
  status: string
  process_instance_id?: string
  published_at?: string
  schema?: unknown
}

export interface AiModelView {
  id: number
  code: string
  provider_type: string
  model_type: string
  endpoint: string
  api_key_fingerprint: string
  routing_weight: number
}

export interface AiPromptView {
  id: number
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
