export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  request_id?: string
  timestamp?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface UserInfo {
  /** 认证服务 /sso/userinfo 以 sub 返回用户 ID（字符串），部分网关会同时返回 user_id。 */
  sub?: string
  /** 雪花 ID，后端按字符串序列化 */
  user_id: string
  username: string
  /** 认证服务返回的展示名（与 real_name 二选一） */
  name?: string
  real_name?: string
  /** 雪花 ID，后端按字符串序列化 */
  tenant_id: string
  tenant_code?: string
  client_type: string
  permissions: string[]
  roles?: string[]
}

export interface TokenPayload {
  access_token: string
  refresh_token: string
  id_token?: string
  token_type: string
  expires_in: number
  refresh_expires_in?: number
}

export interface MenuNode {
  id: number
  code: string
  name: string
  client_type: string
  path?: string
  parent_code?: string | null
  permission_code?: string | null
  icon?: string | null
  sort?: number
  visible?: boolean
  children?: MenuNode[]
}

export interface SelectOption {
  label: string
  value: string | number
}

export function formatDateTime(value?: string | number | null): string {
  if (value === undefined || value === null || value === '') return '-'
  const date = typeof value === 'number' ? new Date(value) : new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function toIsoOrUndefined(value?: string | null): string | undefined {
  return value ? new Date(value).toISOString() : undefined
}

export function splitCsv(value: string): string[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

export const APP_TITLE = '丰麟云枢 · 中台管理台'
