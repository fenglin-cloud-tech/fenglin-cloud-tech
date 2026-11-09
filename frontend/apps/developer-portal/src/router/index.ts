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
