import { ApiError } from '@fenglin/api-client'

export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return `无权限执行该操作（${error.code}）。请联系管理员分配权限。`
    if (error.status === 401) return error.message || '登录已过期，请重新登录。'
    if (error.status === 404) return `资源不存在（${error.code}）。`
    return error.requestId ? `${error.message}（请求号 ${error.requestId}）` : error.message
  }
  return (error as Error)?.message ?? '未知错误'
}
