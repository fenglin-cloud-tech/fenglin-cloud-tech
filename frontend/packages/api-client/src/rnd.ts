import { get, post, put, request } from './http'

export type RndId = string

/**
 * 云研（R&D / devops）接口。后端 Jackson 使用 SNAKE_CASE，BigDecimal 金额/工时均为字符串。
 * 所有路径经网关 /api/rnd/v1/**，权限由网关注入 X-Permissions。
 */
export interface RndProduct {
  id: RndId
  product_code: string
  name: string
  product_line?: string
  owner_id?: RndId | null
  visibility?: string
  status: string
  sprint_cycle_days?: number | null
  description?: string
  created_at?: string
}

export interface RndMember {
  id: RndId
  product_id: RndId
  user_id: RndId
  role_code?: string
  data_scope?: string
  created_at?: string
}

export interface RndStatus {
  id: RndId
  status_code: string
  status_name: string
  category?: string
  color?: string
  sort_no?: number
}

export interface RndRequirement {
  id: RndId
  product_id: RndId
  req_no?: string
  title: string
  req_type?: string
  priority?: string
  status_id?: RndId
  status_code?: string
  status_name?: string
  status_category?: string
  sprint_id?: RndId | null
  parent_id?: RndId | null
  assignee_id?: RndId | null
  reporter_id?: RndId | null
  estimate_hours?: string
  acceptance_criteria?: string
  plan_start_at?: string
  plan_end_at?: string
  delivered_at?: string
  escaped?: number
  created_at?: string
}

export interface RndSprint {
  id: RndId
  product_id: RndId
  sprint_no?: string
  name: string
  goal?: string
  start_date?: string
  end_date?: string
  capacity_hours?: string
  status: string
  requirement_count?: number
  task_count?: number
  done_task_count?: number
  created_at?: string
}

export interface RndBurndownPoint {
  date: string
  remaining_hours: string
  ideal_hours: string
}

export interface RndBurndown {
  sprint_id: RndId
  name: string
  total_tasks: number
  done_tasks: number
  points: RndBurndownPoint[]
}

export interface RndBoardColumn {
  id: RndId
  column_name: string
  status_codes?: string[]
  wip_limit?: number | null
  sort_no?: number
  item_count?: number
}

export interface RndBoard {
  id: RndId
  product_id: RndId
  name: string
  board_type?: string
  sprint_id?: RndId | null
  columns: RndBoardColumn[]
}

export interface RndTask {
  id: RndId
  product_id: RndId
  product_name?: string
  task_no?: string
  title: string
  task_type?: string
  status: string
  priority?: string
  sprint_id?: RndId | null
  requirement_id?: RndId | null
  assignee_id?: RndId | null
  estimate_hours?: string
  actual_hours?: string
  due_date?: string
  completed_at?: string
  created_at?: string
}

export interface RndWorklog {
  id: RndId
  task_id?: RndId | null
  defect_id?: RndId | null
  user_id: RndId
  work_date: string
  hours: string
  remark?: string
  created_at?: string
}

export interface RndDefect {
  id: RndId
  product_id: RndId
  defect_no?: string
  title: string
  severity?: string
  priority?: string
  status: string
  env?: string
  found_stage?: string
  sprint_id?: RndId | null
  requirement_id?: RndId | null
  assignee_id?: RndId | null
  reporter_id?: RndId | null
  reproduce_steps?: string
  reopen_count?: number
  fixed_at?: string
  closed_at?: string
  created_at?: string
}

export interface RndTestCase {
  id: RndId
  product_id: RndId
  case_no?: string
  title: string
  module?: string
  level?: string
  case_type?: string
  preconditions?: string
  steps?: string
  requirement_id?: RndId | null
  review_status?: string
  status?: string
  created_at?: string
}

export interface RndTestPlan {
  id: RndId
  product_id: RndId
  plan_no?: string
  name: string
  sprint_id?: RndId | null
  build_id?: RndId | null
  status: string
  case_count?: number
  pass_count?: number
  fail_count?: number
  created_at?: string
}

