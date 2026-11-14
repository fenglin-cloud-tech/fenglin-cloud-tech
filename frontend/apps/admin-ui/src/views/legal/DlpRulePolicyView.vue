<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { dataLevel, fmtTime, label } from './labels'

const tab = ref('rules')

const rules = ref<LegalRecord[]>([])
const ruleTotal = ref(0)
const ruleLoading = ref(false)
const ruleError = ref('')
const ruleFilter = reactive({ data_level: '', enabled: '' })

const policies = ref<LegalRecord[]>([])
const policyTotal = ref(0)
const policyLoading = ref(false)
const policyError = ref('')
const policyFilter = reactive({ data_level: '', action: '', enabled: '' })

const channels = ref<LegalRecord[]>([])
const channelLoading = ref(false)
const channelError = ref('')
const channelPolicyId = ref('')

const whitelist = ref<LegalRecord[]>([])
const whitelistTotal = ref(0)
const whitelistLoading = ref(false)
const whitelistError = ref('')
const whitelistFilter = reactive({ channel_code: '', status: '' })

const ruleVisible = ref(false)
const ruleSaving = ref(false)
const ruleForm = reactive({
  name: '',
  rule_type: 'REGEX',
  expression: '',
  data_level: 'L3',
  category: 'PII',
  threshold: 1,
  priority: 100,
  remark: '',
})

const policyVisible = ref(false)
const policySaving = ref(false)
const policyForm = reactive({
  name: '',
  data_level: 'L3',
  action: 'BLOCK',
  priority: 100,
  channel_codes: 'EMAIL',
  rule_ids: '',
  effective_from: '',
  effective_to: '',
})

const whitelistVisible = ref(false)
const whitelistSaving = ref(false)
const whitelistForm = reactive({
  name: '',
  channel_code: '',
  match_type: 'EMAIL_DOMAIN',
  match_value: '',
  data_level: 'L3',
  reason: '',
  effective_from: '',
  effective_to: '',
})

const gapItems = [
  '规则/策略/白名单权限分别是 tenant:legal:dlp:rule:view|config、tenant:legal:dlp:policy:view|config、tenant:legal:dlp:whitelist:config，均为 hub_permission 真实值。',
  '通道接口（GET/PUT /admin/dlp/channels）实际校验 tenant:legal:dlp:channel:config；无该权限时本页通道页签会显示 403 原文。',
  '规则测试调用后端 /admin/dlp/rules/{id}/test，请求体为 { rule_id, sample_text }，不在前端本地跑正则。',
]

function enabledParam(value: string): boolean | undefined {
  if (value === 'true') return true
  if (value === 'false') return false
  return undefined
}

function csvIds(text: string): string[] | undefined {
  const values = text
    .split(',')
    .map((item) => item.trim())
    .filter((item) => /^\d+$/.test(item) && item !== '0')
  return values.length ? values : undefined
}

function csvStrings(text: string): string[] | undefined {
  const values = text.split(',').map((item) => item.trim()).filter(Boolean)
  return values.length ? values : undefined
}

async function loadRules() {
  ruleLoading.value = true
  ruleError.value = ''
  try {
    const result = await legalApi.dlp.rules({
      data_level: ruleFilter.data_level || undefined,
      enabled: enabledParam(ruleFilter.enabled),
      page: 1,
      size: 50,
    })
    rules.value = result.records
    ruleTotal.value = result.total
  } catch (err) {
    rules.value = []
    ruleError.value = describeError(err)
  } finally {
    ruleLoading.value = false
  }
}

async function loadPolicies() {
  policyLoading.value = true
  policyError.value = ''
  try {
    const result = await legalApi.dlp.policies({
      data_level: policyFilter.data_level || undefined,
      action: policyFilter.action || undefined,
      enabled: enabledParam(policyFilter.enabled),
      page: 1,
      size: 50,
    })
    policies.value = result.records
    policyTotal.value = result.total
  } catch (err) {
    policies.value = []
    policyError.value = describeError(err)
  } finally {
    policyLoading.value = false
  }
}

async function loadChannels() {
  channelLoading.value = true
  channelError.value = ''
  try {
    channels.value = await legalApi.dlp.channels({ policy_id: channelPolicyId.value ? channelPolicyId.value : undefined })
  } catch (err) {
    channels.value = []
    channelError.value = describeError(err)
  } finally {
    channelLoading.value = false
  }
}

