<script setup lang="ts">
import { computed } from 'vue'

/**
 * COL-01a：按审批模板 form_schema 渲染动态表单字段。
 * 约定结构：{ "fields": [{ key, label, type, required, options }] }，
 * type 支持 text/textarea/number/date/select；未定义 fields 时由调用方回退到 JSON 文本。
 */
const props = defineProps<{
  schema?: Record<string, unknown>
  modelValue: Record<string, unknown>
}>()
const emit = defineEmits<{ 'update:modelValue': [Record<string, unknown>] }>()

interface Field {
  key: string
  label: string
  type: string
  required: boolean
  options: unknown[]
}

const fields = computed<Field[]>(() => {
  const raw = props.schema?.fields
  if (!Array.isArray(raw)) return []
  const result: Field[] = []
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue
    const field = item as Record<string, unknown>
    const key = field.key === undefined || field.key === null ? '' : String(field.key)
    if (!key) continue
    result.push({
      key,
      label: field.label ? String(field.label) : key,
      type: field.type ? String(field.type) : 'text',
      required: field.required === true,
      options: Array.isArray(field.options) ? field.options : [],
    })
  }
  return result
})

function valueOf(key: string): string {
  const value = props.modelValue[key]
  return value === undefined || value === null ? '' : String(value)
}

function setValue(key: string, value: unknown): void {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function validate(): string | null {
  for (const field of fields.value) {
    if (!field.required) continue
    if (valueOf(field.key).trim() === '') return `请填写：${field.label}`
  }
  return null
}

defineExpose({ validate })
</script>

<template>
  <template v-for="field in fields" :key="field.key">
    <el-form-item :label="field.label" :required="field.required">
      <el-select
        v-if="field.type === 'select'"
        :model-value="valueOf(field.key)"
        placeholder="请选择"
        style="width: 100%"
        @update:model-value="setValue(field.key, $event)"
      >
        <el-option v-for="option in field.options" :key="String(option)" :label="String(option)" :value="option" />
      </el-select>
      <el-input
        v-else-if="field.type === 'textarea'"
        :model-value="valueOf(field.key)"
        type="textarea"
        :rows="3"
        @update:model-value="setValue(field.key, $event)"
      />
      <el-date-picker
        v-else-if="field.type === 'date'"
        :model-value="valueOf(field.key)"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        style="width: 100%"
        @update:model-value="setValue(field.key, $event)"
      />
      <el-input
        v-else-if="field.type === 'number'"
        :model-value="valueOf(field.key)"
        placeholder="请输入数字"
        @update:model-value="setValue(field.key, $event)"
      />
      <el-input
        v-else
        :model-value="valueOf(field.key)"
        placeholder="请输入"
        @update:model-value="setValue(field.key, $event)"
      />
    </el-form-item>
  </template>
</template>
