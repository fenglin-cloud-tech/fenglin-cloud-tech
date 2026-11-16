import { defineStore } from 'pinia'
import type { MenuNode, TokenPayload, UserInfo } from '@fenglin/shared'
import { clearTokens, getAccessToken, getCachedUserInfo, getIdToken, getRefreshToken, getSwitchedTenant, hasPermission, saveSwitchedTenant, saveTokens, saveUserInfo } from '@fenglin/auth'
import { authApi, iamApi } from '@fenglin/api-client'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: getCachedUserInfo() as UserInfo | null,
    menus: [] as MenuNode[],
    menuLoading: false,
    menuError: '',
    menuSource: 'none' as 'none' | 'api',
    switchedTenantId: getSwitchedTenant(),
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
      this.switchedTenantId = null
      saveSwitchedTenant(null)
      await this.loadUserInfo()
      await this.loadMenus()
    },
    /** OPEN-06：平台身份切到目标租户，拿到 30 分钟只读令牌（refresh 仍属 home 租户）。 */
    async switchTenant(targetTenantId: string | number, reason?: string) {
      const token = await authApi.switchTenant({ target_tenant_id: targetTenantId, reason })
      saveTokens(token)
      this.switchedTenantId = String(targetTenantId)
      saveSwitchedTenant(targetTenantId)
      await this.loadUserInfo()
      await this.loadMenus()
    },
    /** 显式切回 home 租户：用 home refresh token 换回正常令牌。 */
    async switchBack() {
      const refreshToken = getRefreshToken()
      if (!refreshToken) {
        throw new Error('登录状态已失效，请重新登录')
      }
      const token = await authApi.refresh(refreshToken)
      saveTokens(token)
      this.switchedTenantId = null
      saveSwitchedTenant(null)
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
      this.switchedTenantId = null
    },
    async logout() {
      await authApi.logout(getIdToken() ?? undefined)
      this.reset()
    },
  },
})
