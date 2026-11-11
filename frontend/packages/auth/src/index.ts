import type { TokenPayload, UserInfo } from '@fenglin/shared'

const ACCESS_KEY = 'fl.access_token'
const REFRESH_KEY = 'fl.refresh_token'
const ID_KEY = 'fl.id_token'
const USER_KEY = 'fl.user_info'
const TENANT_KEY = 'fl.selected_tenant'

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function getIdToken(): string | null {
  return localStorage.getItem(ID_KEY)
}

export function saveTokens(payload: TokenPayload): void {
  localStorage.setItem(ACCESS_KEY, payload.access_token)
  if (payload.refresh_token) localStorage.setItem(REFRESH_KEY, payload.refresh_token)
  // 服务端登出需要 id_token_hint，否则会话与刷新令牌不会被吊销
  if (payload.id_token) localStorage.setItem(ID_KEY, payload.id_token)
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(ID_KEY)
  localStorage.removeItem(USER_KEY)
}

export function saveUserInfo(user: UserInfo): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getCachedUserInfo(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export function saveSelectedTenant(value: string | number | null): void {
  if (value === null || value === '') {
    localStorage.removeItem(TENANT_KEY)
    return
  }
  localStorage.setItem(TENANT_KEY, String(value))
}

export function getSelectedTenant(): string | null {
  return localStorage.getItem(TENANT_KEY)
}

export function hasPermission(permissions: string[] | undefined, required?: string | string[]): boolean {
  if (!required) return true
  if (!permissions || permissions.length === 0) return false
  const list = Array.isArray(required) ? required : [required]
  return list.some((code) => permissions.includes(code))
}

export function hasAllPermissions(permissions: string[] | undefined, required: string[]): boolean {
  if (required.length === 0) return true
  if (!permissions) return false
  return required.every((code) => permissions.includes(code))
}
