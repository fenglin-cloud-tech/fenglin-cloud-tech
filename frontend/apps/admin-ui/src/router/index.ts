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
