import { get, post, put } from './http'
import type { PageResult } from '@fenglin/shared'

/** 云服后端返回动态 Map（SNAKE_CASE），此处统一用宽松记录类型承接。 */
export type ServiceRecord = Record<string, any>
export type ServicePage = PageResult<ServiceRecord>
/** 雪花 ID 以字符串下发，仅做透传，禁止转 number（超过 2^53 会丢精度）。 */
export type ServiceId = string | number

const BASE = '/api/service/v1'

export const serviceApi = {
  tickets: {
    list(params: {
      status?: string
      ticketType?: string
      priority?: string
      assigneeId?: ServiceId
      requesterId?: ServiceId
      catalogItemId?: ServiceId
      keyword?: string
      page?: number
      size?: number
    } = {}) {
      return get<ServicePage>(`${BASE}/tickets`, params)
    },
    get(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/tickets/${id}`)
    },
    create(body: {
      title: string
      description?: string
      ticket_type?: string
      priority?: string
      source?: string
      catalog_item_id?: ServiceId | null
      requester_id?: string | number | null
      impact?: string
      urgency?: string
      contact?: string
      assignee_id?: ServiceId | null
      assignee_group_id?: ServiceId | null
      sla_policy_id?: ServiceId | null
      tags?: string[]
    }) {
      return post<ServiceRecord>(`${BASE}/tickets`, body)
    },
    importBatch(items: ServiceRecord[]) {
      return post<ServiceRecord>(`${BASE}/tickets/import`, { items })
    },
    assign(id: ServiceId, body: { assignee_id: ServiceId; assignee_group_id?: ServiceId | null; reason?: string }) {
      return post<ServiceRecord>(`${BASE}/tickets/${id}/assign`, body)
    },
    transition(id: ServiceId, toStatus: string, reason?: string) {
      return post<ServiceRecord>(`${BASE}/tickets/${id}/transition`, { to_status: toStatus, reason })
    },
    suspend(id: ServiceId, reason?: string) {
      return post<ServiceRecord>(`${BASE}/tickets/${id}/suspend`, { reason })
    },
    resume(id: ServiceId, reason?: string) {
      return post<ServiceRecord>(`${BASE}/tickets/${id}/resume`, { reason })
    },
    comments(id: ServiceId) {
      return get<ServiceRecord[]>(`${BASE}/tickets/${id}/comments`)
    },
    addComment(id: ServiceId, body: { content: string; internal?: boolean; comment_type?: string; attachment_ids?: ServiceId[] }) {
      return post<ServiceRecord>(`${BASE}/tickets/${id}/comments`, body)
    },
    flowLogs(id: ServiceId) {
      return get<ServiceRecord[]>(`${BASE}/tickets/${id}/flow-logs`)
    },
    sla(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/tickets/${id}/sla`)
    },
    breaches(params: { status?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/sla/breaches`, params)
    },
    myTickets(params: { page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/my/tickets`, params)
    },
    deskTickets(params: { assigneeId?: ServiceId; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/desk/tickets`, params)
    },
    selfClose(id: ServiceId, reason?: string) {
      return post<ServiceRecord>(`${BASE}/tickets/${id}/self-close`, { reason })
    },
    selfReopen(id: ServiceId, reason?: string) {
      return post<ServiceRecord>(`${BASE}/tickets/${id}/self-reopen`, { reason })
    },
  },
  catalog: {
    tree() {
      return get<ServiceRecord[]>(`${BASE}/catalog/tree`)
    },
    items(catalogId?: ServiceId) {
      return get<ServiceRecord[]>(`${BASE}/catalog/items`, { catalogId })
    },
    item(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/catalog/items/${id}`)
    },
    apply(id: ServiceId, body: ServiceRecord) {
      return post<ServiceRecord>(`${BASE}/catalog/items/${id}/apply`, body)
    },
  },
  kb: {
    categories() {
      return get<ServiceRecord[]>(`${BASE}/kb/categories`)
    },
    createCategory(body: { name: string; parent_id?: ServiceId | null; sort_no?: number }) {
      return post<ServiceRecord>(`${BASE}/kb/categories`, body)
    },
    articles(params: {
      status?: string
      categoryId?: ServiceId
      keyword?: string
      page?: number
      size?: number
    } = {}) {
      return get<ServicePage>(`${BASE}/kb/articles`, params)
    },
    article(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/kb/articles/${id}`)
    },
    saveArticle(body: {
      id?: ServiceId
      category_id?: ServiceId | null
      title: string
      summary?: string
      content: string
      status?: string
      visibility?: string
      keywords?: string[]
    }) {
      return post<ServiceRecord>(`${BASE}/kb/articles`, body)
    },
    feedback(id: ServiceId, body: { helpful: boolean; reason?: string; ticket_id?: ServiceId | null }) {
      return post<ServiceRecord>(`${BASE}/kb/articles/${id}/feedback`, body)
    },
    suggest(params: { ticketId?: ServiceId; keyword?: string; limit?: number } = {}) {
      return get<ServiceRecord[]>(`${BASE}/kb/suggest`, params)
    },
  },
  csat: {
    submit(body: { ticket_id: ServiceId; score: number; comment?: string; channel?: string; tags?: string[] }) {
      return post<ServiceRecord>(`${BASE}/csat`, body)
    },
  },
  problems: {
    list(params: { status?: string; keyword?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/problems`, params)
    },
    get(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/problems/${id}`)
    },
    create(body: {
      title: string
      description?: string
      root_cause?: string
      workaround?: string
      known_error?: boolean
      status?: string
      owner_id?: ServiceId | null
      ticket_ids?: ServiceId[]
    }) {
      return post<ServiceRecord>(`${BASE}/problems`, body)
    },
    aggregate(id: ServiceId, body: { ticket_ids: ServiceId[]; status?: string; root_cause?: string; workaround?: string; known_error?: boolean }) {
      return post<ServiceRecord>(`${BASE}/problems/${id}/aggregate`, body)
    },
  },
  changes: {
    list(params: { status?: string; keyword?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/changes`, params)
    },
    get(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/changes/${id}`)
    },
    create(body: {
      title: string
      change_type?: string
      plan?: string
      rollback_plan?: string
      risk_level?: string
      planned_start?: string
      planned_end?: string
      approval_flow_key?: string
      owner_id?: ServiceId | null
    }) {
      return post<ServiceRecord>(`${BASE}/changes`, body)
    },
    approve(id: ServiceId, body: { approved?: boolean; comment?: string }) {
      return post<ServiceRecord>(`${BASE}/changes/${id}/approve`, body)
    },
  },
  assets: {
    list(params: { status?: string; ownerId?: string | number; assetType?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/assets`, params)
    },
    get(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/assets/${id}`)
    },
    create(body: {
      asset_no?: string
      name: string
      asset_type?: string
      owner_id?: ServiceId | null
      dept_id?: ServiceId | null
      status?: string
      ext_ref?: string
    }) {
      return post<ServiceRecord>(`${BASE}/assets`, body)
    },
    borrow(id: ServiceId, body: { owner_id: ServiceId; days?: number }) {
      return post<ServiceRecord>(`${BASE}/assets/${id}/borrow`, body)
    },
    returnAsset(id: ServiceId, body?: ServiceRecord) {
      return post<ServiceRecord>(`${BASE}/assets/${id}/return`, body ?? {})
    },
    returnSelf(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/assets/${id}/return-self`)
    },
    reminders() {
      return get<ServiceRecord>(`${BASE}/assets/reminders`)
    },
    myAssets(params: { status?: string; assetType?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/assets/my`, params)
    },
    myReminders() {
      return get<ServiceRecord>(`${BASE}/assets/reminders/my`)
    },
  },
  admin: {
    config(configType: string) {
      return get<ServiceRecord[]>(`${BASE}/admin/config/${configType}`)
    },
    saveConfig(
      kind:
        | 'ticket-types'
        | 'groups'
        | 'duty'
        | 'dispatch-rule'
        | 'asset-reminder'
        | 'retry-policy'
        | 'service-report',
      body: ServiceRecord,
    ) {
      return put<ServiceRecord>(`${BASE}/admin/config/${kind}`, body)
    },
    saveCatalog(body: {
      id?: ServiceId
      parent_id?: ServiceId | null
      name: string
      sort_no?: number
      status?: string
    }) {
      return post<ServiceRecord>(`${BASE}/admin/catalog`, body)
    },
    saveCatalogItem(body: {
      id?: ServiceId
      catalog_id: ServiceId
      code?: string
      name: string
      description?: string
      ticket_type?: string
      sla_policy_id?: ServiceId | null
      need_approval?: boolean
      sort_no?: number
      status?: string
    }) {
      return post<ServiceRecord>(`${BASE}/admin/catalog`, { items: [body] })
    },
    slaPolicies() {
      return get<ServiceRecord[]>(`${BASE}/admin/sla-policies`)
    },
    saveSlaPolicy(body: {
      id?: ServiceId
      name: string
      priority?: string
      ticket_type?: string
      response_minutes?: number
      resolve_minutes?: number
      business_hours?: string
      enabled?: boolean
      status?: string
    }) {
      return post<ServiceRecord>(`${BASE}/admin/sla-policies`, body)
    },
  },
  mining: {
    importEvents(events: ServiceRecord[]) {
      return post<ServiceRecord>(`${BASE}/mining/events`, { events })
    },
    discover(body: { name?: string; result_type?: string; source_system?: string; period_start?: string; period_end?: string }) {
      return post<ServiceRecord>(`${BASE}/mining/models/discover`, body)
    },
    models(params: { resultType?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/mining/models`, params)
    },
    model(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/mining/models/${id}`)
    },
    conformance(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/mining/models/${id}/conformance`)
    },
    bottlenecks(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/mining/models/${id}/bottlenecks`)
    },
    resourceLoad(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/mining/models/${id}/resource-load`)
    },
    candidates(id: ServiceId) {
      return get<ServiceRecord[]>(`${BASE}/mining/models/${id}/candidates`)
    },
    toAutomation(id: ServiceId, body: ServiceRecord = {}) {
      return post<ServiceRecord>(`${BASE}/mining/candidates/${id}/to-automation`, body)
    },
    archive(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/mining/models/${id}/archive`)
    },
  },
  rpa: {
    robots(params: { page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/rpa/robots`, params)
    },
    registerRobot(body: {
      robot_code: string
      name: string
      robot_type?: string
      host?: string
      capabilities?: ServiceRecord
      max_concurrency?: number
      agent_version?: string
    }) {
      return post<ServiceRecord>(`${BASE}/rpa/robots`, body)
    },
    governRobot(body: { robot_id: ServiceId; status: string; reason?: string }) {
      return post<ServiceRecord>(`${BASE}/rpa/robots/govern`, body)
    },
    credentials() {
      return get<ServiceRecord[]>(`${BASE}/rpa/credentials`)
    },
    saveCredential(body: {
      name: string
      secret: string
      cred_type?: string
      target_system?: string
      kms_key_id?: string
      kms_version?: string
      rotate_due_at?: string
    }) {
      return post<ServiceRecord>(`${BASE}/rpa/credentials`, body)
    },
    credentialToken(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/rpa/credentials/${id}/token`)
    },
    automations(params: { status?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/rpa/automations`, params)
    },
    automation(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/rpa/automations/${id}`)
    },
    saveAutomation(body: {
      flow_code: string
      name: string
      app_id?: ServiceId | null
      version_no?: number
      nodes?: ServiceRecord[]
      params_schema?: ServiceRecord
      target_whitelist?: string[]
      timeout_sec?: number
      retry_policy?: ServiceRecord
      source_candidate_id?: ServiceId | null
      status?: string
    }) {
      return post<ServiceRecord>(`${BASE}/rpa/automations`, body)
    },
    publish(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/rpa/automations/${id}/publish`)
    },
    updateWhitelist(id: ServiceId, body: { target_whitelist: string[] }) {
      return put<ServiceRecord>(`${BASE}/rpa/automations/${id}/whitelist`, body)
    },
    schedules() {
      return get<ServiceRecord[]>(`${BASE}/rpa/schedules`)
    },
    createSchedule(body: {
      name: string
      automation_flow_id: ServiceId
      trigger_type?: string
      cron_expr?: string
      event_topic?: string
      robot_selector?: ServiceRecord
      concurrency_limit?: number
      timeout_sec?: number
      enabled?: boolean
    }) {
      return post<ServiceRecord>(`${BASE}/rpa/schedules`, body)
    },
    tasks(params: { status?: string; robotId?: ServiceId; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/rpa/tasks`, params)
    },
    task(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/rpa/tasks/${id}`)
    },
    taskLogs(id: ServiceId) {
      return get<ServiceRecord[]>(`${BASE}/rpa/tasks/${id}/logs`)
    },
    triggerTask(body: {
      automation_flow_id: ServiceId
      robot_id?: ServiceId | null
      trigger_type?: string
      priority?: number
      input?: ServiceRecord
      max_retry?: number
      business_key?: string
    }) {
      return post<ServiceRecord>(`${BASE}/rpa/tasks`, body)
    },
    retry(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/rpa/tasks/${id}/retry`)
    },
    taskAction(id: ServiceId, body: { action: string; status?: string; output?: ServiceRecord; error_code?: string; error_msg?: string }) {
      return post<ServiceRecord>(`${BASE}/rpa/tasks/${id}/actions`, body)
    },
  },
  lowcode: {
    apps(params: { status?: string; keyword?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/lowcode/apps`, params)
    },
    adminApps(params: { status?: string; keyword?: string; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/admin/lowcode/apps`, params)
    },
    app(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/lowcode/apps/${id}`)
    },
    createApp(body: { app_code?: string; name: string; description?: string; owner_id?: ServiceId | null }) {
      return post<ServiceRecord>(`${BASE}/lowcode/apps`, body)
    },
    copyApp(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/lowcode/apps/${id}/copy`)
    },
    archiveApp(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/lowcode/apps/${id}/archive`)
    },
    freezeApp(id: ServiceId) {
      return post<ServiceRecord>(`${BASE}/lowcode/apps/${id}/freeze`)
    },
    releases(params: { appId?: ServiceId; page?: number; size?: number } = {}) {
      return get<ServicePage>(`${BASE}/lowcode/releases`, params)
    },
    release(id: ServiceId) {
      return get<ServiceRecord>(`${BASE}/lowcode/releases/${id}`)
    },
    submitRelease(appId: ServiceId, body: { to_env?: string; release_note?: string }) {
      return post<ServiceRecord>(`${BASE}/lowcode/apps/${appId}/releases`, body)
    },
    approveRelease(id: ServiceId, body: { approved?: boolean; comment?: string }) {
      return post<ServiceRecord>(`${BASE}/lowcode/releases/${id}/approve`, body)
    },
    templates() {
      return get<ServiceRecord[]>(`${BASE}/lowcode/templates`)
    },
  },
  reports: {
    tickets() {
      return get<ServiceRecord>(`${BASE}/reports/tickets`)
    },
    sla() {
      return get<ServiceRecord>(`${BASE}/reports/sla`)
    },
    workload() {
      return get<ServiceRecord[]>(`${BASE}/reports/workload`)
    },
    csat() {
      return get<ServiceRecord>(`${BASE}/reports/csat`)
    },
    rpa() {
      return get<ServiceRecord>(`${BASE}/reports/rpa`)
    },
    utilization() {
      return get<ServiceRecord[]>(`${BASE}/reports/robot-utilization`)
    },
  },
}
