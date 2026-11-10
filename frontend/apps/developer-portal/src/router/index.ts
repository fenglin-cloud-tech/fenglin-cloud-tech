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
    component: () => import('@/layouts/DevLayout.vue'),
    redirect: '/apps',
    children: [
      {
        path: 'apps',
        name: 'apps',
        component: () => import('@/views/AppsView.vue'),
        meta: { title: '我的应用' },
      },
      {
        path: 'apps/:id',
        name: 'app-detail',
        component: () => import('@/views/AppDetailView.vue'),
        meta: { title: '应用详情' },
      },
      {
        path: 'logs',
        name: 'call-logs',
        component: () => import('@/views/CallLogsView.vue'),
        meta: { title: '调用日志' },
      },
      {
        path: 'quotas',
        name: 'quotas',
        component: () => import('@/views/QuotasView.vue'),
        meta: { title: '配额与用量' },
      },
      {
        path: 'docs',
        name: 'api-docs',
        component: () => import('@/views/ApiDocsView.vue'),
        meta: { title: '接口文档' },
      },
      {
        path: 'guide',
        name: 'guide',
        component: () => import('@/views/GuideView.vue'),
        meta: { title: '接入指引' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/apps' },
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
  const base = '丰麟云枢 · 开放平台'
  document.title = to.meta.title ? `${String(to.meta.title)} · ${base}` : base
})

export default router