export interface RndExecution {
  id: RndId
  plan_id: RndId
  executor_id?: RndId | null
  result?: string
  pass_count?: number
  fail_count?: number
  executed_at?: string
}

export interface RndRepo {
  id: RndId
  product_id: RndId
  repo_name: string
  vcs_type?: string
  repo_url?: string
  default_branch?: string
  sync_status?: string
  token_masked?: string
  last_sync_at?: string
  created_at?: string
}

export interface RndSyncResult {
  repo_id: RndId
  sync_status: string
  synced_commits: number
  synced_at: string
}

export interface RndCommit {
  id: RndId
  repo_id: RndId
  commit_sha: string
  branch?: string
  author_name?: string
  message?: string
  add_lines?: number
  del_lines?: number
  files_changed?: number
  is_merge?: number
  is_excluded?: number
  committed_at?: string
}

export interface RndPipeline {
  id: RndId
  product_id: RndId
  name: string
  ci_type?: string
  trigger_mode?: string
  status?: string
  created_at?: string
}

export interface RndRun {
  id: RndId
  pipeline_id: RndId
  run_no?: string
  status: string
  trigger_type?: string
  started_at?: string
  finished_at?: string
  duration_ms?: number
  build_id?: RndId | null
  build_no?: string
  artifact_name?: string
  artifact_version?: string
  quality_gate?: string
}

export interface RndRelease {
  id: RndId
  product_id: RndId
  release_no?: string
  release_version: string
  env: string
  build_id?: RndId | null
  status: string
  approval_instance_id?: string
  window_start?: string
  window_end?: string
  deployed_at?: string
  created_at?: string
}

export interface RndDeployment {
  id: RndId
  release_id: RndId
  env: string
  result: string
  strategy?: string
  operator_id?: RndId | null
  started_at?: string
  finished_at?: string
}

export interface RndMetricValue {
  metric_code: string
  metric_name: string
  metric_group?: string
  metric_value: string
  unit?: string
  data_quality?: string
  caliber_version?: string
}

export interface RndMetricComputeResult {
  product_id: RndId
  period_type: string
  period_key: string
  metrics: RndMetricValue[]
  dora_level?: string
}

export interface RndContribution {
  user_id: RndId
  product_id: RndId
  period_type: string
  period_key: string
  commit_count?: number
  add_lines?: number
  del_lines?: number
  effective_commits?: number
  effective_commit_rate?: string
  complexity_score?: string
}

export interface RndDora {
  product_id: RndId
  period_type: string
  period_key: string
  deploy_freq?: string
  lead_time_hours?: string
  change_failure_rate?: string
  mttr_hours?: string
  level?: string
  computed_at?: string
}

export interface RndInsight {
  product_id: RndId
  period_key: string
  metrics: RndMetricValue[]
  dora?: RndDora
  requirement_count?: number
  defect_count?: number
  commit_count?: number
  release_count?: number
  data_quality?: string
}

export interface RndMetricConfig {
  id: RndId
  tenant_id: RndId
  metric_code: string
  metric_name: string
  metric_group?: string
  formula?: string
  caliber_version?: string
  weight?: string
  target_value?: string
  unit?: string
  status?: string
}

export interface RndCaliber {
  id: RndId
  metric_code: string
  caliber_version: string
  formula?: string
  effective_from?: string
  created_at?: string
}

export interface RndDataQuality {
  metric_code?: string
  period_type?: string
  period_key?: string
  sample_count?: number
  missing_count?: number
  outlier_count?: number
  missing_rate?: string
  quality_flag?: string
}

export interface RndAlert {
  id: RndId
  metric_code: string
  scope_type?: string
  scope_id?: RndId | null
  threshold?: string
  compare_op?: string
  triggered_value?: string
  status?: string
  notify_channel?: string
  triggered_at?: string
}

export interface RndTenantConfig {
  id: RndId
  config_json?: Record<string, unknown>
  timezone?: string
  data_retention_months?: number
  dora_target_level?: string
  webhook_base_url?: string
  updated_at?: string
}

const BASE = '/api/rnd/v1'

