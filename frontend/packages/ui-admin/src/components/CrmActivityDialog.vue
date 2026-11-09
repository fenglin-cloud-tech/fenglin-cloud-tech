<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ApiError, crmApi, type ActivityCreateRequest, type CrmId } from '@fenglin/api-client'

/**
 * CRM 跟进记录弹窗，管理端与员工端共用；权限码 crm:activity:create 由后端校验。
 */
const props = defineProps<{
  modelValue: boolean
  bizType: 'LEAD' | 'CUSTOMER' | 'OPPORTUNITY'
  bizId: CrmId
  title?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()

const loading = ref(false)
const error = ref('')
const form = reactive({
  activity_type: 'CALL',
  subject: '',
  content: '',
  duration_min: undefined as number | undefined,
  result: '',
  next_plan: '',
  occurred_at: '',
})

function describeError(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 403) return `无权限执行该操作（${err.message}）`
    if (err.status === 404) return `接口不存在或服务未启动（${err.message}）`
    if (err.status === 401) return '登录已过期，请重新登录'
    return `${err.message}${err.code ? `（${err.code}）` : ''}`
  }
  return (err as Error)?.message ?? String(err)
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      error.value = ''
      Object.assign(form, {
        activity_type: 'CALL',
        subject: '',
        content: '',
        duration_min: undefined,
        result: '',
        next_plan: '',
        occurred_at: '',
      })
    }
  },
)

async function submit() {
  if (!form.subject && !form.content) {
    error.value = '请填写跟进主题或内容'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const body: ActivityCreateRequest = {
      biz_type: props.bizType,
      biz_id: props.bizId,
      activity_type: form.activity_type,
      subject: form.subject || undefined,
      content: form.content || undefined,
      duration_min: form.duration_min,
      result: form.result || undefined,
      next_plan: form.next_plan || undefined,
      occurred_at: form.occurred_at ? new Date(form.occurred_at).toISOString() : undefined,
    }
    await crmApi.activities.create(body)
    ElMessage.success('跟进已记录')
    emit('saved')
    emit('update:modelValue', false)
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title || '新增跟进'" width="560px" @update:model-value="emit('update:modelValue', $event)">
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <el-form label-width="90px">
      <el-form-item label="活动类型">
        <el-select v-model="form.activity_type" style="width: 100%">
          <el-option label="电话" value="CALL" />
          <el-option label="邮件" value="EMAIL" />
          <el-option label="拜访" value="VISIT" />
          <el-option label="微信" value="WECHAT" />
          <el-option label="会议" value="MEETING" />
        </el-select>
      </el-form-item>
      <el-form-item label="跟进时间">
        <el-date-picker v-model="form.occurred_at" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
      </el-form-item>
      <el-form-item label="主题">
        <el-input v-model="form.subject" maxlength="120" />
      </el-form-item>
      <el-form-item label="内容">
        <el-input v-model="form.content" type="textarea" :rows="3" maxlength="1000" />
      </el-form-item>
      <el-form-item label="时长(分)">
        <el-input-number v-model="form.duration_min" :min="1" :max="1440" />
      </el-form-item>
      <el-form-item label="结果">
        <el-input v-model="form.result" maxlength="200" />
      </el-form-item>
      <el-form-item label="下一步">
        <el-input v-model="form.next_plan" maxlength="200" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
