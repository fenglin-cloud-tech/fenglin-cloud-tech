import { ApiError } from '@fenglin/api-client'

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return `无权限执行该操作（${error.message}）`
    if (error.status === 404) return `接口不存在或服务未启动（${error.message}）`
    if (error.status === 401) return '登录已过期，请重新登录'
    return `${error.message}${error.code ? `（${error.code}）` : ''}`
  }
  return (error as Error)?.message ?? String(error)
}

export function isForbidden(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403
}
