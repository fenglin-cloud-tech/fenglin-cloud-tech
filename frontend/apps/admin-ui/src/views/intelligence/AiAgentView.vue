<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canGuardrail = computed(() => auth.can('tenant:ai:guardrail:config'))
const activeTab = ref('agents')

const rows = ref<AiRecord[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({
  code: '',
  name: '',
  description: '',
  nodesText: '[]',
  toolsText: '[]',
  model_code: '',
  status: 'DRAFT',
  max_steps: 20,
})

const runVisible = ref(false)
const runLoading = ref(false)
const runError = ref('')
const runCode = ref('')
const runInput = ref('')
const runResult = ref<AiRecord | null>(null)

const ackLoading = ref(false)
const ackError = ref('')
const ackResult = ref<AiRecord | null>(null)

const gapItems = [
  '护栏配置由平台 AI 中台托管，本域没有读取接口；确认接口只回显托管方，不持久化策略。',
  '智能体没有独立的更新/发布接口，create 传入相同 code 且带 status 即生成新版本。',
  '智能体运行只对 PUBLISHED 版本有效，未发布时后端会返回真实错误。',
]

async function loadAgents() {
  loading.value = true
  error.value = ''
  try {
    const result = await intelligenceApi.agents.list({ page: page.value, size: size.value })
    rows.value = result.records ?? []
    total.value = result.total ?? 0
  } catch (err) {
    rows.value = []
    total.value = 0
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createError.value = ''
  form.code = ''
  form.name = ''
  form.description = ''
  form.nodesText = '[]'
  form.toolsText = '[]'
  form.model_code = ''
  form.status = 'DRAFT'
  form.max_steps = 20
  createVisible.value = true
}

function parseArray(text: string, label: string): unknown[] {
  const parsed = JSON.parse(text || '[]') as unknown
  if (!Array.isArray(parsed)) throw new Error(`${label} 必须是 JSON 数组`)
  return parsed
}

async function submitCreate() {
  createError.value = ''
  if (!form.code.trim() || !form.name.trim()) {
    createError.value = '编码与名称为必填项'
    return
  }
  let nodes: unknown[]
  let tools: unknown[]
  try {
    nodes = parseArray(form.nodesText, 'nodes')
    tools = parseArray(form.toolsText, 'tools')
  } catch (err) {
    createError.value = `JSON 解析失败：${(err as Error).message}`
    return
  }
  createSaving.value = true
  try {
    const created = await intelligenceApi.agents.create({
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      nodes,
      tools,
      model_code: form.model_code.trim() || undefined,
      status: form.status,
      max_steps: form.max_steps,
    })
    ElMessage.success(`智能体已创建：${created?.code ?? ''} v${created?.version_no ?? ''}`)
    createVisible.value = false
    page.value = 1
    await loadAgents()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

function openRun(row: AiRecord) {
  runCode.value = String(row.code ?? '')
  runInput.value = ''
  runResult.value = null
  runError.value = ''
  runVisible.value = true
}

async function submitRun() {
  runError.value = ''
  if (!runCode.value) {
    runError.value = '缺少智能体编码'
    return
  }
  if (!runInput.value.trim()) {
    runError.value = '请输入运行输入'
    return
  }
  runLoading.value = true
  try {
    runResult.value = await intelligenceApi.agents.run(runCode.value, { input: runInput.value.trim() })
    ElMessage.success('运行完成')
  } catch (err) {
    runResult.value = null
    runError.value = describeError(err)
  } finally {
    runLoading.value = false
  }
}

async function ackGuardrail() {
  ackLoading.value = true
  ackError.value = ''
  ackResult.value = null
  try {
    ackResult.value = await intelligenceApi.guardrails.ack({})
    ElMessage.success('已确认护栏由平台 AI 中台托管')
  } catch (err) {
    ackError.value = describeError(err)
  } finally {
    ackLoading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  loadAgents()
}

onMounted(loadAgents)
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="智能体与护栏"
      description="智能体版本、运行与护栏托管确认；数据来自 /api/intelligence/v1/agents 与 /admin/guardrails。"
    >
      <template #actions>
        <el-button v-perm="'tenant:ai:agent:manage'" type="primary" @click="openCreate">新建智能体</el-button>
        <el-button :loading="loading" @click="loadAgents">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="智能体" name="agents">
          <el-table v-loading="loading" :data="rows" border stripe max-height="520">
            <el-table-column prop="id" label="ID" width="170" />
            <el-table-column prop="code" label="编码" min-width="150" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="model_code" label="模型" width="150" />
            <el-table-column prop="version_no" label="版本" width="80" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{ row.status ?? '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="max_steps" label="最大步数" width="100" />
            <el-table-column prop="created_at" label="创建时间" width="170" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'ai:agent:run'" link type="primary" @click="openRun(row)">运行</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无智能体" :image-size="60" />
            </template>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="changePage"
          />
        </el-tab-pane>

        <el-tab-pane v-if="canGuardrail" label="护栏确认" name="guardrail">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="本域只提交确认，不持久化护栏策略；实际生效与存储由平台 AI 中台负责。"
            style="margin-bottom: 12px"
          />
          <el-alert v-if="ackError" type="error" :closable="false" show-icon :title="ackError" style="margin-bottom: 12px" />
          <el-button v-perm="'tenant:ai:guardrail:config'" type="primary" :loading="ackLoading" @click="ackGuardrail">
            确认护栏托管
          </el-button>

          <el-descriptions v-if="ackResult" :column="2" border size="small" style="margin-top: 16px">
            <el-descriptions-item label="托管方">{{ ackResult.managed_by ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="归属单元">{{ ackResult.owner_unit ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="已接受">{{ ackResult.accepted ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="说明">{{ ackResult.note ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="尚未提交确认" :image-size="60" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="createVisible" title="新建智能体" width="640px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="form.code" maxlength="64" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="节点 JSON">
          <el-input v-model="form.nodesText" type="textarea" :rows="4" spellcheck="false" class="fl-mono" />
        </el-form-item>
        <el-form-item label="工具 JSON">
          <el-input v-model="form.toolsText" type="textarea" :rows="3" spellcheck="false" class="fl-mono" />
        </el-form-item>
        <el-form-item label="模型编码"><el-input v-model="form.model_code" placeholder="选填" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="草稿（DRAFT）" value="DRAFT" />
            <el-option label="已发布（PUBLISHED）" value="PUBLISHED" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大步数"><el-input-number v-model="form.max_steps" :min="1" :max="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="runVisible" :title="`运行智能体：${runCode}`" width="600px">
      <el-alert v-if="runError" type="error" :closable="false" show-icon :title="runError" style="margin-bottom: 12px" />
      <el-input v-model="runInput" type="textarea" :rows="4" placeholder="输入内容" />
      <pre v-if="runResult" class="fl-mono run-json">{{ JSON.stringify(runResult, null, 2) }}</pre>
      <template #footer>
        <el-button @click="runVisible = false">关闭</el-button>
        <el-button type="primary" :loading="runLoading" @click="submitRun">运行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.run-json {
  margin: 12px 0 0;
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  background: var(--fl-color-fill-light, #f5f7fa);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
