<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()

type ConfigKey = 'ticket-types' | 'groups' | 'duty' | 'dispatch-rule' | 'asset-reminder' | 'retry-policy' | 'service-report'

const tabs: Array<{ key: ConfigKey; label: string; perm: string; hint: string }> = [
  { key: 'ticket-types', label: '工单类型', perm: 'tenant:service:desk:config', hint: '配置工单类型、默认优先级与是否需要审批' },
  { key: 'groups', label: '处理组', perm: 'tenant:service:desk:config', hint: '配置处理组、成员与技能标签' },
  { key: 'duty', label: '值班表', perm: 'tenant:service:desk:config', hint: '配置值班轮次与升级顺序' },
  { key: 'dispatch-rule', label: '自动分派', perm: 'tenant:service:desk:config', hint: '配置按类型/优先级/负载的自动分派规则' },
  { key: 'asset-reminder', label: '资产提醒', perm: 'tenant:service:asset:config', hint: '配置资产借用到期提醒周期与渠道' },
  { key: 'retry-policy', label: 'RPA 重试', perm: 'tenant:service:rpa:config', hint: '配置机器人任务默认重试次数与退避策略' },
  { key: 'service-report', label: '服务报表', perm: 'tenant:service:report:config', hint: '配置报表周期、口径与导出方式' },
]

const active = ref<ConfigKey>('ticket-types')
const rows = ref<ServiceRecord[]>([])
const text = ref('[]')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')

const gapItems = [
  '已做字段级强类型校验（保守口径）：只校验 7 类配置能从页面语义确定的字段，未知键原样保留、未知结构放行，存量配置零误拒。',
  '保存前可先点“存量校验”跑只读 dry-run；拒绝条数必须为 0 再继续改配置。',
  'GET /admin/config/{type} 仅要求 tenant:service:config:view，PUT/DELETE 分别要求 desk/asset/rpa/report 配置权限。',
  '支持删除单个配置键（默认 default），删除后该类型回落到内置默认值。',
]

async function dryRun() {
  try {
    const result = await serviceApi.admin.configDryRun()
    const items = (result.items ?? []) as ServiceRecord[]
    const rejected = items.filter((item) => Array.isArray(item.errors) && item.errors.length)
    await ElMessageBox.alert(
      `配置类型 ${result.config_types} 类，记录 ${result.records} 条，拒绝 ${result.rejected} 条` +
        (rejected.length ? `；${rejected.map((item) => `${item.config_type}/${item.config_key}: ${(item.errors as string[]).join('、')}`).join('；')}` : ''),
      '存量配置 dry-run',
      { type: result.rejected ? 'warning' : 'success' },
    )
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function currentTab() {
  return tabs.find((item) => item.key === active.value)!
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await serviceApi.admin.config(active.value)
    text.value = JSON.stringify(rows.value.length ? rows.value[0].config_json ?? rows.value[0] : [], null, 2)
  } catch (err) {
    rows.value = []
    text.value = '[]'
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function save() {
  let payload: unknown
  try {
    payload = JSON.parse(text.value || '[]')
  } catch {
    formError.value = '配置不是合法 JSON'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    await serviceApi.admin.saveConfig(active.value, payload as ServiceRecord)
    ElMessage.success('配置已保存')
    await load()
  } catch (err) {
    formError.value = describeError(err)
  } finally {
    saving.value = false
  }
}

function switchTab(key: ConfigKey) {
  active.value = key
  load()
}

async function removeConfig() {
  const kind = active.value
  const key = rows.value.length && rows.value[0].config_key ? String(rows.value[0].config_key) : 'default'
  try {
    await ElMessageBox.confirm(`确认删除配置「${currentTab().label} / ${key}」？删除后回落到内置默认值。`, '删除配置', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await serviceApi.admin.deleteConfig(kind, key)
    ElMessage.success('配置已删除')
    await load()
  } catch (err) {
    formError.value = describeError(err)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="工单配置"
      description="工单类型、处理组、值班、分派、资产提醒、RPA 重试与服务报表配置；数据来自 /api/service/v1/admin/config。"
    >
      <template #actions>
        <el-button @click="dryRun">存量校验</el-button>
        <el-button :loading="loading" @click="load">重新加载</el-button>
        <el-button :disabled="!auth.can(currentTab().perm) || !rows.length" plain type="danger" @click="removeConfig">删除</el-button>
        <el-button :disabled="!auth.can(currentTab().perm)" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs :model-value="active" @tab-change="(name: string) => switchTab(name as ConfigKey)">
        <el-tab-pane v-for="tab in tabs" :key="tab.key" :label="tab.label" :name="tab.key">
          <el-alert :title="tab.hint" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
          <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" style="margin-bottom: 12px" />
          <el-input v-model="text" type="textarea" :rows="18" spellcheck="false" />
          <div class="fl-muted" style="margin-top: 8px">当前读取到 {{ rows.length }} 条配置记录；提交将覆盖 default 键。</div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
