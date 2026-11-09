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
