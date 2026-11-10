export const contractStatus: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审批',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已审批',
  SEALING: '用印中',
  SIGNING: '签署中',
  ACTIVE: '履约中',
  ARCHIVED: '已归档',
  TERMINATED: '已终止',
  REJECTED: '已驳回',
}

export const sealAppStatus: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已通过',
  EXECUTING: '待执行',
  DONE: '已执行',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
}

export const signTaskStatus: Record<string, string> = {
  PENDING: '待签署',
  SIGNED: '已签署',
  REJECTED: '已拒签',
  EXPIRED: '已过期',
}

export const releaseStatus: Record<string, string> = {
  PENDING: '待审批',
  APPROVED: '已放行',
  REJECTED: '已驳回',
}

export const dataLevel: Record<string, string> = {
  L1: 'L1 公开',
  L2: 'L2 内部',
  L3: 'L3 秘密',
  L4: 'L4 机密',
}

export function label(map: Record<string, string>, value?: string): string {
  if (!value) return '-'
  return map[value] ?? value
}

export function fmtTime(value?: string): string {
  if (!value) return '-'
  return String(value).replace('T', ' ').replace(/\.\d+/, '').replace(/\+.*$/, '').replace(/Z$/, '')
}

export function fmtAmount(value?: number | string, currency = 'CNY'): string {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return `${currency} ${num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
