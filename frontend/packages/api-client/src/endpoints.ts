import { download, get, post, postForm, put, request } from './http'
import type {
  AlertRuleItem,
  AlertRuleView,
  AnalyticsResponse,
  AuditExportTaskView,
  AuditLogView,
  ChainVerifyResult,
  CreateRoleRequest,
  CreateTenantRequest,
  CreateTenantResult,
  CreateUserRequest,
  CreateUserResult,
  DashboardData,
  DatasetQueryResult,
  DefinitionView,
  DeployDefinitionRequest,
  FilePolicyRequest,
  FilePolicyView,
  IndexTaskView,
  LoginRequest,
  LowcodeAppView,
  LowcodeVersionView,
  MenuNode,
  MessageListResult,
  MetricCreateRequest,
  MetricView,
  MonitorStatsResult,
  OpenApiCallLogView,
  OpenApiKeyResponse,
  OpenAppCreateResponse,
  PageResult,
  PermissionNode,
  PreferenceView,
  PreviewResult,
  RoleView,
  TemplateRequest,
  TemplateView,
  TenantView,
  TokenPayload,
  UpdateTenantStatusRequest,
  UploadResult,
  UserInfo,
  UserView,
  VersionListResult,
  DownloadResult,
  AiModelView,
  AiPromptView,
  AiMeteringResult,
  AiModeValidateResult,
} from './types'

export const authApi = {
  login(body: LoginRequest) {
    return request<TokenPayload>('/api/hub/v1/sso/login', {
      method: 'POST',
      body: JSON.stringify(body),
      skipAuth: true,
    })
  },
  userinfo() {
    return get<UserInfo>('/api/hub/v1/sso/userinfo')
  },
  logout() {
    return post<unknown>('/api/hub/v1/sso/logout', { all_sessions: false }).catch(() => undefined)
  },
}

export const tenantApi = {
  list(params: { status?: string; keyword?: string; page?: number; size?: number }) {
    return get<PageResult<TenantView>>('/api/hub/v1/admin/tenants', params)
  },
  get(id: number) {
    return get<TenantView>(`/api/hub/v1/admin/tenants/${id}`)
  },
  current() {
    return get<TenantView>('/api/hub/v1/admin/tenants/current')
  },
  create(body: CreateTenantRequest) {
    return post<CreateTenantResult>('/api/hub/v1/admin/tenants', body)
  },
  updateStatus(id: number, body: UpdateTenantStatusRequest) {
    return post<TenantView>(`/api/hub/v1/admin/tenants/${id}/status`, body)
  },
}

export const iamApi = {
  listUsers(params: { keyword?: string; status?: string; page?: number; size?: number }) {
    return get<PageResult<UserView>>('/api/hub/v1/admin/users', params)
  },
  createUser(body: CreateUserRequest) {
    return post<CreateUserResult>('/api/hub/v1/admin/users', body)
  },
  assignRoles(userId: number, roleIds: number[]) {
    return put<UserView>(`/api/hub/v1/admin/users/${userId}/roles`, { role_ids: roleIds })
  },
  listRoles(params: { page?: number; size?: number } = {}) {
    return get<PageResult<RoleView>>('/api/hub/v1/admin/roles', params)
  },
  createRole(body: CreateRoleRequest) {
    return post<RoleView>('/api/hub/v1/admin/roles', body)
  },
  grantPermissions(roleId: number, permissionCodes: string[]) {
    return put<RoleView>(`/api/hub/v1/admin/roles/${roleId}/permissions`, { permission_codes: permissionCodes })
  },
  permissionTree() {
    return get<Record<string, PermissionNode[]>>('/api/hub/v1/admin/permissions/tree')
  },
  menus(clientType = 'ADMIN') {
    return get<MenuNode[]>('/api/hub/v1/admin/menus', { clientType })
  },
}

export const workflowApi = {
  list(params: { category?: string; status?: string; page?: number; size?: number }) {
    return get<PageResult<DefinitionView>>('/api/hub/v1/admin/workflow/definitions', params)
  },
  deploy(body: DeployDefinitionRequest) {
    return post<DefinitionView>('/api/hub/v1/admin/workflow/definitions', body)
  },
  stats(params: { from?: string; to?: string } = {}) {
    return get<MonitorStatsResult>('/api/hub/v1/admin/workflow/monitor/stats', params)
  },
}

