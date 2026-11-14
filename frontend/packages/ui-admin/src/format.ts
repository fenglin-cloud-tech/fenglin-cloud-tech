// 统一时间展示：后端可能返回 RFC3339Nano（2026-11-14T12:00:25.141285+08:00）或空格分隔格式。
// 列表统一展示到分钟，避免 ISO 串与微秒直出。
export function formatDateTime(value?: string | number | null): string {
  if (value === undefined || value === null || value === '') return '-'
  const raw = String(value)
  // Go 的 RFC3339Nano 时区可能是 +08（缺冒号），JS 无法直接解析，先补成 +08:00。
  const normalized = (raw.includes('T') ? raw : raw.replace(' ', 'T')).replace(/([+-]\d{2})$/, '$1:00')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return raw
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 金额统一展示：千分位 + 两位小数，空值回退 '-'，避免 50000 直出。
export function formatMoney(value?: string | number | null): string {
  if (value === undefined || value === null || value === '') return '-'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
