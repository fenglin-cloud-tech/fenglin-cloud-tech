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
    component: () => import('@/layouts/PortalLayout.vue'),
    redirect: '/overview',
    children: [
      {
        path: 'overview',
        name: 'overview',
        component: () => import('@/views/OverviewView.vue'),
        meta: { title: '我的工作台' },
      },
      {
        path: 'my-leads',
        name: 'my-leads',
        component: () => import('@/views/MyLeadsView.vue'),
        meta: { title: '我的线索', perm: 'crm:lead:view' },
      },
      {
        path: 'my-customers',
        name: 'my-customers',
        component: () => import('@/views/MyCustomersView.vue'),
        meta: { title: '我的客户', perm: 'crm:customer:view' },
      },
      {
        path: 'my-opportunities',
        name: 'my-opportunities',
        component: () => import('@/views/MyOpportunitiesView.vue'),
        meta: { title: '我的商机', perm: 'crm:opportunity:view' },
      },
      {
        path: 'my-quotations',
        name: 'my-quotations',
        component: () => import('@/views/MyQuotationsView.vue'),
        meta: { title: '我的报价/合同', perm: 'crm:quotation:view' },
      },
      {
        path: 'payment-reminders',
        name: 'payment-reminders',
        component: () => import('@/views/PaymentRemindersView.vue'),
        meta: { title: '回款提醒', perm: 'crm:contract:view' },
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/collab/MessagesView.vue'),
        meta: { title: '消息', perm: 'biz:collab:im:self:chat' },
      },
      {
        path: 'docs',
        name: 'docs',
        component: () => import('@/views/collab/DocsView.vue'),
        meta: { title: '文档', perm: 'biz:collab:doc:self:read' },
      },
      {
        path: 'my-schedule',
        name: 'my-schedule',
        component: () => import('@/views/collab/MyScheduleView.vue'),
        meta: { title: '我的日程', perm: 'biz:collab:calendar:self:read' },
      },
      {
        path: 'meetings',
        name: 'meetings',
        component: () => import('@/views/collab/MeetingInvitesView.vue'),
        meta: { title: '会议邀请', perm: 'biz:collab:meeting:join' },
      },
      {
        path: 'my-okr',
        name: 'my-okr',
        component: () => import('@/views/collab/MyOkrView.vue'),
        meta: { title: '我的 OKR', perm: 'biz:collab:okr:self:read' },
      },
      {
        path: 'approvals',
        name: 'approvals',
        component: () => import('@/views/collab/ApprovalsView.vue'),
        meta: { title: '审批', perm: 'biz:collab:approval:read' },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/collab/NotificationsView.vue'),
        meta: { title: '通知中心', perm: 'biz:collab:workbench:personal' },
      },
      {
        path: 'my-profile',
        name: 'my-profile',
        component: () => import('@/views/talent/MyProfileView.vue'),
        meta: { title: '我的档案', perm: 'talent:employee:view' },
      },
      {
        path: 'my-attendance',
        name: 'my-attendance',
        component: () => import('@/views/talent/MyAttendanceView.vue'),
        meta: { title: '我的考勤与请假', perm: 'talent:attendance:view' },
      },
      {
        path: 'my-payslip',
        name: 'my-payslip',
        component: () => import('@/views/talent/MyPayslipView.vue'),
        meta: { title: '我的薪酬单' },
      },
      {
        path: 'my-performance',
        name: 'my-performance',
        component: () => import('@/views/talent/MyPerformanceView.vue'),
        meta: { title: '我的绩效' },
      },
      {
        path: 'my-training',
        name: 'my-training',
        component: () => import('@/views/talent/MyTrainingView.vue'),
        meta: { title: '培训与课程', perm: 'talent:course:list' },
      },
      {
        path: 'internal-opportunities',
        name: 'internal-opportunities',
        component: () => import('@/views/talent/InternalOpportunitiesView.vue'),
        meta: { title: '内部机会', perm: 'biz:talent:flow:market' },
      },
      {
        path: 'my-expenses',
        name: 'my-expenses',
        component: () => import('@/views/finance/MyExpensesView.vue'),
        meta: { title: '我的报销', perm: 'biz:finance:expense:view' },
      },
      {
        path: 'my-timesheets',
        name: 'my-timesheets',
        component: () => import('@/views/finance/MyTimesheetView.vue'),
        meta: { title: '我的工时', perm: 'biz:finance:timesheet:view' },
      },
      {
        path: 'my-carbon',
        name: 'my-carbon',
        component: () => import('@/views/finance/MyCarbonView.vue'),
        meta: { title: '碳活动填报', perm: 'biz:finance:carbon:submit' },
      },
      {
        path: 'my-rnd-tasks',
        name: 'my-rnd-tasks',
        component: () => import('@/views/rnd/MyRndTasksView.vue'),
        meta: { title: '我的任务', perm: 'biz:devops:task:self:view' },
      },
      {
        path: 'my-rnd-worklogs',
        name: 'my-rnd-worklogs',
        component: () => import('@/views/rnd/MyRndWorklogView.vue'),
        meta: { title: '工时填报', perm: 'biz:devops:worklog:self:view' },
      },
      {
        path: 'my-rnd-defects',
        name: 'my-rnd-defects',
        component: () => import('@/views/rnd/MyRndDefectView.vue'),
        meta: { title: '缺陷提报', perm: 'biz:devops:defect:self:view' },
      },
      {
        path: 'my-rnd-contribution',
        name: 'my-rnd-contribution',
        component: () => import('@/views/rnd/MyRndContributionView.vue'),
        meta: { title: '我的贡献', perm: 'biz:devops:contribution:self:view' },
      },
      {
        path: 'my-service-tickets',
        name: 'my-service-tickets',
        component: () => import('@/views/service/MyServiceTicketsView.vue'),
        meta: { title: '我的工单', perm: 'service:ticket:view' },
      },
      {
        path: 'service-catalog',
        name: 'service-catalog',
        component: () => import('@/views/service/ServiceCatalogView.vue'),
        meta: { title: '服务申请', perm: 'service:catalog:view' },
      },
      {
        path: 'service-kb',
        name: 'service-kb',
        component: () => import('@/views/service/MyServiceKbView.vue'),
        meta: { title: '知识库', perm: 'service:kb:view' },
      },
      {
        path: 'my-service-assets',
        name: 'my-service-assets',
        component: () => import('@/views/service/MyServiceAssetsView.vue'),
        meta: { title: '我的资产', perm: 'service:asset:self:view' },
      },
      {
        path: 'ai-workbench',
        name: 'ai-workbench',
        component: () => import('@/views/intelligence/MyAiWorkbenchView.vue'),
        meta: { title: '智能工作台', perm: 'ai:chat:use' },
      },
      {
        path: 'ai-copilot',
        name: 'ai-copilot',
        component: () => import('@/views/intelligence/AiCopilotView.vue'),
        meta: { title: 'AI Copilot', perm: 'ai:chat:use' },
      },
      {
        path: 'ai-search',
        name: 'ai-search',
        component: () => import('@/views/intelligence/AiSearchView.vue'),
        meta: { title: '知识搜索', perm: 'ai:search:query' },
      },
      {
        path: 'ai-kg',
        name: 'ai-kg',
        component: () => import('@/views/intelligence/AiKgView.vue'),
        meta: { title: '知识图谱', perm: 'ai:kg:query' },
      },
      {
        path: 'ai-tools',
        name: 'ai-tools',
        component: () => import('@/views/intelligence/AiToolsView.vue'),
        meta: { title: '文本工具箱', perm: 'ai:writing:use' },
      },
      {
        path: 'ai-agents',
        name: 'ai-agents',
        component: () => import('@/views/intelligence/AiAgentRunView.vue'),
        meta: { title: '智能体', perm: 'ai:agent:run' },
      },
      {
        path: 'ai-dashboards',
        name: 'ai-dashboards',
        component: () => import('@/views/intelligence/AiDashboardView.vue'),
        meta: { title: '数据看板', perm: 'ai:bi:view' },
      },
      {
        path: 'ai-insights',
        name: 'ai-insights',
        component: () => import('@/views/intelligence/AiInsightAskView.vue'),
        meta: { title: '洞察与问数', perm: 'ai:bi:view' },
      },
      {
        path: 'legal',
        name: 'legal-workbench',
        component: () => import('@/views/legal/LegalWorkbenchView.vue'),
        meta: { title: '法务工作台' },
      },
      {
        path: 'my-contracts',
        name: 'my-contracts',
        component: () => import('@/views/legal/MyContractsView.vue'),
        meta: { title: '我的合同', perm: 'legal:contract:view:self' },
      },
      {
        path: 'my-sign-tasks',
        name: 'my-sign-tasks',
        component: () => import('@/views/legal/MySignTasksView.vue'),
        meta: { title: '我的签署任务', perm: 'legal:sign:self:execute' },
      },
      {
        path: 'my-seal-applications',
        name: 'my-seal-applications',
        component: () => import('@/views/legal/MySealApplicationsView.vue'),
        meta: { title: '用印申请', perm: 'legal:seal:apply' },
      },
      {
        path: 'my-data-release',
        name: 'my-data-release',
        component: () => import('@/views/legal/MyDataReleaseView.vue'),
        meta: { title: '数据放行申请', perm: 'legal:dlp:release:apply' },
      },
      {
        path: 'my-evidence',
        name: 'my-evidence',
        component: () => import('@/views/legal/MyEvidenceView.vue'),
        meta: { title: '我的存证', perm: 'legal:evidence:view' },
      },
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
    } catch {
      auth.reset()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

router.afterEach((to) => {
  const base = '丰麟云枢 · 员工门户'
  document.title = to.meta.title ? `${String(to.meta.title)} · ${base}` : base
})

export default router
