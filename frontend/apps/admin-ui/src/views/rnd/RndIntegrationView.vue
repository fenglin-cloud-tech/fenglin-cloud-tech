<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { rndApi, type RndTenantConfig } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const config = ref<RndTenantConfig | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')

const form = reactive({
  config_json_text: '{}',
  timezone: 'Asia/Shanghai',
  data_retention_months: 24,
  dora_target_level: 'HIGH',
  webhook_base_url: '',
})

const gapItems = [
  '集成配置为整租户单条记录（/api/rnd/v1/tenant-config），没有多环境或多实例配置。',
  'Webhook 密钥与访问令牌由后端加密存储，本页只维护基础地址、时区、保留期与 DORA 目标等级。',
  '权限点 tenant:devops:integration:manage 仅授予租户管理员，普通研发账号返回真实 403。',
]

function fillForm(data: RndTenantConfig) {
  form.config_json_text = JSON.stringify(data.config_json ?? {}, null, 2)
  form.timezone = data.timezone ?? 'Asia/Shanghai'
  form.data_retention_months = data.data_retention_months ?? 24
  form.dora_target_level = data.dora_target_level ?? 'HIGH'
  form.webhook_base_url = data.webhook_base_url ?? ''
}

async function loadConfig() {
  loading.value = true
  error.value = ''
  try {
    config.value = await rndApi.tenantConfig.get()
    if (config.value) fillForm(config.value)
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function submit() {
  let configJson: Record<string, unknown>
  try {
    configJson = JSON.parse(form.config_json_text || '{}') as Record<string, unknown>
  } catch {
    formError.value = 'config_json 不是合法 JSON'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    config.value = await rndApi.tenantConfig.update({
      config_json: configJson,
      timezone: form.timezone,
      data_retention_months: Number(form.data_retention_months),
      dora_target_level: form.dora_target_level,
      webhook_base_url: form.webhook_base_url.trim() || undefined,
    })
    ElMessage.success('集成配置已保存')
  } catch (err) {
    formError.value = describeError(err)
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="集成配置"
      description="租户级研发集成参数、时区、数据保留期与 DORA 目标；数据来自 /api/rnd/v1/tenant-config。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadConfig">重新加载</el-button>
        <el-button v-perm="'tenant:devops:integration:manage'" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />
    <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" style="margin-bottom: 12px" />

    <el-card shadow="never" v-loading="loading">
      <el-form label-width="140px" style="max-width: 720px">
        <el-form-item label="时区">
          <el-input v-model="form.timezone" maxlength="64" />
        </el-form-item>
        <el-form-item label="数据保留月数">
          <el-input-number v-model="form.data_retention_months" :min="1" :max="120" />
        </el-form-item>
        <el-form-item label="DORA 目标等级">
          <el-select v-model="form.dora_target_level" style="width: 220px">
            <el-option label="精英 ELITE" value="ELITE" />
            <el-option label="高效 HIGH" value="HIGH" />
            <el-option label="中等 MEDIUM" value="MEDIUM" />
            <el-option label="低 LOW" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="Webhook 基础地址">
          <el-input v-model="form.webhook_base_url" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="扩展配置 (JSON)">
          <el-input v-model="form.config_json_text" type="textarea" :rows="10" />
        </el-form-item>
        <el-form-item label="最近更新">
          <span class="fl-muted">{{ config?.updated_at ?? '-' }}</span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