async function loadWhitelist() {
  whitelistLoading.value = true
  whitelistError.value = ''
  try {
    const result = await legalApi.dlp.whitelist({
      channel_code: whitelistFilter.channel_code || undefined,
      status: whitelistFilter.status || undefined,
      page: 1,
      size: 50,
    })
    whitelist.value = result.records
    whitelistTotal.value = result.total
  } catch (err) {
    whitelist.value = []
    whitelistError.value = describeError(err)
  } finally {
    whitelistLoading.value = false
  }
}

async function createRule() {
  if (!ruleForm.name.trim() || !ruleForm.expression.trim()) {
    ElMessage.warning('规则名称与表达式必填')
    return
  }
  ruleSaving.value = true
  try {
    await legalApi.dlp.createRule({
      name: ruleForm.name.trim(),
      rule_type: ruleForm.rule_type,
      expression: ruleForm.expression.trim(),
      data_level: ruleForm.data_level,
      category: ruleForm.category,
      threshold: ruleForm.threshold,
      priority: ruleForm.priority,
      remark: ruleForm.remark || undefined,
    })
    ElMessage.success('规则已创建')
    ruleVisible.value = false
    ruleForm.name = ''
    ruleForm.expression = ''
    ruleForm.remark = ''
    await loadRules()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    ruleSaving.value = false
  }
}

