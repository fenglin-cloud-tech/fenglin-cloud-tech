<script setup lang="ts">
/**
 * 雪花 ID / 长整型 ID 输入框：始终以字符串承载，避免 el-input-number 的 number 精度截断。
 * 输入时剔除非数字字符；不做数值转换，原样提交给后端（Long 接受 JSON 字符串）。
 */
defineProps<{
  modelValue?: string | number | null
  placeholder?: string
  disabled?: boolean
  maxlength?: number | string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function onInput(value: string) {
  emit('update:modelValue', value.replace(/\D/g, ''))
}

function display(value: string | number | null | undefined) {
  return value === null || value === undefined ? '' : String(value)
}
</script>

<template>
  <el-input
    :model-value="display(modelValue)"
    :placeholder="placeholder"
    :disabled="disabled"
    :maxlength="maxlength ?? 19"
    inputmode="numeric"
    @update:model-value="onInput"
  />
</template>
