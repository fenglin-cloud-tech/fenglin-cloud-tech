<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { aiApi, type AiModeValidateResult, type AiMeteringResult, type AiModelView, type AiPromptView } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canModel = () => auth.can('platform:ai:model:manage')
const canPrompt = () => auth.can('tenant:ai:prompt:manage')
const canGrant = () => auth.can('tenant:ai:grant:manage')

type Tab = 'model' | 'prompt' | 'grant' | 'metering' | 'mode'
const activeTab = ref<Tab>('model')

/* ---------------- 模型配置 ---------------- */
const modelForm = reactive({
  code: '',
  provider_type: 'OPENAI_COMPATIBLE',
  model_type: 'CHAT',
  endpoint: '',
  api_key: '',
  routing_weight: 100,
})
const modelSaving = ref(false)
const modelError = ref('')
const createdModels = ref<AiModelView[]>([])

async function submitModel() {
  modelError.value = ''
  if (!modelForm.code || !modelForm.endpoint || !modelForm.api_key) {
    modelError.value = '编码、endpoint、api_key 为必填项'
    return
  }
  modelSaving.value = true
  try {
    const result = await aiApi.createModel({ ...modelForm })
    createdModels.value = [result, ...createdModels.value]
    modelForm.api_key = ''
    ElMessage.success(`模型 ${result.code} 已创建（密钥仅回显指纹）`)
  } catch (err) {
    modelError.value = describeError(err)
  } finally {
    modelSaving.value = false
  }
}

/* ---------------- 提示词 ---------------- */
const promptForm = reactive({
  code: '',
  name: '',
  scene: '',
  system_prompt: '',
  user_prompt: '',
  variablesText: '',
})
const promptSaving = ref(false)
const promptError = ref('')
const createdPrompts = ref<AiPromptView[]>([])

async function submitPrompt() {
  promptError.value = ''
  if (!promptForm.code || !promptForm.name || !promptForm.scene) {
    promptError.value = '编码、名称、场景为必填项'
    return
  }
  promptSaving.value = true
  try {
    const result = await aiApi.createPrompt({
      code: promptForm.code,
      name: promptForm.name,
      scene: promptForm.scene,
      system_prompt: promptForm.system_prompt || undefined,
      user_prompt: promptForm.user_prompt || undefined,
      variables: promptForm.variablesText
        ? promptForm.variablesText.split(',').map((item) => item.trim()).filter(Boolean)
        : undefined,
    })
    createdPrompts.value = [result, ...createdPrompts.value.filter((item) => item.code !== result.code)]
    ElMessage.success(`提示词 ${result.code} 当前版本 v${result.prompt_version}`)
  } catch (err) {
    promptError.value = describeError(err)
  } finally {
    promptSaving.value = false
  }
}

/* ---------------- 能力授权 ---------------- */
const grantForm = reactive({
  host_product: 'hub',
  capability: 'chat',
  enabled: true,
  quota_qps: 10,
  quota_token_month: 1000000,
  billing_mode: 'METERED',
})
const grantSaving = ref(false)
const grantError = ref('')
const grantResults = ref<Array<{ host_product: string; capability: string; enabled: boolean; quota_qps: number; quota_token_month: number; billing_mode: string }>>([])

async function submitGrant() {
  grantError.value = ''
  if (!grantForm.host_product || !grantForm.capability) {
    grantError.value = '宿主产品与能力为必填项'
    return
  }
  grantSaving.value = true
  try {
    await aiApi.putGrants({
      items: [
        {
          host_product: grantForm.host_product,
          capability: grantForm.capability,
          enabled: grantForm.enabled,
          quota_qps: grantForm.quota_qps,
          quota_token_month: grantForm.quota_token_month,
          billing_mode: grantForm.billing_mode,
        },
      ],
    })
    ElMessage.success('能力授权已保存（按 host_product + capability 幂等 upsert）')
    grantResults.value = [{ ...grantForm }]
  } catch (err) {
    grantError.value = describeError(err)
  } finally {
    grantSaving.value = false
  }
}

/* ---------------- 计量 ---------------- */
const meteringForm = reactive({ mode: '', host_product: '', from: '', to: '' })
const meteringLoading = ref(false)
const meteringError = ref('')
const metering = ref<AiMeteringResult | null>(null)

async function loadMetering() {
  meteringLoading.value = true
  meteringError.value = ''
  try {
    metering.value = await aiApi.metering({
      mode: meteringForm.mode || undefined,
      host_product: meteringForm.host_product || undefined,
      from: meteringForm.from || undefined,
      to: meteringForm.to || undefined,
    })
  } catch (err) {
    meteringError.value = describeError(err)
  } finally {
    meteringLoading.value = false
  }
}

/* ---------------- 双模式校验 ---------------- */
const modeLoading = ref(false)
const modeError = ref('')
const modeResult = ref<AiModeValidateResult | null>(null)

