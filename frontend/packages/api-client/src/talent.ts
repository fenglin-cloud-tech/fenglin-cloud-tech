import type { PageResult } from '@fenglin/shared'
import { download, get, post, put, request } from './http'

/** 云才（Talent）接口。字段为后端 SNAKE_CASE 原样返回。 */
export type TalentId = string | number

export interface TalentNameRef {
  id: TalentId
  name: string
}

export interface TalentOrgNode {
  id: TalentId
  org_code: string
  org_name: string
  parent_id?: TalentId
  level: number
  org_type: string
  leader_id?: TalentId
  children?: TalentOrgNode[]
}

export interface TalentOrgDetail {
  id: TalentId
  org_code: string
  org_name: string
  parent_id?: TalentId
  path: string
  level: number
  org_type: string
  leader?: TalentNameRef
  employee_count: number
  status: string
}

export interface TalentPosition {
  id: TalentId
  position_code: string
  position_name: string
  job_family: string
  job_sequence: string
  headcount: number
  org_id?: TalentId
  status: string
}

export interface TalentGrade {
  id: TalentId
  grade_code: string
  grade_name: string
  sequence: string
  grade_level: number
  band_min?: number
  band_max?: number
}

export interface TalentEmployeeListItem {
  id: TalentId
  emp_no: string
  name: string
  org?: TalentNameRef
  position?: string
  grade?: string
  emp_status: string
  hire_date?: string
  masked_phone: string
}

export interface TalentEmployeeDetail {
  id: TalentId
  emp_no: string
  name: string
  gender: string
  birth_date?: string
  masked_phone: string
  masked_id_card: string
  email?: string
  org?: TalentNameRef
  position?: TalentNameRef
  grade?: TalentNameRef
  manager?: TalentNameRef
  employment_type: string
  hire_date?: string
  formal_date?: string
  emp_status: string
  work_location?: string
}

export interface TalentRequisition {
  id: TalentId
  req_no: string
  position_name: string
  org_name: string
  headcount: number
  req_status: string
  owner?: TalentNameRef
  created_at?: string
}

export interface TalentCandidate {
  id: TalentId
  name: string
  source: string
  cand_status: string
  experience_years?: number
  current_company?: string
  masked_phone: string
  created_at?: string
}

export interface TalentMyInterview {
  id: TalentId
  candidate_name: string
  position_name: string
  round_no: number
  scheduled_at?: string
  mode: string
  int_status: string
}

export interface TalentHeadcountReport {
  total: number
  by_status: Record<string, number>
  by_org: Array<{ org_id: TalentId; org_name: string; count: number }>
  by_employment_type: Record<string, number>
  avg_tenure_months?: number
}

// ---------- 考勤 / 假期 ----------

export interface TalentAttendanceRecord {
  id: TalentId
  employee_id: TalentId
  work_date: string
  clock_in_at?: string
  clock_out_at?: string
  source?: string
  att_status: string
  work_minutes: number
  late_minutes: number
  early_minutes: number
  exception_note?: string
  version?: number
}

export interface TalentAttendanceSummary {
  normal_days: number
  late_days: number
  early_days: number
  late_early_days: number
  missing_days: number
  absent_days: number
  work_minutes: number
  late_minutes: number
  early_minutes: number
}

export interface TalentLeaveType {
  id: TalentId
  type_code: string
  type_name: string
  annual_quota_days?: number
  paid: boolean
}

export interface TalentLeaveBalance {
  id: TalentId
  leave_type_id: TalentId
  type_code: string
  type_name: string
  year: number
  total_days: number
  used_days: number
  available_days: number
  version?: number
}

export interface TalentLeaveRequest {
  id: TalentId
  request_no: string
  employee_id: TalentId
  employee_name: string
  leave_type_name: string
  start_date: string
  end_date: string
  days: number
  lr_status: string
  approve_opinion?: string
  created_at?: string
}

export interface TalentLeaveRequestResult {
  id: TalentId
  request_no: string
  lr_status: string
  process_instance_id?: string
  used_days?: number
}

