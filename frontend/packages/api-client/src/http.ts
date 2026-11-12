import type { ApiEnvelope, PageResult } from '@fenglin/shared'
import { clearTokens, getAccessToken, getRefreshToken, saveTokens } from '@fenglin/auth'

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly requestId?: string

  constructor(message: string, code = 'UNKNOWN', status = 0, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.requestId = requestId
  }
}

let refreshInFlight: Promise<boolean> | null = null
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  try {
    const res = await fetch('/api/hub/v1/sso/token:refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
    })
    if (!res.ok) return false
    const body = (await res.json()) as ApiEnvelope<{ access_token: string; refresh_token: string; token_type: string; expires_in: number }>
    if (body.code !== '0' || !body.data?.access_token) return false
    saveTokens(body.data)
    return true
  } catch {
    return false
  }
}

async function ensureRefreshed(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

export interface RequestOptions extends RequestInit {
  skipAuth?: boolean
}

export async function request<T>(path: string, options: RequestOptions = {}, retry = true): Promise<T> {
  const headers = new Headers(options.headers)
  const token = getAccessToken()
  if (token && !options.skipAuth) headers.set('Authorization', `Bearer ${token}`)
  const isForm = options.body instanceof FormData
  if (options.body && !isForm && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(path, { ...options, headers })
  } catch (error) {
    throw new ApiError(`网络请求失败：${(error as Error).message}`, 'NETWORK_ERROR', 0)
  }

  if (response.status === 401 && retry && !options.skipAuth && getRefreshToken()) {
    const ok = await ensureRefreshed()
    if (ok) return request<T>(path, options, false)
    clearTokens()
    onUnauthorized?.()
    throw new ApiError('登录已过期，请重新登录', 'UNAUTHORIZED', 401)
  }

  if (response.status === 204) return undefined as T

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    if (!response.ok) throw new ApiError(`请求失败（HTTP ${response.status}）`, 'HTTP_ERROR', response.status)
    return response as unknown as T
  }

  const body = (await response.json()) as ApiEnvelope<T>
  if (!response.ok || body.code !== '0') {
    if (response.status === 401 && !options.skipAuth) {
      clearTokens()
      onUnauthorized?.()
    }
    throw new ApiError(body.message || `请求失败（HTTP ${response.status}）`, body.code, response.status, body.request_id)
  }
  return body.data
}

export function get<T>(path: string, params?: Record<string, unknown>): Promise<T> {
  const query = new URLSearchParams()
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  })
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return request<T>(`${path}${suffix}`)
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'PUT',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function postForm<T>(path: string, form: FormData): Promise<T> {
  return request<T>(path, { method: 'POST', body: form })
}

export async function download(path: string, fallbackName: string): Promise<void> {
  const headers = new Headers()
  const token = getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(path, { headers })
  if (!response.ok) throw new ApiError(`下载失败（HTTP ${response.status}）`, 'DOWNLOAD_ERROR', response.status)
  const blob = await response.blob()
  const disposition = response.headers.get('content-disposition') ?? ''
  const match = /filename\*?=(?:UTF-8'')?["']?([^"';]+)/i.exec(disposition)
  const filename = match ? decodeURIComponent(match[1]) : fallbackName
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

export type { PageResult }
