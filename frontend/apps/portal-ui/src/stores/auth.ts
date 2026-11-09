import { defineStore } from 'pinia'
import type { TokenPayload, UserInfo } from '@fenglin/shared'
import { clearTokens, getAccessToken, getCachedUserInfo, hasPermission, saveTokens, saveUserInfo } from '@fenglin/auth'
import { authApi } from '@fenglin/api-client'

export const useAuthStore = defineStore('portal-auth', {
  state: () => ({
    user: getCachedUserInfo() as UserInfo | null,
  }),
  getters: {
    isLoggedIn: () => Boolean(getAccessToken()),
    permissions: (state) => state.user?.permissions ?? [],
    displayName: (state) => state.user?.real_name || state.user?.name || state.user?.username || '未登录',
    userId: (state) => state.user?.user_id ?? (state.user?.sub ? Number(state.user.sub) : undefined),
  },
  actions: {
    async login(payload: { tenant_code: string; username: string; password: string }) {
      const token: TokenPayload = await authApi.login({ ...payload, client_type: 'PORTAL' })
      saveTokens(token)
      await this.loadUserInfo()
    },
    async loadUserInfo() {
      const user = await authApi.userinfo()
      this.user = user
      saveUserInfo(user)
    },
    can(required?: string | string[]) {
      return hasPermission(this.permissions, required)
    },
    reset() {
      clearTokens()
      this.user = null
    },
    async logout() {
      await authApi.logout()
      this.reset()
    },
  },
})