// ---------- 薪酬 ----------

export interface TalentPayPeriod {
  id: TalentId
  period_code: string
  start_date: string
  end_date: string
  pay_date?: string
  period_status: string
  employee_count: number
  gross_total?: number
  net_total?: number
  amount_masked?: boolean
  version?: number
}

export interface TalentPayrollRecord {
  id: TalentId
  period_id: TalentId
  employee_id: TalentId
  employee_name: string
  base_salary?: number
  earning_total?: number
  deduction_total?: number
  gross_pay?: number
  net_pay?: number
  rec_status: string
  amount_masked?: boolean
}

// ---------- 绩效 ----------

export interface TalentPerfCycle {
  id: TalentId
  cycle_code: string
  cycle_name: string
  cycle_type: string
  start_date: string
  end_date: string
  cycle_status: string
  evaluation_count: number
  avg_score?: number
  version?: number
}

export interface TalentPerfGoal {
  id: TalentId
  cycle_id: TalentId
  employee_id: TalentId
  employee_name: string
  goal_name: string
  goal_desc?: string
  weight?: number
  measure_type: string
  target_value?: string
  goal_status: string
  approve_opinion?: string
  created_at?: string
}

export interface TalentPerfEvaluation {
  id: TalentId
  cycle_id: TalentId
  employee_id: TalentId
  employee_name: string
  evaluator_id?: TalentId
  review_type: string
  score?: number
  grade?: string
  eval_status: string
  process_instance_id?: string
  approve_opinion?: string
  created_at?: string
}

export interface TalentPerfEvaluationSubmitResult {
  id: TalentId
  eval_status: string
  cycle_id: TalentId
  process_instance_id?: string
  score?: number
  grade?: string
}

export interface TalentPerfResult {
  id: TalentId
  cycle_id: TalentId
  employee_id: TalentId
  employee_name: string
  final_score?: number
  grade?: string
  rank_no: number
  bonus_coefficient?: number
  result_status: string
  published_at?: string
}

// ---------- 培训 ----------

export interface TalentCourse {
  id: TalentId
  course_code: string
  title: string
  category?: string
  mode?: string
  duration_hours?: number
  credit?: number
  course_status: string
}

export interface TalentCourseCreateResult {
  id: TalentId
  course_status: string
}

export interface TalentCourseStatusResult {
  id: TalentId
  course_status: string
}

export interface TalentSessionCreateResult {
  id: TalentId
  session_status: string
}

export interface TalentEnrollmentResult {
  id: TalentId
  enroll_status: string
  waitlist?: boolean
  waitlist_no?: number
}

export interface TalentLearningProgressResult {
  id: TalentId
  progress: number
  learn_status: string
}

export interface TalentCertificate {
  id: TalentId
  cert_no: string
  cert_name: string
  issue_date?: string
  expire_date?: string
}

// ---------- 内部机会（活水） ----------

export interface TalentMarketItem {
  demand_id: TalentId
  demand_no: string
  org_name: string
  position_name: string
  skills?: unknown
  headcount: number
  expected_date?: string
  match_score?: number
}

export interface TalentFlowApplyResult {
  id: TalentId
  flow_status: string
}

export interface TalentFlowApplication {
  id: TalentId
  demand_no: string
  employee_name: string
  apply_type: string
  flow_status: string
  created_at?: string
}

export interface TalentEmployeeSkill {
  skill_id: TalentId
  skill_name: string
  self_level?: number
  cert_level?: number
  years?: number
  last_used_at?: string
  verified_by?: TalentId
}