async function validateMode() {
  modeLoading.value = true
  modeError.value = ''
  try {
    modeResult.value = await aiApi.validateMode()
  } catch (err) {
    modeError.value = describeError(err)
  } finally {
    modeLoading.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader module="AI 中台 · A-26" title="AI 中台" description="模型配置、提示词、能力授权、计量与双模式一致性校验。">
      <template #actions>
        <el-button :loading="meteringLoading" @click="activeTab = 'metering'; loadMetering()">刷新计量</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '后端仅提供写入型接口（模型/提示词/授权），无列表、更新、删除接口；本页只展示本次会话内创建成功的记录，不回填历史数据。',
        'API Key 加密存储，接口仅回显 api_key_fingerprint，页面不保存明文。',
        '模型配置需 platform:ai:model:manage，提示词需 tenant:ai:prompt:manage，能力授权需 tenant:ai:grant:manage；当前：模型=' + (canModel() ? '具备' : '缺失') + '，提示词=' + (canPrompt() ? '具备' : '缺失') + '，授权=' + (canGrant() ? '具备' : '缺失') + '。',
        'HUB-API-1307 计量与 HUB-API-1308 双模式校验在接口文档中未定义细粒度权限码，按后端 ADMIN + 租户 RLS 收敛。',
      ]"
    />

    <div class="fl-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="模型配置" name="model">
          <el-alert v-if="modelError" type="error" :closable="false" show-icon :title="modelError" style="margin-bottom: 12px" />
          <el-form label-width="120px" style="max-width: 640px">
            <el-form-item label="模型编码" required><el-input v-model="modelForm.code" placeholder="如 gpt-4o-primary" /></el-form-item>
            <el-form-item label="供应商类型">
              <el-input v-model="modelForm.provider_type" placeholder="OPENAI_COMPATIBLE" />
            </el-form-item>
            <el-form-item label="模型类型">
              <el-select v-model="modelForm.model_type" style="width: 100%">
                <el-option label="CHAT" value="CHAT" />
                <el-option label="EMBEDDING" value="EMBEDDING" />
                <el-option label="RERANK" value="RERANK" />
              </el-select>
            </el-form-item>
            <el-form-item label="Endpoint" required><el-input v-model="modelForm.endpoint" placeholder="https://..." /></el-form-item>
            <el-form-item label="API Key" required><el-input v-model="modelForm.api_key" type="password" show-password /></el-form-item>
            <el-form-item label="路由权重"><el-input-number v-model="modelForm.routing_weight" :min="0" :max="1000" /></el-form-item>
            <el-form-item>
              <el-button v-perm="'platform:ai:model:manage'" type="primary" :loading="modelSaving" @click="submitModel">创建模型</el-button>
              <span v-if="!canModel()" class="fl-muted" style="margin-left: 8px">缺少 platform:ai:model:manage</span>
            </el-form-item>
          </el-form>
          <h4>本次会话已创建</h4>
          <el-table :data="createdModels" border size="small">
            <el-table-column prop="code" label="编码" min-width="150" />
            <el-table-column prop="provider_type" label="供应商" width="160" />
            <el-table-column prop="model_type" label="类型" width="110" />
            <el-table-column prop="endpoint" label="Endpoint" min-width="200" />
            <el-table-column prop="api_key_fingerprint" label="密钥指纹" width="180" />
            <el-table-column prop="routing_weight" label="权重" width="90" />
          </el-table>
          <el-empty v-if="!createdModels.length" description="尚无本次会话创建记录（后端无列表接口）" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="提示词" name="prompt">
          <el-alert v-if="promptError" type="error" :closable="false" show-icon :title="promptError" style="margin-bottom: 12px" />
          <el-form label-width="120px" style="max-width: 720px">
            <el-form-item label="提示词编码" required><el-input v-model="promptForm.code" placeholder="如 summary-v1" /></el-form-item>
            <el-form-item label="名称" required><el-input v-model="promptForm.name" /></el-form-item>
            <el-form-item label="场景" required><el-input v-model="promptForm.scene" placeholder="如 doc-summary" /></el-form-item>
            <el-form-item label="System Prompt"><el-input v-model="promptForm.system_prompt" type="textarea" :rows="4" class="fl-mono" /></el-form-item>
            <el-form-item label="User Prompt"><el-input v-model="promptForm.user_prompt" type="textarea" :rows="4" class="fl-mono" /></el-form-item>
            <el-form-item label="变量">
              <el-input v-model="promptForm.variablesText" placeholder="以英文逗号分隔，如 title,content" />
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'tenant:ai:prompt:manage'" type="primary" :loading="promptSaving" @click="submitPrompt">保存提示词</el-button>
              <span v-if="!canPrompt()" class="fl-muted" style="margin-left: 8px">缺少 tenant:ai:prompt:manage</span>
            </el-form-item>
          </el-form>
          <h4>本次会话已保存</h4>
          <el-table :data="createdPrompts" border size="small">
            <el-table-column prop="code" label="编码" min-width="140" />
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column prop="scene" label="场景" width="140" />
            <el-table-column prop="prompt_version" label="版本" width="80" />
            <el-table-column prop="status" label="状态" width="110" />
          </el-table>
          <el-empty v-if="!createdPrompts.length" description="尚无本次会话保存记录（后端无查询接口）" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="能力授权" name="grant">
          <el-alert v-if="grantError" type="error" :closable="false" show-icon :title="grantError" style="margin-bottom: 12px" />
          <el-form label-width="140px" style="max-width: 640px">
            <el-form-item label="宿主产品" required><el-input v-model="grantForm.host_product" placeholder="hub / portal / developer" /></el-form-item>
            <el-form-item label="能力" required>
              <el-select v-model="grantForm.capability" filterable allow-create default-first-option style="width: 100%">
                <el-option label="chat" value="chat" />
                <el-option label="rag" value="rag" />
                <el-option label="search" value="search" />
                <el-option label="guardrail" value="guardrail" />
                <el-option label="embedding" value="embedding" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用"><el-switch v-model="grantForm.enabled" /></el-form-item>
            <el-form-item label="QPS 配额"><el-input-number v-model="grantForm.quota_qps" :min="0" /></el-form-item>
            <el-form-item label="月 Token 配额"><el-input-number v-model="grantForm.quota_token_month" :min="0" :step="10000" /></el-form-item>
            <el-form-item label="计费模式">
              <el-select v-model="grantForm.billing_mode" style="width: 100%">
                <el-option label="METERED" value="METERED" />
                <el-option label="PACKAGE" value="PACKAGE" />
                <el-option label="FREE" value="FREE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'tenant:ai:grant:manage'" type="primary" :loading="grantSaving" @click="submitGrant">保存授权</el-button>
              <span v-if="!canGrant()" class="fl-muted" style="margin-left: 8px">缺少 tenant:ai:grant:manage</span>
            </el-form-item>
          </el-form>
          <h4>本次会话已提交</h4>
          <el-table :data="grantResults" border size="small">
            <el-table-column prop="host_product" label="宿主产品" width="140" />
            <el-table-column prop="capability" label="能力" width="120" />
            <el-table-column prop="enabled" label="启用" width="90" />
            <el-table-column prop="quota_qps" label="QPS" width="90" />
            <el-table-column prop="quota_token_month" label="月 Token" width="140" />
            <el-table-column prop="billing_mode" label="计费" width="110" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="计量" name="metering">
          <div class="fl-toolbar">
            <el-input v-model="meteringForm.mode" placeholder="模式 mode" clearable style="width: 160px" />
            <el-input v-model="meteringForm.host_product" placeholder="宿主产品" clearable style="width: 160px" />
            <el-date-picker v-model="meteringForm.from" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 160px" />
            <el-date-picker v-model="meteringForm.to" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 160px" />
            <el-button type="primary" :loading="meteringLoading" @click="loadMetering">查询</el-button>
          </div>
          <el-alert v-if="meteringError" type="error" :closable="false" show-icon :title="meteringError" style="margin-bottom: 12px" />
          <el-table v-loading="meteringLoading" :data="metering?.records ?? []" border stripe>
            <el-table-column prop="stat_date" label="统计日期" width="130" />
            <el-table-column prop="host_product" label="宿主产品" width="140" />
            <el-table-column prop="capability" label="能力" width="120" />
            <el-table-column prop="mode" label="模式" width="110" />
            <el-table-column prop="call_count" label="调用次数" width="110" />
            <el-table-column prop="token_in" label="输入 Token" width="120" />
            <el-table-column prop="token_out" label="输出 Token" width="120" />
            <el-table-column prop="cost" label="成本" width="110" />
          </el-table>
          <el-empty v-if="!metering?.records?.length" description="暂无计量数据（未查询或该租户无记录）" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="双模式校验" name="mode">
          <div class="fl-toolbar">
            <el-button type="primary" :loading="modeLoading" @click="validateMode">执行一致性校验</el-button>
          </div>
          <el-alert v-if="modeError" type="error" :closable="false" show-icon :title="modeError" style="margin-bottom: 12px" />
          <el-descriptions v-if="modeResult" :column="2" border>
            <el-descriptions-item label="当前模式">{{ modeResult.mode }}</el-descriptions-item>
            <el-descriptions-item label="是否一致">
              <el-tag :type="modeResult.consistent ? 'success' : 'danger'">{{ modeResult.consistent ? '一致' : '不一致' }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-for="issue in modeResult?.issues ?? []"
            :key="issue"
            type="warning"
            :closable="false"
            show-icon
            :title="issue"
            style="margin-top: 8px"
          />
          <el-empty v-if="!modeResult" description="尚未执行校验" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
h4 {
  margin: 20px 0 8px;
  font-size: 13px;
}
</style>
