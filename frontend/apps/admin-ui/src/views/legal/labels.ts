export const contractStatus: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审批',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已审批',
  SEALING: '用印中',
  SEALED: '已用印',
  SIGNING: '签署中',
  SIGNED: '已签署',
  ACTIVE: '履约中',
  ARCHIVED: '已归档',
  TERMINATED: '已终止',
  REJECTED: '已驳回',
}

export const contractCategory: Record<string, string> = {
  SALES: '销售合同',
  PURCHASE: '采购合同',
  SERVICE: '服务合同',
  LEASE: '租赁合同',
  LABOR: '劳动合同',
  NDA: '保密协议',
  OTHER: '其他',
}

export const riskLevel: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高' }

export const sealStatus: Record<string, string> = {
  NONE: '未用印',
  PENDING: '待用印',
  APPROVED: '已审批',
  EXECUTING: '用印中',
  DONE: '已完成',
  REJECTED: '已驳回',
}

export const signStatusMap: Record<string, string> = {
  NONE: '未签署',
  PENDING: '待签署',
  PARTIAL: '部分签署',
  DONE: '已签署',
  REJECTED: '已拒签',
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

export const eventStatus: Record<string, string> = {
  NEW: '待处理',
  TRIAGING: '分级中',
  INVESTIGATING: '调查中',
  HANDLED: '已处置',
  FALSE_POSITIVE: '误报',
  ESCALATED: '已升级',
  CLOSED: '已关闭',
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

import { formatDateTime } from '@fenglin/ui-admin'

export function fmtTime(value?: string): string {
  return formatDateTime(value)
}

export function fmtAmount(value?: number | string, currency = 'CNY'): string {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return `${currency} ${num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
