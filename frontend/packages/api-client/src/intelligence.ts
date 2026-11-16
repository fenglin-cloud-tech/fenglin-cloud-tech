import { del, download, get, post, put, postForm, streamSse } from './http'
import type { PageResult } from './http'

export type AiRecord = Record<string, any>
export type AiPage = PageResult<AiRecord>
/** 雪花 ID 以字符串下发，仅做透传，禁止转 number（超过 2^53 会丢精度）。 */
export type IntelligenceId = string | number

const BASE = '/api/intelligence/v1'

export type AiPageQuery = {
  page?: number
  size?: number
}

export const intelligenceApi = {
  // ---------- 运营概览 ----------
  overview(params?: { from?: string; to?: string }) {
    return get<AiRecord>(`${BASE}/report/overview`, params)
  },
  searchAnalytics(limit?: number, params?: { from?: string; to?: string }) {
    return get<AiRecord>(`${BASE}/admin/search/analytics`, { limit, ...params })
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
    acl(kbId: IntelligenceId) {
      return get<AiRecord[]>(`${BASE}/admin/knowledge-bases/${kbId}/acl`)
    },
    saveAcl(kbId: IntelligenceId, items: AiRecord[]) {
      return put<AiRecord>(`${BASE}/admin/knowledge-bases/${kbId}/acl`, { items })
    },
    personal() {
      return get<AiRecord[]>(`${BASE}/copilot/private-kb`)
    },
    createPersonal(body?: AiRecord) {
      return post<AiRecord>(`${BASE}/copilot/private-kb`, body ?? {})
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
    test(id: IntelligenceId) {
      return post<AiRecord>(`${BASE}/admin/data-sources/${id}/test`)
    },
    sync(id: IntelligenceId) {
      return post<AiRecord>(`${BASE}/admin/data-sources/${id}/sync`)
    },
  },

  // ---------- 文档 / 索引 / 审核 ----------
  documents: {
    list(kbId: IntelligenceId, params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/knowledge-bases/${kbId}/documents`, params)
    },
    detail(id: IntelligenceId) {
      return get<AiRecord>(`${BASE}/admin/documents/${id}`)
    },
    create(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/documents`, body)
    },
    submitReview(id: IntelligenceId) {
      return post<AiRecord>(`${BASE}/admin/documents/${id}/review:submit`)
    },
    review(id: IntelligenceId, action: string) {
      return post<AiRecord>(`${BASE}/admin/documents/${id}/review`, { action })
    },
    index(id: IntelligenceId) {
      return post<AiRecord>(`${BASE}/admin/documents/${id}/index`)
    },
    parse(id: IntelligenceId, content: string) {
      return post<AiRecord>(`${BASE}/admin/documents/${id}/parse`, { content })
    },
    upload(form: FormData) {
      return postForm<AiRecord>(`${BASE}/admin/documents/upload`, form)
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
    preview(docId: IntelligenceId, secLevel?: number) {
      return get<AiRecord>(`${BASE}/documents/${docId}/preview`, { sec_level: secLevel })
    },
    suggest(q: string, limit?: number) {
      return get<string[]>(`${BASE}/search/suggest`, { q, limit })
    },
  },

  // ---------- 知识图谱 ----------
  kg: {
    entities(params?: { kb_id?: IntelligenceId; keyword?: string; limit?: number }) {
      return get<AiRecord[]>(`${BASE}/kg/entities`, params)
    },
    paths(params: { from_id: IntelligenceId; to_id: IntelligenceId; max_depth?: number }) {
      return get<AiRecord>(`${BASE}/kg/paths`, params)
    },
    createEntity(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/kg/entities`, body)
    },
    createRelation(body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/kg/relations`, body)
    },
    graph(params?: { kbId?: IntelligenceId; entityId?: IntelligenceId; depth?: number }) {
      return get<AiRecord>(`${BASE}/kg/graph`, params)
    },
    entity(id: IntelligenceId) {
      return get<AiRecord>(`${BASE}/kg/entities/${id}`)
    },
    deleteEntity(id: IntelligenceId) {
      return del<AiRecord>(`${BASE}/admin/kg/entities/${id}`)
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
    messages(id: IntelligenceId, params?: AiPageQuery) {
      return get<AiPage>(`${BASE}/copilot/conversations/${id}/messages`, params)
    },
    send(id: IntelligenceId, body: AiRecord) {
      return post<AiRecord>(`${BASE}/copilot/conversations/${id}/messages`, body)
    },
    stream(
      id: IntelligenceId,
      body: AiRecord,
      onEvent: (event: string, data: AiRecord) => void,
    ) {
      return streamSse(`${BASE}/copilot/conversations/${id}/messages:stream`, body, { onEvent })
    },
    citations(messageId: IntelligenceId) {
      return get<AiRecord[]>(`${BASE}/copilot/messages/${messageId}/citations`)
    },
    feedback(messageId: IntelligenceId, body: AiRecord) {
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
    executions(params?: AiPageQuery & { agent_id?: IntelligenceId; scope?: string }) {
      return get<AiPage>(`${BASE}/agents/executions`, params)
    },
    execution(id: IntelligenceId) {
      return get<AiRecord>(`${BASE}/agents/executions/${id}`)
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
    fields(datasetId: IntelligenceId) {
      return get<AiRecord[]>(`${BASE}/datasets/${datasetId}/fields`)
    },
    addField(datasetId: IntelligenceId, body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/datasets/${datasetId}/fields`, body)
    },
    deleteField(datasetId: IntelligenceId, fieldId: IntelligenceId) {
      return del<AiRecord>(`${BASE}/admin/datasets/${datasetId}/fields/${fieldId}`)
    },
    preview(datasetId: IntelligenceId, limit = 20) {
      return post<AiRecord>(`${BASE}/datasets/${datasetId}/preview`, { limit })
    },
    query(datasetId: IntelligenceId, body: AiRecord) {
      return post<AiRecord>(`${BASE}/datasets/${datasetId}/query`, body)
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
    detail(id: IntelligenceId) {
      return get<AiRecord>(`${BASE}/dashboards/${id}`)
    },
    createChart(id: IntelligenceId, body: AiRecord) {
      return post<AiRecord>(`${BASE}/admin/dashboards/${id}/charts`, body)
    },
    deleteChart(id: IntelligenceId, chartId: IntelligenceId) {
      return del<AiRecord>(`${BASE}/admin/dashboards/${id}/charts/${chartId}`)
    },
    reorderCharts(id: IntelligenceId, chartIds: IntelligenceId[]) {
      return put<AiRecord>(`${BASE}/admin/dashboards/${id}/charts/order`, { chart_ids: chartIds })
    },
    chartData(id: IntelligenceId, chartId: IntelligenceId, body?: AiRecord) {
      return post<AiRecord>(`${BASE}/dashboards/${id}/charts/${chartId}/data`, body ?? {})
    },
    chartAlert(id: IntelligenceId, chartId: IntelligenceId) {
      return get<AiRecord>(`${BASE}/admin/dashboards/${id}/charts/${chartId}/alert`)
    },
    saveChartAlert(id: IntelligenceId, chartId: IntelligenceId, body: AiRecord) {
      return put<AiRecord>(`${BASE}/admin/dashboards/${id}/charts/${chartId}/alert`, body)
    },
    alerts(limit?: number) {
      return get<AiRecord[]>(`${BASE}/admin/ai/alerts`, { limit })
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
    runTask(id: IntelligenceId) {
      return post<AiRecord>(`${BASE}/admin/insight-tasks/${id}/run`)
    },
    results(id: IntelligenceId) {
      return get<AiRecord[]>(`${BASE}/insight-tasks/${id}/results`)
    },
    attribution(body: AiRecord) {
      return post<AiRecord>(`${BASE}/analytics/attribution`, body)
    },
    attributions(metricId: IntelligenceId, limit?: number) {
      return get<AiRecord[]>(`${BASE}/analytics/attributions`, { metric_id: metricId, limit })
    },
    nl2sql(body: AiRecord) {
      return post<AiRecord>(`${BASE}/analytics/nl2sql`, body)
    },
    exportReport(body: AiRecord) {
      return post<AiRecord>(`${BASE}/analytics/export`, body)
    },
    exportCsv(taskId: IntelligenceId) {
      return download(`${BASE}/insight-tasks/${taskId}/results/export`, `insight-result-${taskId}.csv`)
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
    list(limit?: number) {
      return get<AiRecord[]>(`${BASE}/admin/data-permissions`, { limit })
    },
  },
}
