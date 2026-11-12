import { defineStore } from 'pinia'
import type { TenantView } from '@fenglin/api-client'
import { getSelectedTenant, saveSelectedTenant } from '@fenglin/auth'
import { tenantApi } from '@fenglin/api-client'

export const useTenantStore = defineStore('tenant', {
  state: () => ({
    current: null as TenantView | null,
    tenants: [] as TenantView[],
    selectedTenantId: getSelectedTenant(),
    loading: false,
    error: '',
  }),
  getters: {
    selectedTenant(state): TenantView | null {
      if (!state.selectedTenantId) return state.current
      return state.tenants.find((item) => String(item.id) === state.selectedTenantId) ?? state.current
    },
  },
  actions: {
    async loadCurrent() {
      try {
        this.current = await tenantApi.current()
        if (!this.selectedTenantId) this.selectedTenantId = String(this.current.id)
        saveSelectedTenant(this.selectedTenantId)
      } catch (error) {
        this.error = (error as Error).message
      }
    },
    async loadList() {
      this.loading = true
      this.error = ''
      try {
        const result = await tenantApi.list({ page: 1, size: 200 })
        this.tenants = result.records
      } catch (error) {
        this.error = (error as Error).message
      } finally {
        this.loading = false
      }
    },
    selectTenant(id: string | number) {
      this.selectedTenantId = String(id)
      saveSelectedTenant(id)
    },
  },
})
