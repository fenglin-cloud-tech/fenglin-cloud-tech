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
}