async function testRule(row: LegalRecord) {
  try {
    const result = await legalApi.dlp.testRule(row.id, {
      rule_id: row.id,
      sample_text: '测试样本：客户手机号 13800000000，邮箱 test@example.com',
    })
    const matched = result.matched ?? result.hit ?? result.hits ?? '-'
    ElMessage.success(`测试结果：${matched}`)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function createPolicy() {
  if (!policyForm.name.trim()) {
    ElMessage.warning('策略名称必填')
    return
  }
  policySaving.value = true
  try {
    await legalApi.dlp.createPolicy({
      name: policyForm.name.trim(),
      data_level: policyForm.data_level,
      action: policyForm.action,
      priority: policyForm.priority,
      channel_codes: csvStrings(policyForm.channel_codes),
      rule_ids: csvIds(policyForm.rule_ids),
      effective_from: policyForm.effective_from || undefined,
      effective_to: policyForm.effective_to || undefined,
    })
    ElMessage.success('策略已创建')
    policyVisible.value = false
    policyForm.name = ''
    await loadPolicies()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    policySaving.value = false
  }
}

async function saveChannels() {
  if (!channelPolicyId.value) {
    ElMessage.warning('请填写策略ID')
    return
  }
  try {
    await legalApi.dlp.saveChannels({ policy_id: channelPolicyId.value, channels: channels.value })
    ElMessage.success('通道配置已保存')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function createWhitelist() {
  if (!whitelistForm.name.trim() || !whitelistForm.channel_code.trim() || !whitelistForm.match_value.trim()) {
    ElMessage.warning('名称、通道编码与匹配值必填')
    return
  }
  if (!whitelistForm.effective_from || !whitelistForm.effective_to) {
    ElMessage.warning('白名单必须填写生效与失效时间')
    return
  }
  whitelistSaving.value = true
  try {
    await legalApi.dlp.createWhitelist({
      name: whitelistForm.name.trim(),
      channel_code: whitelistForm.channel_code.trim(),
      match_type: whitelistForm.match_type,
      match_value: whitelistForm.match_value.trim(),
      data_level: whitelistForm.data_level,
      reason: whitelistForm.reason || undefined,
      effective_from: whitelistForm.effective_from,
      effective_to: whitelistForm.effective_to,
    })
    ElMessage.success('白名单已创建')
    whitelistVisible.value = false
    whitelistForm.name = ''
    whitelistForm.match_value = ''
    whitelistForm.reason = ''
    await loadWhitelist()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    whitelistSaving.value = false
  }
}

function switchTab(value: string) {
  tab.value = value
  if (value === 'policies' && policies.value.length === 0) loadPolicies()
  if (value === 'channels' && channels.value.length === 0) loadChannels()
  if (value === 'whitelist' && whitelist.value.length === 0) loadWhitelist()
}

onMounted(loadRules)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="DLP 规则与策略"
      description="敏感识别规则、处置策略、通道管控与白名单；数据来自 /api/legal/v1/admin/dlp/*。"
    >
      <template #actions>
        <el-button v-perm="'tenant:legal:dlp:rule:config'" type="primary" @click="ruleVisible = true">新建规则</el-button>
        <el-button v-perm="'tenant:legal:dlp:policy:config'" type="primary" plain @click="policyVisible = true">新建策略</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="tab" @tab-change="switchTab">
      <el-tab-pane label="识别规则" name="rules">
        <el-alert v-if="ruleError" type="error" :closable="false" show-icon :title="ruleError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-select v-model="ruleFilter.data_level" placeholder="全部级别" clearable style="width: 130px">
              <el-option v-for="(text, code) in dataLevel" :key="code" :label="text" :value="code" />
            </el-select>
            <el-select v-model="ruleFilter.enabled" placeholder="启停" clearable style="width: 110px">
              <el-option label="启用" value="true" />
              <el-option label="停用" value="false" />
            </el-select>
            <el-button :loading="ruleLoading" @click="loadRules">查询</el-button>
          </div>
          <el-table :data="rules" v-loading="ruleLoading" border stripe max-height="500">
            <el-table-column prop="rule_no" label="规则编号" width="160" />
            <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="rule_type" label="类型" width="110" />
            <el-table-column prop="expression" label="表达式" min-width="150" show-overflow-tooltip />
            <el-table-column label="级别" width="100">
              <template #default="{ row }">{{ label(dataLevel, row.data_level) }}</template>
            </el-table-column>
            <el-table-column prop="category" label="分类" width="110" />
            <el-table-column prop="threshold" label="阈值" width="80" />
            <el-table-column prop="priority" label="优先级" width="90" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-perm="'tenant:legal:dlp:rule:test'" link type="primary" @click="testRule(row)">测试</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="total">共 {{ ruleTotal }} 条</div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="处置策略" name="policies">
        <el-alert v-if="policyError" type="error" :closable="false" show-icon :title="policyError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-select v-model="policyFilter.data_level" placeholder="全部级别" clearable style="width: 130px">
              <el-option v-for="(text, code) in dataLevel" :key="code" :label="text" :value="code" />
            </el-select>
            <el-select v-model="policyFilter.action" placeholder="全部动作" clearable style="width: 120px">
              <el-option label="阻断" value="BLOCK" />
              <el-option label="告警" value="ALERT" />
              <el-option label="审计" value="AUDIT" />
              <el-option label="放行审批" value="APPROVAL" />
            </el-select>
            <el-button :loading="policyLoading" @click="loadPolicies">查询</el-button>
          </div>
          <el-table :data="policies" v-loading="policyLoading" border stripe max-height="500">
            <el-table-column prop="policy_no" label="策略编号" width="160" />
            <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
            <el-table-column label="级别" width="100">
              <template #default="{ row }">{{ label(dataLevel, row.data_level) }}</template>
            </el-table-column>
            <el-table-column prop="action" label="动作" width="110" />
            <el-table-column prop="channel_codes" label="通道" min-width="140" show-overflow-tooltip />
            <el-table-column prop="priority" label="优先级" width="90" />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="生效时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.effective_from) }}</template>
            </el-table-column>
          </el-table>
          <div class="total">共 {{ policyTotal }} 条</div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="通道管控" name="channels">
        <el-alert v-if="channelError" type="error" :closable="false" show-icon :title="channelError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="channelPolicyId" placeholder="策略ID" clearable style="width: 160px" />
            <el-button :loading="channelLoading" @click="loadChannels">查询</el-button>
            <el-button v-perm="'tenant:legal:dlp:channel:config'" type="primary" @click="saveChannels">保存</el-button>
          </div>
          <el-table :data="channels" v-loading="channelLoading" border stripe max-height="500">
            <el-table-column prop="channel_code" label="通道编码" width="150" />
            <el-table-column prop="default_action" label="默认动作" width="120" />
            <el-table-column prop="params" label="参数" min-width="200" show-overflow-tooltip />
            <el-table-column label="启用" width="80">
              <template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!channelLoading && channels.length === 0" description="该策略暂无通道配置" :image-size="60" />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="白名单" name="whitelist">
        <el-alert v-if="whitelistError" type="error" :closable="false" show-icon :title="whitelistError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="whitelistFilter.channel_code" placeholder="通道编码" clearable style="width: 160px" />
            <el-input v-model="whitelistFilter.status" placeholder="状态" clearable style="width: 120px" />
            <el-button :loading="whitelistLoading" @click="loadWhitelist">查询</el-button>
            <el-button v-perm="'tenant:legal:dlp:whitelist:config'" type="primary" @click="whitelistVisible = true">新增白名单</el-button>
          </div>
          <el-table :data="whitelist" v-loading="whitelistLoading" border stripe max-height="500">
            <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="channel_code" label="通道编码" width="140" />
            <el-table-column prop="match_type" label="匹配方式" width="130" />
            <el-table-column prop="match_value" label="匹配值" min-width="160" show-overflow-tooltip />
            <el-table-column label="级别" width="100">
              <template #default="{ row }">{{ label(dataLevel, row.data_level) }}</template>
            </el-table-column>
            <el-table-column label="生效时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.effective_from) }}</template>
            </el-table-column>
            <el-table-column label="失效时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.effective_to) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" />
          </el-table>
          <div class="total">共 {{ whitelistTotal }} 条</div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="ruleVisible" title="新建识别规则" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="ruleForm.name" maxlength="120" /></el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="ruleForm.rule_type" style="width: 100%">
            <el-option label="正则" value="REGEX" />
            <el-option label="关键字" value="KEYWORD" />
            <el-option label="字典" value="DICTIONARY" />
          </el-select>
        </el-form-item>
        <el-form-item label="表达式" required><el-input v-model="ruleForm.expression" placeholder="后端按 rule_type 解析" /></el-form-item>
        <el-form-item label="数据级别">
          <el-select v-model="ruleForm.data_level" style="width: 100%">
            <el-option v-for="(text, code) in dataLevel" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类"><el-input v-model="ruleForm.category" /></el-form-item>
        <el-form-item label="阈值"><el-input-number v-model="ruleForm.threshold" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority" :min="1" :max="9999" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="ruleForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleVisible = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="createRule">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="policyVisible" title="新建处置策略" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="policyForm.name" maxlength="120" /></el-form-item>
        <el-form-item label="数据级别">
          <el-select v-model="policyForm.data_level" style="width: 100%">
            <el-option v-for="(text, code) in dataLevel" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="policyForm.action" style="width: 100%">
            <el-option label="阻断" value="BLOCK" />
            <el-option label="告警" value="ALERT" />
            <el-option label="审计" value="AUDIT" />
            <el-option label="放行审批" value="APPROVAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="通道编码"><el-input v-model="policyForm.channel_codes" placeholder="逗号分隔，如 EMAIL,USB" /></el-form-item>
        <el-form-item label="规则ID"><el-input v-model="policyForm.rule_ids" placeholder="逗号分隔，可留空" /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="policyForm.priority" :min="1" :max="9999" style="width: 100%" /></el-form-item>
        <el-form-item label="生效时间"><el-date-picker v-model="policyForm.effective_from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" style="width: 100%" /></el-form-item>
        <el-form-item label="失效时间"><el-date-picker v-model="policyForm.effective_to" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyVisible = false">取消</el-button>
        <el-button type="primary" :loading="policySaving" @click="createPolicy">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="whitelistVisible" title="新增白名单" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="whitelistForm.name" /></el-form-item>
        <el-form-item label="通道编码" required><el-input v-model="whitelistForm.channel_code" /></el-form-item>
        <el-form-item label="匹配方式">
          <el-select v-model="whitelistForm.match_type" style="width: 100%">
            <el-option label="邮箱域名" value="EMAIL_DOMAIN" />
            <el-option label="用户" value="USER" />
            <el-option label="组织" value="ORG" />
            <el-option label="路径" value="PATH" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配值" required><el-input v-model="whitelistForm.match_value" /></el-form-item>
        <el-form-item label="数据级别">
          <el-select v-model="whitelistForm.data_level" style="width: 100%">
            <el-option v-for="(text, code) in dataLevel" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因"><el-input v-model="whitelistForm.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="生效时间" required>
          <el-date-picker v-model="whitelistForm.effective_from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效时间" required>
          <el-date-picker v-model="whitelistForm.effective_to" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="whitelistVisible = false">取消</el-button>
        <el-button type="primary" :loading="whitelistSaving" @click="createWhitelist">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.total {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: right;
}
</style>
