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
        meta: { title: '消息', perm: 'biz:collab:im:chat' },
      },
      {
        path: 'docs',
        name: 'docs',
        component: () => import('@/views/collab/DocsView.vue'),
        meta: { title: '文档', perm: 'biz:collab:doc:read' },
      },
      {
        path: 'my-schedule',
        name: 'my-schedule',
        component: () => import('@/views/collab/MyScheduleView.vue'),
        meta: { title: '我的日程', perm: 'biz:collab:calendar:read' },
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
        meta: { title: '我的 OKR', perm: 'biz:collab:okr:read' },
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
