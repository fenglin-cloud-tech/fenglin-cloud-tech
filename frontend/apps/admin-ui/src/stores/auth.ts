import { defineStore } from 'pinia'
import type { MenuNode, TokenPayload, UserInfo } from '@fenglin/shared'
import { clearTokens, getAccessToken, getCachedUserInfo, getIdToken, hasPermission, saveUserInfo, saveTokens } from '@fenglin/auth'
import { authApi, iamApi } from '@fenglin/api-client'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: getCachedUserInfo() as UserInfo | null,
    menus: [] as MenuNode[],
    menuLoading: false,
    menuError: '',
    menuSource: 'none' as 'none' | 'api',
  }),
  getters: {
    isLoggedIn: () => Boolean(getAccessToken()),
    permissions: (state) => state.user?.permissions ?? [],
    displayName: (state) => state.user?.real_name || state.user?.username || '未登录',
  },
  actions: {
    async login(payload: { tenant_code: string; username: string; password: string; client_type: string }) {
      const token: TokenPayload = await authApi.login(payload)
      saveTokens(token)
      await this.loadUserInfo()
      await this.loadMenus()
    },
    async loadUserInfo() {
      const user = await authApi.userinfo()
      this.user = user
      saveUserInfo(user)
    },
    async loadMenus() {
      this.menuLoading = true
      this.menuError = ''
      try {
        this.menus = await iamApi.menus('ADMIN')
        this.menuSource = 'api'
      } catch (error) {
        this.menus = []
        this.menuSource = 'none'
        this.menuError = (error as Error).message
      } finally {
        this.menuLoading = false
      }
    },
    can(required?: string | string[]) {
      return hasPermission(this.permissions, required)
    },
    reset() {
      clearTokens()
      this.user = null
      this.menus = []
      this.menuError = ''
      this.menuSource = 'none'
    },
    async logout() {
      await authApi.logout(getIdToken() ?? undefined)
      this.reset()
    },
  },
})