export const messageApi = {
  listTemplates() {
    return get<TemplateView[]>('/api/hub/v1/admin/message/templates')
  },
  upsertTemplate(body: TemplateRequest) {
    return post<TemplateView>('/api/hub/v1/admin/message/templates', body)
  },
  list(params: { status?: string; channel?: string; page?: number; size?: number }) {
    return get<MessageListResult>('/api/hub/v1/messages', params)
  },
  read(id: number) {
    return post<unknown>(`/api/hub/v1/messages/${id}:read`)
  },
  preferences() {
    return get<PreferenceView[]>('/api/hub/v1/messages/preferences')
  },
  updatePreferences(items: PreferenceView[]) {
    return put<PreferenceView[]>('/api/hub/v1/messages/preferences', {
      items: items.map((item) => ({
        event_type: item.event_type,
        channels: item.channels,
        quiet_hours: { from: item.quiet_hours_from, to: item.quiet_hours_to },
      })),
    })
  },
}

export const fileApi = {
  getPolicy() {
    return get<FilePolicyView>('/api/hub/v1/admin/file/policies')
  },
  updatePolicy(body: FilePolicyRequest) {
    return put<FilePolicyView>('/api/hub/v1/admin/file/policies', body)
  },
  upload(file: File, meta: { biz_type?: string; biz_id?: string; sec_level?: number; md5?: string }) {
    const form = new FormData()
    form.append('file', file)
    if (meta.biz_type) form.append('bizType', meta.biz_type)
    if (meta.biz_id) form.append('bizId', meta.biz_id)
    if (meta.sec_level !== undefined) form.append('secLevel', String(meta.sec_level))
    if (meta.md5) form.append('md5', meta.md5)
    return postForm<UploadResult>('/api/hub/v1/files', form)
  },
  preview(id: number) {
    return get<PreviewResult>(`/api/hub/v1/files/${id}/preview`)
  },
  download(id: number, version?: number) {
    return get<DownloadResult>(`/api/hub/v1/files/${id}/download`, { version })
  },
  versions(id: number) {
    return get<VersionListResult>(`/api/hub/v1/files/${id}/versions`)
  },
}

export const auditApi = {
  list(params: {
    event_type?: string
    operator_id?: number
    target_type?: string
    target_id?: string
    request_id?: string
    result?: string
    from?: string
    to?: string
    page?: number
    size?: number
  }) {
    return get<PageResult<AuditLogView>>('/api/hub/v1/admin/audits', params)
  },
  get(id: number) {
    return get<AuditLogView>(`/api/hub/v1/admin/audits/${id}`)
  },
  verifyChain(params: { tenant_id?: number; from?: string; to?: string } = {}) {
    return get<ChainVerifyResult>('/api/hub/v1/admin/audits/chain/verify', params)
  },
  export(body: { from?: string; to?: string; event_type?: string; format?: string }) {
    return post<AuditExportTaskView>('/api/hub/v1/admin/audit/logs:export', body)
  },
  exportTask(taskId: number) {
    return get<AuditExportTaskView>(`/api/hub/v1/admin/audit/export-tasks/${taskId}`)
  },
  downloadExport(taskId: number) {
    return download(`/api/hub/v1/admin/audit/export-tasks/${taskId}/download`, `audit-${taskId}.csv`)
  },
  myRecords(params: { from?: string; to?: string; page?: number; size?: number }) {
    return get<PageResult<AuditLogView>>('/api/hub/v1/audit/my-records', params)
  },
  alertRules() {
    return get<AlertRuleView[]>('/api/hub/v1/admin/audit/alert-rules')
  },
  updateAlertRules(items: AlertRuleItem[]) {
    return put<AlertRuleView[]>('/api/hub/v1/admin/audit/alert-rules', { items })
  },
}

export const searchApi = {
  createIndexTask(body: { index_name: string; task_type: string }) {
    return post<IndexTaskView>('/api/hub/v1/admin/search/index-tasks', body)
  },
  getIndexTask(taskId: number) {
    return get<IndexTaskView>(`/api/hub/v1/admin/search/index-tasks/${taskId}`)
  },
  analytics(params: { type?: string; from?: string; to?: string }) {
    return get<AnalyticsResponse>('/api/hub/v1/admin/search/analytics', params)
  },
  query(body: { query: string; domains?: string[]; mode?: string; page?: number; size?: number }) {
    return post<unknown>('/api/hub/v1/search/query', body)
  },
}

