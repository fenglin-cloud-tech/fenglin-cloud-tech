import { get, post, put } from './http'
import type { PageResult } from './http'

export type AiRecord = Record<string, any>
export type AiPage = PageResult<AiRecord>

const BASE = '/api/intelligence/v1'

export type AiPageQuery = {
  page?: number
  size?: number
}

export const intelligenceApi = {
  // ---------- 运营概览 ----------
  overview() {
    return get<AiRecord>(`${BASE}/report/overview`)
  },
  searchAnalytics(limit?: number) {
    return get<AiRecord>(`${BASE}/admin/search/analytics`, { limit })
  },

  // ---------- 运营：能力授权 / 计量 / 双模式 ----------
  admin: {
    grants(hostProduct?: string) {
      return get<AiRecord>(`${BASE}/admin/ai/capability-grants`, { host_product: hostProduct })
    },
    metering(params?: { mode?: string; host_product?: string; from?: string; to?: string }) {
      return get<AiRecord>(`${BASE}/admin/ai/metering`, params)
    },
    validateMode() {
      return post<AiRecord>(`${BASE}/admin/ai/mode:validate`)
    },
    mode() {
      return get<AiRecord>(`${BASE}/admin/ai/mode`)
    },
  },

  // ---------- 知识库 ----------
  kb: {
    list(params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/knowledge-bases`, params)
    },
    create(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/knowledge-bases`, body)
    },
  },

  // ---------- 数据源（知识） ----------
  dataSources: {
    list(params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/admin/data-sources`, params)
    },
    create(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/data-sources`, body)
    },
  },

  // ---------- 文档 / 索引 / 审核 ----------
  documents: {
    list(kbId: number, params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/knowledge-bases/${kbId}/documents`, params)
    },
    detail(id: number) {
      return get<AiRecord>(`${BASE}/admin/documents/${id}`)
    },
    create(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/documents`, body)
    },
    submitReview(id: number) {
      return post<AiRecord>(`${BASE}/admin/documents/${id}/review:submit`)
    },
    review(id: number, action: string) {
      return post<AiRecord>(`${BASE}/admin/documents/${id}/review`, { action })
    },
    index(id: number) {
      return post<AiRecord>(`${BASE}/admin/documents/${id}/index`)
    },
  },

  // ---------- 检索 ----------
  search: {
    hybrid(body: AiRecord) {
      return post<AiRecord>(`${BASE}/search/hybrid`, body)
    },
    secure(body: AiRecord) {
      return post<AiRecord>(`${BASE}/search/secure`, body)
    },
    preview(docId: number, secLevel?: number) {
      return get<AiRecord>(`${BASE}/documents/${docId}/preview`, { sec_level: secLevel })
    },
  },

  // ---------- 知识图谱 ----------
  kg: {
    entities(params?: { kb_id?: number; keyword?: string; limit?: number }) {
      return get<AiRecord[]>(`${BASE}/kg/entities`, params)
    },
    paths(params: { from_id: number; to_id: number; max_depth?: number }) {
      return get<AiRecord>(`${BASE}/kg/paths`, params)
    },
    createEntity(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/kg/entities`, body)
    },
    createRelation(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/kg/relations`, body)
    },
  },

  // ---------- Copilot ----------
  copilot: {
    conversations(params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/copilot/conversations`, params)
    },
    createConversation(body?: AiRecord) {
      return post<AiRecord>(`${BASE}/copilot/conversations`, body ?? {})
    },
    messages(id: number, params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/copilot/conversations/${id}/messages`, params)
    },
    send(id: number, body: AiRecord) {
      return post<AiRecord>(`${BASE}/copilot/conversations/${id}/messages`, body)
    },
    feedback(messageId: number, body: AiRecord) {
      return post<AiRecord>(`${BASE}/copilot/messages/${messageId}/feedback`, body)
    },
    write(body: AiRecord) {
      return post<AiRecord>(`${BASE}/copilot/text:write`, body)
    },
    summarize(body: AiRecord) {
      return post<AiRecord>(`${BASE}/copilot/text:summarize`, body)
    },
    translate(body: AiRecord) {
      return post<AiRecord>(`${BASE}/copilot/text:translate`, body)
    },
  },

  // ---------- Agent ----------
  agents: {
    list(params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/agents`, params)
    },
    create(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/agents`, body)
    },
    run(code: string, body?: AiRecord) {
      return post<AiRecord>(`${BASE}/agents/${encodeURIComponent(code)}/run`, body ?? {})
    },
  },

  // ---------- 护栏（平台 AI 中台托管，本域仅确认） ----------
  guardrails: {
    ack(body: AiRecord) {
      return put<AiRecord>(`${BASE}/admin/guardrails`, body)
    },
  },

  // ---------- BI 指标 / 数据集 ----------
  metrics: {
    list(params?: AiPageQuery & { keyword?: string }) {
      return get<AiPage>(`${BASE}/metrics`, params)
    },
    create(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/metrics`, body)
    },
  },

  datasets: {
    list(params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/datasets`, params)
    },
    create(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/datasets`, body)
    },
    fields(datasetId: number) {
      return get<AiRecord[]>(`${BASE}/datasets/${datasetId}/fields`)
    },
    addField(datasetId: number, body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/datasets/${datasetId}/fields`, body)
    },
  },

  // ---------- BI 看板 / 图表 ----------
  dashboards: {
    list(params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/dashboards`, params)
    },
    create(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/dashboards`, body)
    },
    detail(id: number) {
      return get<AiRecord>(`${BASE}/dashboards/${id}`)
    },
    createChart(id: number, body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/dashboards/${id}/charts`, body)
    },
  },

  // ---------- BI 洞察 / 归因 / 问数 / 订阅 ----------
  insight: {
    tasks(params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/insight-tasks`, params)
    },
    createTask(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/insight-tasks`, body)
    },
    runTask(id: number) {
      return post<AiRecord>(`${BASE}/admin/insight-tasks/${id}/run`)
    },
    results(id: number) {
      return get<AiRecord[]>(`${BASE}/insight-tasks/${id}/results`)
    },
    attribution(body: AiRecord) {
      return post<AiRecord>(`${BASE}/analytics/attribution`, body)
    },
    attributions(metricId: number, limit?: number) {
      return get<AiRecord[]>(`${BASE}/analytics/attributions`, { metric_id: metricId, limit })
    },
    nl2sql(body: AiRecord) {
      return post<AiRecord>(`${BASE}/analytics/nl2sql`, body)
    },
    exportReport(body: AiRecord) {
      return post<AiRecord>(`${BASE}/analytics/export`, body)
    },
    subscriptions(limit?: number) {
      return get<AiRecord[]>(`${BASE}/subscriptions`, { limit })
    },
    subscribe(body: AiRecord) {
      return post<AiRecord>(`${BASE}/subscriptions`, body)
    },
  },

  // ---------- BI 数据权限 ----------
  dataPermissions: {
    config(body: AiRecord) {
      return put<AiRecord>(`${BASE}/admin/data-permissions`, body)
    },
  },
}