export const talentApi = {
  orgs: {
    tree(params: { rootId?: TalentId; depth?: number; includeDisabled?: boolean } = {}) {
      return get<TalentOrgNode[]>('/api/talent/v1/orgs/tree', params)
    },
    detail(id: TalentId) {
      return get<TalentOrgDetail>(`/api/talent/v1/orgs/${id}`)
    },
    create(body: {
      parent_id?: TalentId
      org_code: string
      org_name: string
      org_type?: string
      leader_id?: TalentId
      sort_no?: number
    }) {
      return post<{ id: TalentId; org_code: string; path: string; level: number }>('/api/talent/v1/orgs', body)
    },
    update(id: TalentId, body: {
      org_name?: string
      leader_id?: TalentId
      sort_no?: number
      status?: string
      version?: number
    }) {
      return put<{ id: TalentId; updated_at?: string }>(`/api/talent/v1/orgs/${id}`, body)
    },
    move(id: TalentId, targetParentId?: TalentId) {
      return post<{ id: TalentId; path: string; level: number }>(`/api/talent/v1/orgs/${id}/move`, {
        target_parent_id: targetParentId,
      })
    },
    remove(id: TalentId) {
      return request<{ id: TalentId; status: string }>(`/api/talent/v1/orgs/${id}`, { method: 'DELETE' })
    },
  },

  positions: {
    list(params: { page?: number; size?: number } = {}) {
      return get<PageResult<TalentPosition>>('/api/talent/v1/positions', params)
    },
    create(body: {
      position_code: string
      position_name: string
      org_id?: TalentId
      job_family?: string
      job_sequence?: string
      headcount?: number
      description?: string
    }) {
      return post<{ id: TalentId; position_code: string }>('/api/talent/v1/positions', body)
    },
  },

  grades: {
    list() {
      return get<TalentGrade[]>('/api/talent/v1/grades')
    },
  },

  employees: {
    list(params: {
      orgId?: TalentId
      includeSubOrg?: boolean
      empStatus?: string
      keyword?: string
      page?: number
      size?: number
    } = {}) {
      return get<PageResult<TalentEmployeeListItem>>('/api/talent/v1/employees', params)
    },
    detail(id: TalentId) {
      return get<TalentEmployeeDetail>(`/api/talent/v1/employees/${id}`)
    },
    create(body: {
      emp_no: string
      name: string
      gender?: string
      birth_date?: string
      phone?: string
      id_card?: string
      email?: string
      org_id?: TalentId
      position_id?: TalentId
      grade_id?: TalentId
      manager_id?: TalentId
      employment_type?: string
      hire_date?: string
      work_location?: string
    }) {
      return post<{ id: TalentId; emp_no: string; emp_status: string }>('/api/talent/v1/employees', body)
    },
    update(id: TalentId, body: {
      name?: string
      email?: string
      org_id?: TalentId
      position_id?: TalentId
      grade_id?: TalentId
      manager_id?: TalentId
      work_location?: string
      version?: number
    }) {
      return put<{ id: TalentId; updated_at?: string }>(`/api/talent/v1/employees/${id}`, body)
    },
    exportRoster(params: { orgId?: TalentId; includeSubOrg?: boolean; empStatus?: string } = {}) {
      const query = new URLSearchParams()
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
      })
      const suffix = query.toString() ? `?${query.toString()}` : ''
      return download(`/api/talent/v1/employees/roster/export${suffix}`, 'roster.csv')
    },
  },

  recruit: {
    requisitions(params: { reqStatus?: string; orgId?: TalentId; ownerId?: TalentId; page?: number; size?: number } = {}) {
      return get<PageResult<TalentRequisition>>('/api/talent/v1/recruit/requisitions', params)
    },
    createRequisition(body: {
      position_id?: TalentId
      org_id?: TalentId
      headcount?: number
      salary_min?: number
      salary_max?: number
      urgency?: string
      reason_type?: string
      owner_id?: TalentId
      expect_date?: string
      description?: string
      requirements?: string
    }) {
      return post<{ id: TalentId; req_no: string; req_status: string }>('/api/talent/v1/recruit/requisitions', body)
    },
    submitRequisition(id: TalentId) {
      return post<{ id: TalentId; req_status: string; process_instance_id?: string }>(
        `/api/talent/v1/recruit/requisitions/${id}/submit`,
      )
    },
    candidates(params: {
      requisitionId?: TalentId
      candStatus?: string
      source?: string
      keyword?: string
      page?: number
      size?: number
    } = {}) {
      return get<PageResult<TalentCandidate>>('/api/talent/v1/recruit/candidates', params)
    },
    createCandidate(body: {
      name: string
      phone?: string
      id_card?: string
      email?: string
      gender?: string
      birth_date?: string
      source?: string
      experience_years?: number
      current_company?: string
      expected_salary?: number
      tags?: unknown
    }) {
      return post<{ id: TalentId; cand_status: string; referral_hint: boolean }>(
        '/api/talent/v1/recruit/candidates',
        body,
      )
    },
    createApplication(body: { candidate_id: TalentId; requisition_id: TalentId; stage?: string }) {
      return post<{ id: TalentId; app_status: string }>('/api/talent/v1/recruit/applications', body)
    },
    createInterview(body: {
      application_id: TalentId
      round_no?: number
      interviewer_id?: TalentId
      scheduled_at?: string
      duration_min?: number
      mode?: string
      location?: string
    }) {
      return post<{ id: TalentId; int_status: string }>('/api/talent/v1/recruit/interviews', body)
    },
    evaluateInterview(id: TalentId, body: {
      score?: number
      evaluation?: string
      result?: string
      version?: number
    }) {
      return post<{ id: TalentId; int_status: string; result: string }>(
        `/api/talent/v1/recruit/interviews/${id}/evaluate`,
        body,
      )
    },
    myInterviews() {
      return get<TalentMyInterview[]>('/api/talent/v1/recruit/interviews/my')
    },
  },

  lifecycle: {
    createOnboarding(body: {
      offer_id?: TalentId
      employee_id?: TalentId
      onboard_date?: string
      mentor_id?: TalentId
      checklist?: unknown
    }) {
      return post<{ id: TalentId; ob_status: string }>('/api/talent/v1/onboarding', body)
    },
    completeOnboarding(id: TalentId) {
      return post<{ id: TalentId; ob_status: string; employee_status: string }>(
        `/api/talent/v1/onboarding/${id}/complete`,
      )
    },
    evaluateProbation(id: TalentId, body: { evaluation?: string; result: string; extend_months?: number }) {
      return post<{ id: TalentId; prob_status: string }>(`/api/talent/v1/probation/${id}/evaluate`, body)
    },
    createTransfer(body: {
      employee_id: TalentId
      transfer_type: string
      after?: { org_id?: TalentId; position_id?: TalentId; grade_id?: TalentId; salary?: number }
      effective_date?: string
      reason?: string
    }) {
      return post<{ id: TalentId; tf_status: string; process_instance_id?: string }>(
        '/api/talent/v1/transfers',
        body,
      )
    },
    effectiveTransfer(id: TalentId) {
      return post<{ id: TalentId; tf_status: string; employee?: Record<string, TalentId> }>(
        `/api/talent/v1/transfers/${id}/effective`,
      )
    },
    approveTransfer(id: TalentId, body: { approved: boolean; opinion?: string }) {
      return post<{ id: TalentId; tf_status: string; employee?: Record<string, TalentId> }>(
        `/api/talent/v1/transfers/${id}/approval`,
        body,
      )
    },
    createResignation(body: {
      employee_id: TalentId
      resign_type: string
      apply_date?: string
      last_work_date?: string
      reason_category?: string
    }) {
      return post<{ id: TalentId; rs_status: string }>('/api/talent/v1/resignations', body)
    },
    approveResignation(id: TalentId, body: { approved: boolean; opinion?: string }) {
      return post<{ id: TalentId; rs_status: string }>(`/api/talent/v1/resignations/${id}/approve`, body)
    },
  },

  reports: {
    headcount(params: { orgId?: TalentId; includeSubOrg?: boolean } = {}) {
      return get<TalentHeadcountReport>('/api/talent/v1/reports/headcount', params)
    },
  },

  attendance: {
    clock(body: { clock_type?: string; source?: string } = {}) {
      return post<TalentAttendanceRecord>('/api/talent/v1/attendance/clock', body)
    },
    records(params: {
      employeeId?: TalentId
      from?: string
      to?: string
      status?: string
      page?: number
      size?: number
    } = {}) {
      return get<PageResult<TalentAttendanceRecord>>('/api/talent/v1/attendance/records', params)
    },
    summary(params: { month?: string; employeeId?: TalentId } = {}) {
      return get<TalentAttendanceSummary>('/api/talent/v1/attendance/summary', params)
    },
    my(params: { month?: string } = {}) {
      return get<TalentAttendanceSummary>('/api/talent/v1/attendance/my', params)
    },
  },

  leave: {
    types() {
      return get<TalentLeaveType[]>('/api/talent/v1/leave/types')
    },
    balances(params: { employeeId?: TalentId; year?: number } = {}) {
      return get<TalentLeaveBalance[]>('/api/talent/v1/leave/balances', params)
    },
    myRequests(params: { status?: string; page?: number; size?: number } = {}) {
      return get<PageResult<TalentLeaveRequest>>('/api/talent/v1/leave/requests/my', params)
    },
    create(body: {
      employee_id?: TalentId
      leave_type_id: TalentId
      start_date: string
      end_date: string
      days?: number
      reason?: string
      idempotency_key?: string
    }) {
      return post<TalentLeaveRequestResult>('/api/talent/v1/leave/requests', body)
    },
    cancel(id: TalentId) {
      return post<TalentLeaveRequestResult>(`/api/talent/v1/leave/requests/${id}/cancel`)
    },
  },

  payroll: {
    periods(params: { page?: number; size?: number } = {}) {
      return get<PageResult<TalentPayPeriod>>('/api/talent/v1/payroll/periods', params)
    },
    records(periodId: TalentId, params: { page?: number; size?: number } = {}) {
      return get<PageResult<TalentPayrollRecord>>(`/api/talent/v1/payroll/periods/${periodId}/records`, params)
    },
  },

  performance: {
    cycles(params: { page?: number; size?: number } = {}) {
      return get<PageResult<TalentPerfCycle>>('/api/talent/v1/performance/cycles', params)
    },
    createCycle(body: {
      cycle_code: string
      cycle_name: string
      cycle_type?: string
      start_date: string
      end_date: string
    }) {
      return post<TalentPerfCycle>('/api/talent/v1/performance/cycles', body)
    },
    openGoals(id: TalentId) {
      return post<TalentPerfCycle>(`/api/talent/v1/performance/cycles/${id}/open-goals`)
    },
    startEvaluation(id: TalentId) {
      return post<TalentPerfCycle>(`/api/talent/v1/performance/cycles/${id}/start-evaluation`)
    },
    closeCycle(id: TalentId) {
      return post<TalentPerfCycle>(`/api/talent/v1/performance/cycles/${id}/close`)
    },
    goals(cycleId: TalentId, params: { page?: number; size?: number } = {}) {
      return get<PageResult<TalentPerfGoal>>(`/api/talent/v1/performance/cycles/${cycleId}/goals`, params)
    },
    createGoal(body: {
      cycle_id: TalentId
      employee_id: TalentId
      goal_name: string
      goal_desc?: string
      weight?: number
      measure_type?: string
      target_value?: string
    }) {
      return post<TalentPerfGoal>('/api/talent/v1/performance/goals', body)
    },
    submitGoal(id: TalentId) {
      return post<TalentPerfGoal>(`/api/talent/v1/performance/goals/${id}/submit`)
    },
    approveGoal(id: TalentId, body: { approved: boolean; opinion?: string }) {
      return post<TalentPerfGoal>(`/api/talent/v1/performance/goals/${id}/approval`, body)
    },
    evaluations(cycleId: TalentId, params: { page?: number; size?: number } = {}) {
      return get<PageResult<TalentPerfEvaluation>>(
        `/api/talent/v1/performance/cycles/${cycleId}/evaluations`,
        params,
      )
    },
    createEvaluation(body: {
      cycle_id: TalentId
      employee_id: TalentId
      evaluator_id?: TalentId
      review_type?: string
      score: number
      summary?: string
    }) {
      return post<TalentPerfEvaluation>('/api/talent/v1/performance/evaluations', body)
    },
    submitEvaluation(id: TalentId) {
      return post<TalentPerfEvaluationSubmitResult>(`/api/talent/v1/performance/evaluations/${id}/submit`)
    },
    approveEvaluation(id: TalentId, body: { approved: boolean; opinion?: string }) {
      return post<TalentPerfEvaluation>(`/api/talent/v1/performance/evaluations/${id}/approval`, body)
    },
    results(cycleId: TalentId, params: { page?: number; size?: number } = {}) {
      return get<PageResult<TalentPerfResult>>(`/api/talent/v1/performance/cycles/${cycleId}/results`, params)
    },
    calculateResults(cycleId: TalentId, params: { page?: number; size?: number } = {}) {
      return post<PageResult<TalentPerfResult>>(
        `/api/talent/v1/performance/cycles/${cycleId}/results/calculate`,
        params,
      )
    },
    publishResults(cycleId: TalentId, params: { page?: number; size?: number } = {}) {
      return post<PageResult<TalentPerfResult>>(
        `/api/talent/v1/performance/cycles/${cycleId}/results/publish`,
        params,
      )
    },
  },

  training: {
    courses(params: {
      category?: string
      mode?: string
      courseStatus?: string
      keyword?: string
      page?: number
      size?: number
    } = {}) {
      return get<PageResult<TalentCourse>>('/api/talent/v1/training/courses', params)
    },
    createCourse(body: {
      course_code: string
      title: string
      category?: string
      description?: string
      lecturer?: string
      mode: string
      duration_hours?: number
      credit?: number
      target_audience?: unknown
    }) {
      return post<TalentCourseCreateResult>('/api/talent/v1/training/courses', body)
    },
    publishCourse(id: TalentId) {
      return post<TalentCourseStatusResult>(`/api/talent/v1/training/courses/${id}/publish`)
    },
    offlineCourse(id: TalentId) {
      return post<TalentCourseStatusResult>(`/api/talent/v1/training/courses/${id}/offline`)
    },
    createSession(courseId: TalentId, body: {
      start_at: string
      end_at: string
      capacity: number
      enroll_deadline?: string
      venue?: string
    }) {
      return post<TalentSessionCreateResult>(`/api/talent/v1/training/courses/${courseId}/sessions`, body)
    },
    enroll(body: { course_id: TalentId; session_id: TalentId; employee_id?: TalentId }) {
      return post<TalentEnrollmentResult>('/api/talent/v1/training/enrollments', body)
    },
    reportProgress(id: TalentId, body: { progress: number; study_seconds?: number; position?: string }) {
      return post<TalentLearningProgressResult>(`/api/talent/v1/training/learning/${id}/progress`, body)
    },
    certificates(params: { employeeId?: TalentId } = {}) {
      return get<TalentCertificate[]>('/api/talent/v1/training/certificates', params)
    },
  },

  flow: {
    market(params: { keyword?: string; orgId?: TalentId; skills?: string; page?: number; size?: number } = {}) {
      return get<PageResult<TalentMarketItem>>('/api/talent/v1/flow/market', params)
    },
    apply(body: { demand_id: TalentId; apply_type?: string; reason?: string; employee_id?: TalentId }) {
      return post<TalentFlowApplyResult>('/api/talent/v1/flow/applications', body)
    },
    applications(params: {
      employeeId?: TalentId
      demandId?: TalentId
      flowStatus?: string
      page?: number
      size?: number
    } = {}) {
      return get<PageResult<TalentFlowApplication>>('/api/talent/v1/flow/applications', params)
    },
  },

  skills: {
    employeeSkills(id: TalentId) {
      return get<TalentEmployeeSkill[]>(`/api/talent/v1/skills/employees/${id}`)
    },
  },
}