export const openApi = {
  createApp(body: { name: string; owner_type?: string; scopes?: string[]; redirect_uris?: string[] }) {
    return post<OpenAppCreateResponse>('/api/hub/v1/admin/openapi/apps', body)
  },
  reviewApp(id: number, body: { action: string; reason?: string }) {
    return post<unknown>(`/api/hub/v1/admin/openapi/apps/${id}:review`, body)
  },
  issueKey(id: number, body: { action?: string; grace_hours?: number } = {}) {
    return post<OpenApiKeyResponse>(`/api/hub/v1/admin/openapi/apps/${id}/keys`, body)
  },
  upsertQuota(body: {
    app_id: number
    items: Array<{ api_path: string; qps_limit?: number; daily_limit?: number }>
  }) {
    return put<unknown>('/api/hub/v1/admin/openapi/quotas', body)
  },
  callLogs(params: {
    app_id?: number
    api_path?: string
    status?: number
    from?: string
    to?: string
    page?: number
    size?: number
  }) {
    return get<PageResult<OpenApiCallLogView>>('/api/hub/v1/admin/openapi/call-logs', params)
  },
}

export const reportApi = {
  listMetrics(params: { category?: string; status?: string; page?: number; size?: number }) {
    return get<PageResult<MetricView>>('/api/hub/v1/admin/metrics', params)
  },
  createMetric(body: MetricCreateRequest) {
    return post<MetricView>('/api/hub/v1/admin/metrics', body)
  },
  queryDataset(id: number, body: { dimensions?: string[]; measures?: string[]; filters?: unknown[]; limit?: number }) {
    return post<DatasetQueryResult>(`/api/hub/v1/report/datasets/${id}/query`, body)
  },
  dashboardData(id: number, body: { chart_ids?: number[]; params?: Record<string, unknown> }) {
    return post<DashboardData>(`/api/hub/v1/report/dashboards/${id}/data`, body)
  },
  exportReport(body: { report_id: number; format: string }) {
    return post<{ file_id: string; download_url: string; watermark: boolean; format: string }>(
      '/api/hub/v1/report:export',
      body,
    )
  },
}

export const lowcodeApi = {
  list(params: { env?: string; status?: string; page?: number; size?: number }) {
    return get<PageResult<LowcodeAppView>>('/api/hub/v1/admin/lowcode/apps', params)
  },
  create(body: {
    code: string
    name: string
    app_type: string
    schema?: unknown
    data_model?: unknown
    env?: string
  }) {
    return post<LowcodeAppView>('/api/hub/v1/admin/lowcode/apps', body)
  },
  publish(id: number) {
    return post<{ app_id: number; env: string; status: string; process_instance_id?: string; version_no: number }>(
      `/api/hub/v1/admin/lowcode/apps/${id}:publish`,
    )
  },
  versions(id: number) {
    return get<LowcodeVersionView[]>(`/api/hub/v1/admin/lowcode/apps/${id}/versions`)
  },
  rollback(id: number, versionNo: number) {
    return post<LowcodeAppView>(`/api/hub/v1/admin/lowcode/apps/${id}:rollback`, { version_no: versionNo })
  },
}

export const aiApi = {
  createModel(body: {
    code: string
    provider_type: string
    model_type: string
    endpoint: string
    api_key: string
    routing_weight?: number
  }) {
    return post<AiModelView>('/api/hub/v1/admin/ai/models', body)
  },
  createPrompt(body: {
    code: string
    name: string
    scene: string
    system_prompt?: string
    user_prompt?: string
    variables?: string[]
  }) {
    return post<AiPromptView>('/api/hub/v1/admin/ai/prompts', body)
  },
  putGrants(body: {
    items: Array<{
      host_product: string
      capability: string
      enabled?: boolean
      quota_qps?: number
      quota_token_month?: number
      billing_mode?: string
    }>
  }) {
    return put<unknown>('/api/hub/v1/admin/ai/capability-grants', body)
  },
  metering(params: { mode?: string; host_product?: string; from?: string; to?: string }) {
    return get<AiMeteringResult>('/api/hub/v1/admin/ai/metering', params)
  },
  validateMode() {
    return post<AiModeValidateResult>('/api/hub/v1/admin/ai/mode:validate')
  },
}

export const gatewayApi = {
  health() {
    return get<{ status: string; components?: Record<string, unknown> }>('/actuator/health')
  },
}

export * from './http'
export * from './types'
