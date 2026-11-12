import { get, post, put, request } from './http'
import type { PageResult } from '@fenglin/shared'

/** 云法后端统一返回动态 Map（SNAKE_CASE），此处用宽松记录类型承接。 */
export type LegalRecord = Record<string, any>
export type LegalPage = PageResult<LegalRecord>
/** 雪花 ID 以字符串下发，仅做透传，禁止转 number（超过 2^53 会丢精度）。 */
export type LegalId = string | number

const BASE = '/api/legal/v1'

function idemKey(): string {
  const c = globalThis.crypto
  if (c && 'randomUUID' in c) return c.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

/** 后端写接口统一校验 Idempotency-Key，缺失会返回 400。 */
function postIdem<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Idempotency-Key': idemKey() },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export const legalApi = {
  contracts: {
    list(params: {
      status?: string
      category?: string
      owner_id?: LegalId
      org_id?: LegalId
      keyword?: string
      page?: number
      size?: number
    } = {}) {
      return get<LegalPage>(`${BASE}/contracts`, params)
    },
    search(params: { keyword?: string; status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/contracts/search`, params)
    },
    get(id: LegalId) {
      return get<LegalRecord>(`${BASE}/contracts/${id}`)
    },
    create(body: LegalRecord) {
      return postIdem<LegalRecord>(`${BASE}/contracts`, body)
    },
    update(id: LegalId, body: LegalRecord) {
      return put<LegalRecord>(`${BASE}/contracts/${id}`, body)
    },
    remove(id: LegalId) {
      return request<LegalRecord>(`${BASE}/contracts/${id}`, { method: 'DELETE' })
    },
    saveVersion(id: LegalId, body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/contracts/${id}/versions`, body)
    },
    saveClauses(id: LegalId, body: LegalRecord) {
      return put<LegalRecord[]>(`${BASE}/contracts/${id}/clauses`, body)
    },
    submit(id: LegalId, body: LegalRecord = {}) {
      return postIdem<LegalRecord>(`${BASE}/contracts/${id}/submit`, body)
    },
    approve(id: LegalId, body: LegalRecord = {}) {
      return postIdem<LegalRecord>(`${BASE}/contracts/${id}/approve`, body)
    },
    archive(id: LegalId) {
      return postIdem<LegalRecord>(`${BASE}/contracts/${id}/archive`)
    },
    terminate(id: LegalId, body: LegalRecord = {}) {
      return postIdem<LegalRecord>(`${BASE}/contracts/${id}/terminate`, body)
    },
    verifyIntegrity(id: LegalId) {
      return post<LegalRecord>(`${BASE}/contracts/${id}/verify-integrity`)
    },
  },

  templates: {
    list(params: { category?: string; status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/contract-templates`, params)
    },
    create(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/contract-templates`, body)
    },
    publish(id: LegalId, body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/contract-templates/${id}/versions`, body)
    },
    saveVariables(id: LegalId, body: LegalRecord) {
      return put<LegalRecord[]>(`${BASE}/admin/contract-templates/${id}/variables`, body)
    },
  },

  parties: {
    list(params: { keyword?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/contract-parties`, params)
    },
    create(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/contract-parties`, body)
    },
    applyMobile(id: LegalId, body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/contract-parties/${id}/mobile-requests`, body)
    },
    myMobileRequests(params: { status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/contract-parties/mobile-requests/my`, params)
    },
    mobileRequests(params: { status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/admin/contract-parties/mobile-requests`, params)
    },
    reviewMobileRequest(id: LegalId, body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/contract-parties/mobile-requests/${id}/review`, body)
    },
    mobile(id: LegalId) {
      return get<LegalRecord>(`${BASE}/contract-parties/${id}/mobile`)
    },
  },

  performance: {
    list(params: { contract_id?: LegalId; status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/performance-nodes`, params)
    },
    create(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/performance-nodes`, body)
    },
    updateStatus(id: LegalId, status: string) {
      return post<LegalRecord>(`${BASE}/performance-nodes/${id}/status`, { status })
    },
  },

  reminders: {
    list(params: { contract_id?: LegalId; status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/expiry-reminders`, params)
    },
    create(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/expiry-reminders`, body)
    },
  },

  seals: {
    list(params: { seal_type?: string; status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/admin/seals`, params)
    },
    available(params: { seal_type?: string } = {}) {
      return get<LegalRecord[]>(`${BASE}/seals/available`, params)
    },
    create(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/seals`, body)
    },
    revoke(id: LegalId) {
      return post<LegalRecord>(`${BASE}/admin/seals/${id}/revoke`)
    },
  },

  sealApplications: {
    list(params: { status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/seal-applications`, params)
    },
    create(body: LegalRecord) {
      return postIdem<LegalRecord>(`${BASE}/seal-applications`, body)
    },
    approve(id: LegalId, body: LegalRecord) {
      return postIdem<LegalRecord>(`${BASE}/seal-applications/${id}/approve`, body)
    },
    execute(id: LegalId, body: LegalRecord) {
      return postIdem<LegalRecord>(`${BASE}/seal-applications/${id}/execute`, body)
    },
  },

  sealRecords: {
    list(params: { contract_id?: LegalId; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/seal-records`, params)
    },
  },

  signatories: {
    list(contract_id: LegalId) {
      return get<LegalRecord[]>(`${BASE}/contract-signatories`, { contract_id: contract_id })
    },
    create(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/contract-signatories`, body)
    },
  },

  sign: {
    initiate(contract_id: LegalId, body: LegalRecord = {}) {
      return postIdem<LegalRecord>(`${BASE}/contracts/${contract_id}/sign/initiate`, body)
    },
    tasks(params: { page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/sign-tasks`, params)
    },
    sign(taskId: LegalId, body: LegalRecord) {
      return postIdem<LegalRecord>(`${BASE}/sign-tasks/${taskId}/sign`, body)
    },
    reject(taskId: LegalId, body: LegalRecord) {
      return postIdem<LegalRecord>(`${BASE}/sign-tasks/${taskId}/reject`, body)
    },
    records(params: { contract_id?: LegalId; status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/sign-records`, params)
    },
  },

  evidences: {
    list(params: { contract_id?: LegalId; evidence_type?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/evidences`, params)
    },
    verify(id: LegalId) {
      return post<LegalRecord>(`${BASE}/evidences/${id}/verify`)
    },
    chainVerify(contract_id: LegalId) {
      return get<LegalRecord>(`${BASE}/evidences/chain-verify`, { contract_id: contract_id })
    },
  },

  dlp: {
    rules(params: { data_level?: string; enabled?: boolean; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/admin/dlp/rules`, params)
    },
    createRule(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/dlp/rules`, body)
    },
    testRule(id: LegalId, body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/dlp/rules/${id}/test`, body)
    },
    policies(params: { data_level?: string; action?: string; enabled?: boolean; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/admin/dlp/policies`, params)
    },
    createPolicy(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/dlp/policies`, body)
    },
    channels(params: { policy_id?: LegalId } = {}) {
      return get<LegalRecord[]>(`${BASE}/admin/dlp/channels`, params)
    },
    saveChannels(body: LegalRecord) {
      return put<LegalRecord>(`${BASE}/admin/dlp/channels`, body)
    },
    whitelist(params: { channel_code?: string; status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/admin/dlp/whitelist`, params)
    },
    createWhitelist(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/dlp/whitelist`, body)
    },
    events(params: {
      data_level?: string
      channel_code?: string
      status?: string
      event_type?: string
      page?: number
      size?: number
    } = {}) {
      return get<LegalPage>(`${BASE}/dlp/events`, params)
    },
    event(id: LegalId) {
      return get<LegalRecord>(`${BASE}/dlp/events/${id}`)
    },
    handleEvent(id: LegalId, body: LegalRecord) {
      return postIdem<LegalRecord>(`${BASE}/dlp/events/${id}/handle`, body)
    },
    releases(params: {
      applicant_id?: LegalId
      status?: string
      from?: string
      to?: string
      page?: number
      size?: number
    } = {}) {
      return get<LegalPage>(`${BASE}/dlp/releases`, params)
    },
    release(id: LegalId) {
      return get<LegalRecord>(`${BASE}/dlp/releases/${id}`)
    },
    releaseProgress(id: LegalId) {
      return get<LegalRecord>(`${BASE}/dlp/releases/${id}/progress`)
    },
    createRelease(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/dlp/releases`, body)
    },
    approveRelease(id: LegalId, body: LegalRecord) {
      return postIdem<LegalRecord>(`${BASE}/dlp/releases/${id}/approve`, body)
    },
    watermarks(params: { biz_type?: string; trace_status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/admin/dlp/watermarks`, params)
    },
    createWatermark(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/dlp/watermarks`, body)
    },
    trace(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/dlp/watermarks/trace`, body)
    },
    terminals(params: { status?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/admin/dlp/terminals`, params)
    },
    auditLogs(params: { action?: string; page?: number; size?: number } = {}) {
      return get<LegalPage>(`${BASE}/admin/dlp/audit-logs`, params)
    },
    verifyAudit(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/dlp/audit-logs/verify`, body)
    },
  },

  reports: {
    contracts(dim?: string) {
      return get<LegalRecord>(`${BASE}/admin/reports/contracts`, { dim })
    },
    dlp(dim?: string) {
      return get<LegalRecord>(`${BASE}/admin/reports/dlp`, { dim })
    },
    signCompliance() {
      return get<LegalRecord>(`${BASE}/admin/reports/sign-compliance`)
    },
  },

  config: {
    reminder() {
      return get<LegalRecord>(`${BASE}/admin/config/reminder`)
    },
    saveReminder(body: LegalRecord) {
      return put<LegalRecord>(`${BASE}/admin/config/reminder`, body)
    },
    contractFields() {
      return get<LegalRecord>(`${BASE}/admin/config/contract-fields`)
    },
    saveContractFields(body: LegalRecord) {
      return put<LegalRecord>(`${BASE}/admin/config/contract-fields`, body)
    },
    dictionary(type: string) {
      return get<LegalRecord>(`${BASE}/admin/config/dictionary`, { type })
    },
    saveDictionary(body: LegalRecord) {
      return put<LegalRecord>(`${BASE}/admin/config/dictionary`, body)
    },
    caProviders() {
      return get<LegalRecord[]>(`${BASE}/admin/config/ca-providers`)
    },
    saveCaProvider(body: LegalRecord) {
      return post<LegalRecord>(`${BASE}/admin/config/ca-providers`, body)
    },
  },
}

