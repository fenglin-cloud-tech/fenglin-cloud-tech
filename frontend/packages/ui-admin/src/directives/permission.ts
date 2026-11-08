import type { Directive } from 'vue'
import { getCachedUserInfo, hasPermission } from '@fenglin/auth'

export const permissionDirective: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const permissions = getCachedUserInfo()?.permissions
    if (!hasPermission(permissions, binding.value)) {
      el.remove()
    }
  },
}

export function canUse(code?: string | string[]): boolean {
  return hasPermission(getCachedUserInfo()?.permissions, code)
}
