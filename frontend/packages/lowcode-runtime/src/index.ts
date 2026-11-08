export interface LowcodeFieldSchema {
  name?: string
  label?: string
  type?: string
  required?: boolean
}

export interface LowcodeSchemaSummary {
  fieldCount: number
  fields: LowcodeFieldSchema[]
  warnings: string[]
}

/**
 * 后端 schema 为自由 JsonNode，契约未定义固定结构。
 * 这里只做防御式解析，供管理台预览使用，不臆造业务渲染规则。
 */
export function summarizeSchema(schema: unknown): LowcodeSchemaSummary {
  if (!schema || typeof schema !== 'object') {
    return { fieldCount: 0, fields: [], warnings: ['schema 为空或不是对象'] }
  }
  const candidate = schema as Record<string, unknown>
  const rawFields = Array.isArray(candidate.fields)
    ? candidate.fields
    : Array.isArray(candidate.components)
      ? candidate.components
      : []
  const fields = rawFields
    .filter((item): item is Record<string, unknown> => !!item && typeof item === 'object')
    .map((item) => ({
      name: typeof item.name === 'string' ? item.name : undefined,
      label: typeof item.label === 'string' ? item.label : undefined,
      type: typeof item.type === 'string' ? item.type : undefined,
      required: item.required === true,
    }))
  const warnings: string[] = []
  if (fields.length === 0) warnings.push('未识别到 fields/components 数组，schema 结构由业务自定义')
  return { fieldCount: fields.length, fields, warnings }
}
