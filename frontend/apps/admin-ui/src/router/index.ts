import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { getAccessToken } from '@fenglin/auth'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/overview',
    children: [
      { path: 'overview', name: 'overview', component: () => import('@/views/OverviewView.vue'), meta: { title: '运营概览' } },
      { path: 'tenants', name: 'tenants', component: () => import('@/views/TenantsView.vue'), meta: { title: '租户管理' } },
      { path: 'iam', name: 'iam', component: () => import('@/views/IamView.vue'), meta: { title: '身份与权限' } },
      { path: 'workflow', name: 'workflow', component: () => import('@/views/WorkflowView.vue'), meta: { title: '流程中心' } },
      { path: 'messages', name: 'messages', component: () => import('@/views/MessagesView.vue'), meta: { title: '消息中心' } },
      { path: 'files', name: 'files', component: () => import('@/views/FilesView.vue'), meta: { title: '文件中心' } },
      { path: 'audit', name: 'audit', component: () => import('@/views/AuditView.vue'), meta: { title: '审计中心' } },
      { path: 'search', name: 'search', component: () => import('@/views/SearchView.vue'), meta: { title: '搜索中心' } },
      { path: 'openapi', name: 'openapi', component: () => import('@/views/OpenApiView.vue'), meta: { title: '开放平台' } },
      { path: 'report', name: 'report', component: () => import('@/views/ReportView.vue'), meta: { title: '报表中心' } },
      { path: 'lowcode', name: 'lowcode', component: () => import('@/views/LowcodeView.vue'), meta: { title: '低代码' } },
      { path: 'ai', name: 'ai', component: () => import('@/views/AiView.vue'), meta: { title: 'AI 中台' } },
      { path: 'gateway', name: 'gateway', component: () => import('@/views/GatewayView.vue'), meta: { title: '网关治理' } },
      { path: 'crm/leads', name: 'crm-leads', component: () => import('@/views/crm/LeadsView.vue'), meta: { title: '线索管理' } },
      { path: 'crm/customers', name: 'crm-customers', component: () => import('@/views/crm/CustomersView.vue'), meta: { title: '客户管理' } },
      { path: 'crm/opportunities', name: 'crm-opportunities', component: () => import('@/views/crm/OpportunitiesView.vue'), meta: { title: '商机管理' } },
      { path: 'crm/quotations', name: 'crm-quotations', component: () => import('@/views/crm/QuotationsView.vue'), meta: { title: '报价管理' } },
      { path: 'crm/contracts', name: 'crm-contracts', component: () => import('@/views/crm/ContractsView.vue'), meta: { title: '合同管理' } },
      { path: 'crm/payments', name: 'crm-payments', component: () => import('@/views/crm/PaymentsView.vue'), meta: { title: '回款管理' } },
      { path: 'crm/collections', name: 'crm-collections', component: () => import('@/views/crm/CollectionsView.vue'), meta: { title: '逾期催收' } },
      { path: 'collab/org-members', name: 'collab-org-members', component: () => import('@/views/collab/OrgMembersView.vue'), meta: { title: '组织与成员' } },
      { path: 'collab/im', name: 'collab-im', component: () => import('@/views/collab/ImAdminView.vue'), meta: { title: 'IM 会话管理' } },
      { path: 'collab/docs', name: 'collab-docs', component: () => import('@/views/collab/DocsView.vue'), meta: { title: '文档中心' } },
      { path: 'collab/calendar', name: 'collab-calendar', component: () => import('@/views/collab/CalendarView.vue'), meta: { title: '日历与会议室' } },
      { path: 'collab/meetings', name: 'collab-meetings', component: () => import('@/views/collab/MeetingsView.vue'), meta: { title: '会议管理' } },
      { path: 'collab/okr', name: 'collab-okr', component: () => import('@/views/collab/OkrView.vue'), meta: { title: 'OKR 管理' } },
      { path: 'collab/approvals', name: 'collab-approvals', component: () => import('@/views/collab/ApprovalsView.vue'), meta: { title: '审批中心' } },
      { path: 'talent/org', name: 'talent-org', component: () => import('@/views/talent/TalentOrgView.vue'), meta: { title: '组织与岗位' } },
      { path: 'talent/employees', name: 'talent-employees', component: () => import('@/views/talent/TalentEmployeesView.vue'), meta: { title: '员工档案' } },
      { path: 'talent/recruit', name: 'talent-recruit', component: () => import('@/views/talent/TalentRecruitView.vue'), meta: { title: '招聘管理' } },
      { path: 'talent/lifecycle', name: 'talent-lifecycle', component: () => import('@/views/talent/TalentLifecycleView.vue'), meta: { title: '入转调离' } },
      { path: 'talent/attendance', name: 'talent-attendance', component: () => import('@/views/talent/TalentAttendanceView.vue'), meta: { title: '考勤与假期' } },
      { path: 'talent/payroll', name: 'talent-payroll', component: () => import('@/views/talent/TalentPayrollView.vue'), meta: { title: '薪酬管理' } },
      { path: 'talent/performance', name: 'talent-performance', component: () => import('@/views/talent/TalentPerformanceView.vue'), meta: { title: '绩效管理' } },
      { path: 'talent/training', name: 'talent-training', component: () => import('@/views/talent/TalentTrainingView.vue'), meta: { title: '培训管理' } },
      { path: 'finance/ledger', name: 'finance-ledger', component: () => import('@/views/finance/FinanceLedgerView.vue'), meta: { title: '账套与科目' } },
      { path: 'finance/vouchers', name: 'finance-vouchers', component: () => import('@/views/finance/FinanceVoucherView.vue'), meta: { title: '凭证管理' } },
      { path: 'finance/reports', name: 'finance-reports', component: () => import('@/views/finance/FinanceReportView.vue'), meta: { title: '财务报表' } },
      { path: 'finance/arap', name: 'finance-arap', component: () => import('@/views/finance/FinanceArapView.vue'), meta: { title: '应收应付' } },
      { path: 'finance/expenses', name: 'finance-expenses', component: () => import('@/views/finance/FinanceExpenseView.vue'), meta: { title: '报销与预算' } },
      { path: 'finance/projects', name: 'finance-projects', component: () => import('@/views/finance/FinanceProjectView.vue'), meta: { title: '项目核算' } },
      { path: 'finance/assets', name: 'finance-assets', component: () => import('@/views/finance/FinanceAssetView.vue'), meta: { title: '资产与盘点' } },
      { path: 'finance/bank', name: 'finance-bank', component: () => import('@/views/finance/FinanceBankView.vue'), meta: { title: '银企对账' } },
      { path: 'finance/carbon', name: 'finance-carbon', component: () => import('@/views/finance/FinanceCarbonView.vue'), meta: { title: '碳与 ESG' } },
      { path: 'rnd/products', name: 'rnd-products', component: () => import('@/views/rnd/RndProductView.vue'), meta: { title: '产品与成员' } },
      { path: 'rnd/requirements', name: 'rnd-requirements', component: () => import('@/views/rnd/RndRequirementView.vue'), meta: { title: '需求管理' } },
      { path: 'rnd/sprints', name: 'rnd-sprints', component: () => import('@/views/rnd/RndSprintBoardView.vue'), meta: { title: '迭代与看板' } },
      { path: 'rnd/tasks', name: 'rnd-tasks', component: () => import('@/views/rnd/RndTaskView.vue'), meta: { title: '任务与工时' } },
      { path: 'rnd/defects', name: 'rnd-defects', component: () => import('@/views/rnd/RndDefectView.vue'), meta: { title: '缺陷管理' } },
      { path: 'rnd/testing', name: 'rnd-testing', component: () => import('@/views/rnd/RndTestView.vue'), meta: { title: '测试管理' } },
      { path: 'rnd/code', name: 'rnd-code', component: () => import('@/views/rnd/RndCodeView.vue'), meta: { title: '代码与流水线' } },
      { path: 'rnd/releases', name: 'rnd-releases', component: () => import('@/views/rnd/RndReleaseView.vue'), meta: { title: '发布管理' } },
      { path: 'rnd/metrics', name: 'rnd-metrics', component: () => import('@/views/rnd/RndMetricView.vue'), meta: { title: '效能度量' } },
      { path: 'rnd/integration', name: 'rnd-integration', component: () => import('@/views/rnd/RndIntegrationView.vue'), meta: { title: '集成配置' } },
      { path: 'service/tickets', name: 'service-tickets', component: () => import('@/views/service/ServiceTicketView.vue'), meta: { title: '工单工作台' } },
      { path: 'service/catalog', name: 'service-catalog', component: () => import('@/views/service/ServiceCatalogView.vue'), meta: { title: '服务目录与 SLA' } },
      { path: 'service/kb', name: 'service-kb', component: () => import('@/views/service/ServiceKbView.vue'), meta: { title: '知识库' } },
      { path: 'service/desk-config', name: 'service-desk-config', component: () => import('@/views/service/ServiceDeskConfigView.vue'), meta: { title: '工单配置' } },
      { path: 'service/problem-change', name: 'service-problem-change', component: () => import('@/views/service/ServiceProblemChangeView.vue'), meta: { title: '问题与变更' } },
      { path: 'service/assets', name: 'service-assets', component: () => import('@/views/service/ServiceAssetCsatView.vue'), meta: { title: '资产与满意度' } },
      { path: 'service/mining', name: 'service-mining', component: () => import('@/views/service/ServiceMiningView.vue'), meta: { title: '流程挖掘' } },
      { path: 'service/rpa', name: 'service-rpa', component: () => import('@/views/service/ServiceRpaView.vue'), meta: { title: 'RPA 自动化' } },
      { path: 'service/lowcode', name: 'service-lowcode', component: () => import('@/views/service/ServiceLowcodeView.vue'), meta: { title: '低代码应用' } },
      { path: 'service/reports', name: 'service-reports', component: () => import('@/views/service/ServiceReportView.vue'), meta: { title: '服务报表' } },
      { path: 'intelligence/overview', name: 'intelligence-overview', component: () => import('@/views/intelligence/AiOverviewView.vue'), meta: { title: '智能运营概览' } },
      { path: 'intelligence/knowledge', name: 'intelligence-knowledge', component: () => import('@/views/intelligence/AiKnowledgeView.vue'), meta: { title: '知识库' } },
      { path: 'intelligence/data-sources', name: 'intelligence-data-sources', component: () => import('@/views/intelligence/AiDataSourceView.vue'), meta: { title: '数据源' } },
      { path: 'intelligence/documents', name: 'intelligence-documents', component: () => import('@/views/intelligence/AiDocumentView.vue'), meta: { title: '文档与索引' } },
      { path: 'intelligence/kg', name: 'intelligence-kg', component: () => import('@/views/intelligence/AiKgView.vue'), meta: { title: '知识图谱' } },
      { path: 'intelligence/agents', name: 'intelligence-agents', component: () => import('@/views/intelligence/AiAgentView.vue'), meta: { title: '智能体与护栏' } },
      { path: 'intelligence/metrics', name: 'intelligence-metrics', component: () => import('@/views/intelligence/AiMetricView.vue'), meta: { title: '指标与数据集' } },
      { path: 'intelligence/dashboards', name: 'intelligence-dashboards', component: () => import('@/views/intelligence/AiDashboardView.vue'), meta: { title: '看板与图表' } },
      { path: 'intelligence/insights', name: 'intelligence-insights', component: () => import('@/views/intelligence/AiInsightView.vue'), meta: { title: '自动洞察与归因' } },
      { path: 'intelligence/governance', name: 'intelligence-governance', component: () => import('@/views/intelligence/AiGovernanceView.vue'), meta: { title: '双模式与治理' } },
      { path: 'legal/contracts', name: 'legal-contracts', component: () => import('@/views/legal/ContractLedgerView.vue'), meta: { title: '合同台账' } },
      { path: 'legal/templates', name: 'legal-templates', component: () => import('@/views/legal/ContractTemplateView.vue'), meta: { title: '合同模板' } },
      { path: 'legal/parties', name: 'legal-parties', component: () => import('@/views/legal/ContractPartyView.vue'), meta: { title: '相对方' } },
      { path: 'legal/performance', name: 'legal-performance', component: () => import('@/views/legal/ContractPerformanceView.vue'), meta: { title: '履约与到期提醒' } },
      { path: 'legal/seals', name: 'legal-seals', component: () => import('@/views/legal/SealManageView.vue'), meta: { title: '印章管理' } },
      { path: 'legal/seal-approvals', name: 'legal-seal-approvals', component: () => import('@/views/legal/SealApprovalView.vue'), meta: { title: '用印审批' } },
      { path: 'legal/sign-evidence', name: 'legal-sign-evidence', component: () => import('@/views/legal/SignEvidenceView.vue'), meta: { title: '签署与存证' } },
      { path: 'legal/dlp-rules', name: 'legal-dlp-rules', component: () => import('@/views/legal/DlpRulePolicyView.vue'), meta: { title: 'DLP 规则与策略' } },
      { path: 'legal/dlp-events', name: 'legal-dlp-events', component: () => import('@/views/legal/DlpEventView.vue'), meta: { title: 'DLP 事件处置' } },
      { path: 'legal/dlp-watermark', name: 'legal-dlp-watermark', component: () => import('@/views/legal/DlpWatermarkAuditView.vue'), meta: { title: 'DLP 水印与审计' } },
      { path: 'legal/reports', name: 'legal-reports', component: () => import('@/views/legal/LegalReportView.vue'), meta: { title: '法务报表' } },
      { path: 'legal/config', name: 'legal-config', component: () => import('@/views/legal/LegalConfigView.vue'), meta: { title: '法务配置' } },
      { path: '403', name: 'forbidden', component: () => import('@/views/ForbiddenView.vue'), meta: { title: '无访问权限' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!getAccessToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const auth = useAuthStore()
  if (!auth.user) {
    try {
      await auth.loadUserInfo()
      await auth.loadMenus()
    } catch {
      auth.reset()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

router.afterEach((to) => {
  const base = '丰麟云枢 · 中台管理台'
  document.title = to.meta.title ? `${String(to.meta.title)} · ${base}` : base
})

export default router