export const rndApi = {
  products: {
    list(params: { status?: string; page?: number; size?: number } = {}) {
      return get<RndProduct[]>(`${BASE}/products`, params)
    },
    get(id: RndId) {
      return get<RndProduct>(`${BASE}/products/${id}`)
    },
    create(body: {
      product_code: string
      name: string
      product_line?: string
      owner_id?: RndId | null
      visibility?: string
      sprint_cycle_days?: number
      description?: string
    }) {
      return post<RndProduct>(`${BASE}/products`, body)
    },
    update(
      id: RndId,
      body: {
        name?: string
        product_line?: string
        owner_id?: RndId | null
        visibility?: string
        sprint_cycle_days?: number
        description?: string
        status?: string
      },
    ) {
      return put<RndProduct>(`${BASE}/products/${id}`, body)
    },
    members(id: RndId) {
      return get<RndMember[]>(`${BASE}/products/${id}/members`)
    },
    addMember(id: RndId, body: { user_id: RndId; role_code?: string; data_scope?: string }) {
      return post<RndMember>(`${BASE}/products/${id}/members`, body)
    },
    updateMember(id: RndId, memberId: RndId, body: { role_code?: string; data_scope?: string }) {
      return put<RndMember>(`${BASE}/products/${id}/members/${memberId}`, body)
    },
    deleteMember(id: RndId, memberId: RndId) {
      return request<void>(`${BASE}/products/${id}/members/${memberId}`, { method: 'DELETE' })
    },
    statuses(id: RndId) {
      return get<RndStatus[]>(`${BASE}/products/${id}/statuses`)
    },
  },
  requirements: {
    list(params: { productId: RndId; sprintId?: RndId; statusCode?: string; page?: number; size?: number }) {
      return get<RndRequirement[]>(`${BASE}/requirements`, params)
    },
    get(id: RndId) {
      return get<RndRequirement>(`${BASE}/requirements/${id}`)
    },
    create(body: {
      product_id: RndId
      title: string
      req_type?: string
      priority?: string
      assignee_id?: RndId | null
      sprint_id?: RndId | null
      parent_id?: RndId | null
      estimate_hours?: string
      acceptance_criteria?: string
      plan_start_at?: string
      plan_end_at?: string
    }) {
      return post<RndRequirement>(`${BASE}/requirements`, body)
    },
    transition(id: RndId, statusCode: string) {
      return post<RndRequirement>(`${BASE}/requirements/${id}/transition`, { status_code: statusCode })
    },
    review(id: RndId) {
      return post<{ approval_instance_id?: string; status_code: string }>(`${BASE}/requirements/${id}/review`)
    },
    addRelation(id: RndId, body: { dst_requirement_id: RndId; relation_type: string }) {
      return post<void>(`${BASE}/requirements/${id}/relations`, body)
    },
    assignSprint(id: RndId, sprintId: RndId | null) {
      return post<RndRequirement>(`${BASE}/requirements/${id}/sprint`, { sprint_id: sprintId })
    },
  },
  sprints: {
    list(params: { productId: RndId; status?: string }) {
      return get<RndSprint[]>(`${BASE}/sprints`, params)
    },
    get(id: RndId) {
      return get<RndSprint>(`${BASE}/sprints/${id}`)
    },
    create(body: {
      product_id: RndId
      name: string
      goal?: string
      start_date?: string
      end_date?: string
      capacity_hours?: string
      members?: Array<{ user_id: RndId; available_hours?: string }>
    }) {
      return post<RndSprint>(`${BASE}/sprints`, body)
    },
    start(id: RndId) {
      return post<RndSprint>(`${BASE}/sprints/${id}/start`)
    },
    complete(id: RndId) {
      return post<RndSprint>(`${BASE}/sprints/${id}/complete`)
    },
    burndown(id: RndId) {
      return get<RndBurndown>(`${BASE}/sprints/${id}/burndown`)
    },
  },
  boards: {
    get(id: RndId) {
      return get<RndBoard>(`${BASE}/boards/${id}`)
    },
    create(body: {
      product_id: RndId
      name: string
      board_type?: string
      sprint_id?: RndId | null
      columns?: Array<{ column_name: string; status_codes?: string[]; wip_limit?: number }>
    }) {
      return post<RndBoard>(`${BASE}/boards`, body)
    },
    move(id: RndId, body: { biz_type: string; biz_id: RndId; column_id: RndId }) {
      return post<RndBoard>(`${BASE}/boards/${id}/move`, body)
    },
  },
  tasks: {
    list(params: {
      productId: RndId
      sprintId?: RndId
      assigneeId?: RndId
      status?: string
      page?: number
      size?: number
    }) {
      return get<RndTask[]>(`${BASE}/tasks`, params)
    },
    get(id: RndId) {
      return get<RndTask>(`${BASE}/tasks/${id}`)
    },
    my(params: { status?: string; page?: number; size?: number } = {}) {
      return get<RndTask[]>(`${BASE}/tasks/my`, params)
    },
    create(body: {
      product_id: RndId
      title: string
      task_type?: string
      sprint_id?: RndId | null
      requirement_id?: RndId | null
      defect_id?: RndId | null
      assignee_id?: RndId | null
      estimate_hours?: string
      due_date?: string
      priority?: string
    }) {
      return post<RndTask>(`${BASE}/tasks`, body)
    },
    transition(id: RndId, status: string) {
      return post<RndTask>(`${BASE}/tasks/${id}/transition`, { status })
    },
  },
  worklogs: {
    list(params: { taskId?: RndId; limit?: number } = {}) {
      return get<RndWorklog[]>(`${BASE}/worklogs`, params)
    },
    my(params: { from?: string; to?: string; page?: number; size?: number } = {}) {
      return get<RndWorklog[]>(`${BASE}/worklogs/my`, params)
    },
    create(body: {
      task_id?: RndId | null
      defect_id?: RndId | null
      work_date: string
      hours: string
      remark?: string
    }) {
      return post<RndWorklog>(`${BASE}/worklogs`, body)
    },
  },
  defects: {
    list(params: { productId: RndId; status?: string; severity?: string; page?: number; size?: number }) {
      return get<RndDefect[]>(`${BASE}/defects`, params)
    },
    get(id: RndId) {
      return get<RndDefect>(`${BASE}/defects/${id}`)
    },
    create(body: {
      product_id: RndId
      title: string
      severity?: string
      priority?: string
      env?: string
      found_stage?: string
      sprint_id?: RndId | null
      requirement_id?: RndId | null
      test_case_id?: RndId | null
      assignee_id?: RndId | null
      reproduce_steps?: string
    }) {
      return post<RndDefect>(`${BASE}/defects`, body)
    },
    transition(id: RndId, status: string) {
      return post<RndDefect>(`${BASE}/defects/${id}/transition`, { status })
    },
  },
  test: {
    cases: {
      list(params: { productId: RndId; reviewStatus?: string; page?: number; size?: number }) {
        return get<RndTestCase[]>(`${BASE}/test-cases`, params)
      },
      get(id: RndId) {
        return get<RndTestCase>(`${BASE}/test-cases/${id}`)
      },
      create(body: {
        product_id: RndId
        title: string
        module?: string
        level?: string
        case_type?: string
        preconditions?: string
        steps_json?: string
        requirement_id?: RndId | null
      }) {
        return post<RndTestCase>(`${BASE}/test-cases`, body)
      },
      review(id: RndId, reviewStatus: string) {
        return post<RndTestCase>(`${BASE}/test-cases/${id}/review?reviewStatus=${encodeURIComponent(reviewStatus)}`)
      },
    },
    plans: {
      get(id: RndId) {
        return get<RndTestPlan>(`${BASE}/test-plans/${id}`)
      },
      create(body: {
        product_id: RndId
        name: string
        sprint_id?: RndId | null
        build_id?: RndId | null
        start_date?: string
        end_date?: string
      }) {
        return post<RndTestPlan>(`${BASE}/test-plans`, body)
      },
      createExecution(id: RndId, body?: { executor_id?: RndId }) {
        return post<RndExecution>(`${BASE}/test-plans/${id}/executions`, body ?? {})
      },
    },
    executions: {
      get(id: RndId) {
        return get<RndExecution>(`${BASE}/executions/${id}`)
      },
      recordItem(id: RndId, body: { case_id: RndId; result: string; remark?: string }) {
        return post<RndExecution>(`${BASE}/executions/${id}/items`, body)
      },
    },
  },
  code: {
    repos: {
      list(productId: RndId) {
        return get<RndRepo[]>(`${BASE}/repos`, { productId })
      },
      get(id: RndId) {
        return get<RndRepo>(`${BASE}/repos/${id}`)
      },
      create(body: {
        product_id: RndId
        repo_name: string
        vcs_type?: string
        repo_url?: string
        default_branch?: string
        access_token?: string
        webhook_secret?: string
      }) {
        return post<RndRepo>(`${BASE}/repos`, body)
      },
      update(
        id: RndId,
        body: { repo_name?: string; default_branch?: string; access_token?: string; webhook_secret?: string },
      ) {
        return put<RndRepo>(`${BASE}/repos/${id}`, body)
      },
      remove(id: RndId) {
        return request<void>(`${BASE}/repos/${id}`, { method: 'DELETE' })
      },
      sync(id: RndId) {
        return post<RndSyncResult>(`${BASE}/repos/${id}/sync`)
      },
    },
    commits: {
      list(params: { repoId: RndId; authorUserId?: RndId; page?: number; size?: number }) {
        return get<RndCommit[]>(`${BASE}/commits`, params)
      },
      ingest(body: {
        repo_id: RndId
        commit_sha: string
        branch?: string
        author_name?: string
        author_email?: string
        add_lines?: number
        del_lines?: number
        files_changed?: number
        is_merge?: number
        message?: string
        committed_at?: string
      }) {
        return post<RndCommit>(`${BASE}/commits`, body)
      },
      link(id: RndId, body: { commit_id?: RndId; biz_type: string; biz_id: RndId; link_type: string }) {
        return post<RndCommit>(`${BASE}/commits/${id}/links`, body)
      },
    },
  },
  pipelines: {
    list(productId: RndId) {
      return get<RndPipeline[]>(`${BASE}/pipelines`, { productId })
    },
    get(id: RndId) {
      return get<RndPipeline>(`${BASE}/pipelines/${id}`)
    },
    create(body: { product_id: RndId; name: string; ci_type?: string; trigger_mode?: string; repo_id?: RndId }) {
      return post<RndPipeline>(`${BASE}/pipelines`, body)
    },
    trigger(id: RndId, triggerType?: string) {
      return post<RndRun>(`${BASE}/pipelines/${id}/runs`, { trigger_type: triggerType })
    },
    listRuns(id: RndId, limit = 20) {
      return get<RndRun[]>(`${BASE}/pipelines/${id}/runs`, { limit })
    },
    getRun(id: RndId) {
      return get<RndRun>(`${BASE}/runs/${id}`)
    },
    finishRun(
      id: RndId,
      body: {
        status: string
        artifact_name?: string
        artifact_version?: string
        artifact_url?: string
        quality_gate?: string
      },
    ) {
      return post<RndRun>(`${BASE}/runs/${id}/finish`, body)
    },
  },
  releases: {
    list(params: { productId: RndId; env?: string; status?: string; page?: number; size?: number }) {
      return get<RndRelease[]>(`${BASE}/releases`, params)
    },
    get(id: RndId) {
      return get<RndRelease>(`${BASE}/releases/${id}`)
    },
    create(body: {
      product_id: RndId
      release_version: string
      env: string
      build_id?: RndId | null
      window_start?: string
      window_end?: string
    }) {
      return post<RndRelease>(`${BASE}/releases`, body)
    },
    submit(id: RndId) {
      return post<RndRelease>(`${BASE}/releases/${id}/submit`)
    },
    approve(id: RndId) {
      return post<RndRelease>(`${BASE}/releases/${id}/approve`)
    },
    reject(id: RndId) {
      return post<RndRelease>(`${BASE}/releases/${id}/reject`)
    },
    deploy(id: RndId, strategy?: string) {
      return post<RndDeployment>(`${BASE}/releases/${id}/deploy`, { strategy })
    },
    rollback(id: RndId) {
      return post<RndDeployment>(`${BASE}/releases/${id}/rollback`)
    },
    deployments(id: RndId) {
      return get<RndDeployment[]>(`${BASE}/releases/${id}/deployments`)
    },
  },
  metrics: {
    compute(body: { product_id: RndId; period_type?: string; period_key?: string; user_id?: RndId }) {
      return post<RndMetricComputeResult>(`${BASE}/metrics/compute`, body)
    },
    snapshots(params: { productId: RndId; periodType?: string; periodKey?: string }) {
      return get<RndMetricValue[]>(`${BASE}/metrics`, params)
    },
    contributions(params: { productId: RndId; periodType?: string; periodKey?: string }) {
      return get<RndContribution[]>(`${BASE}/metrics/contributions`, params)
    },
    dora(params: { productId: RndId; periodType?: string; periodKey?: string }) {
      return get<RndDora>(`${BASE}/metrics/dora`, params)
    },
    insight(params: { productId: RndId; periodType?: string; periodKey?: string }) {
      return get<RndInsight>(`${BASE}/metrics/insight`, params)
    },
    configs() {
      return get<RndMetricConfig[]>(`${BASE}/metric-configs`)
    },
    createConfig(body: {
      metric_code: string
      metric_name: string
      metric_group?: string
      formula?: string
      weight?: string
      target_value?: string
      unit?: string
    }) {
      return post<RndMetricConfig>(`${BASE}/metric-configs`, body)
    },
    updateConfig(
      metricCode: string,
      body: {
        metric_name?: string
        metric_group?: string
        formula?: string
        weight?: string
        target_value?: string
        unit?: string
        status?: string
      },
    ) {
      return put<RndMetricConfig>(`${BASE}/metric-configs/${metricCode}`, body)
    },
    calibers(metricCode?: string) {
      return get<RndCaliber[]>(`${BASE}/metric-calibers`, { metricCode })
    },
    publishCaliber(body: {
      metric_code: string
      caliber_version: string
      formula?: string
      effective_from?: string
    }) {
      return post<RndCaliber>(`${BASE}/metric-calibers`, body)
    },
    dataQuality(params: { metricCode?: string; periodType?: string; periodKey?: string } = {}) {
      return get<RndDataQuality>(`${BASE}/metric-data-quality`, params)
    },
    alerts(params: { status?: string; metricCode?: string } = {}) {
      return get<RndAlert[]>(`${BASE}/metric-alerts`, params)
    },
    createAlert(body: {
      metric_code: string
      scope_type: string
      scope_id?: RndId | null
      threshold: string
      compare_op: string
      notify_channel?: string
    }) {
      return post<RndAlert>(`${BASE}/metric-alerts`, body)
    },
    updateAlert(
      id: RndId,
      body: { threshold?: string; compare_op?: string; notify_channel?: string; status?: string },
    ) {
      return put<RndAlert>(`${BASE}/metric-alerts/${id}`, body)
    },
    resolveAlert(id: RndId) {
      return post<RndAlert>(`${BASE}/metric-alerts/${id}/resolve`)
    },
    evaluateAlerts(params: { productId: RndId; periodType?: string; periodKey?: string }) {
      return post<RndAlert[]>(
        `${BASE}/metrics/alerts/evaluate?productId=${params.productId}` +
          `${params.periodType ? `&periodType=${encodeURIComponent(params.periodType)}` : ''}` +
          `${params.periodKey ? `&periodKey=${encodeURIComponent(params.periodKey)}` : ''}`,
      )
    },
  },
  tenantConfig: {
    get() {
      return get<RndTenantConfig>(`${BASE}/tenant-config`)
    },
    update(body: {
      config_json?: Record<string, unknown>
      timezone?: string
      data_retention_months?: number
      dora_target_level?: string
      webhook_base_url?: string
    }) {
      return put<RndTenantConfig>(`${BASE}/tenant-config`, body)
    },
  },
}
