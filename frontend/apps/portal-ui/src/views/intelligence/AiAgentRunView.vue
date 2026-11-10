<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canUse = auth.can('ai:chat:use')
const canRun = auth.can('ai:agent:run')

const agents = ref<AiRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const error = ref('')

const selectedCode = ref('')
const input = ref('')
const running = ref(false)
const runError = ref('')
const runResult = ref<AiRecord | null>(null)

const gapItems = [
  '后端未提供智能体执行历史查询接口，运行结果为同步返回，刷新后不在页面留存。',
  '运行接口 POST /agents/{code}/run 同步等待模型返回，没有流式或异步任务进度。',
  '仅 PUBLISHED 状态的智能体可运行；未发布时后端返回“智能体未发布”，页面原样提示。',
]

const selectedAgent = computed(() => agents.value.find((item) => String(item.code) === selectedCode.value))

const answerText = computed(() => {
  const data = runResult.value
  if (!data) return ''
  const candidates = [data.answer, data.content, data.output, data.result]
  const found = candidates.find((item) => typeof item === 'string' && item.trim().length > 0)
  return found ? String(found) : ''
})

function statusTagType(status?: string): 'success' | 'info' | 'warning' {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'DRAFT') return 'info'
  return 'warning'
}

async function load() {
  if (!canUse) return
  loading.value = true
  error.value = ''
  try {
    const result = await intelligenceApi.agents.list({ page: page.value, size: size.value })
    agents.value = result.records
    total.value = result.total
    if (selectedCode.value && !result.records.some((item) => String(item.code) === selectedCode.value)) {
      selectedCode.value = ''
    }
  } catch (err) {
    error.value = describeError(err)
    agents.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function selectAgent(row: AiRecord) {
  selectedCode.value = String(row.code)
  runResult.value = null
  runError.value = ''
}

async function run() {
  if (!selectedCode.value) {
    ElMessage.warning('请先选择智能体')
    return
  }
  if (!input.value.trim()) {
    ElMessage.warning('请输入执行输入')
    return
  }
  running.value = true
  runError.value = ''
  runResult.value = null
  try {
    runResult.value = await intelligenceApi.agents.run(selectedCode.value, {
      input: { text: input.value.trim() },
    })
  } catch (err) {
    runError.value = describeError(err)
  } finally {
    running.value = false
  }
}

function clearResult() {
  runResult.value = null
  runError.value = ''
  input.value = ''
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="丰麟云智"
      title="智能体"
      description="选择已发布智能体并同步运行；列表与执行接口均来自 /api/intelligence/v1。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="!canUse" type="info" :closable="false" show-icon
      title="缺少权限 ai:chat:use，无法查看智能体列表。" style="margin-bottom: 12px" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12">
      <el-col :span="14">
        <el-card shadow="never" class="agent-card">
          <template #header>
            <div class="card-head">
              <span>智能体列表</span>
              <span class="fl-muted">共 {{ total }} 个</span>
            </div>
          </template>
          <el-table
            v-loading="loading"
            :data="agents"
            border
            stripe
            size="small"
            highlight-current-row
            @row-click="selectAgent"
          >
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="code" label="编码" min-width="150" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">{{ row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version_no" label="版本" width="80" />
            <el-table-column prop="model_code" label="模型" width="130">
              <template #default="{ row }">{{ row.model_code || '-' }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="canUse && agents.length === 0 && !loading" description="暂无智能体" :image-size="70" />
          <el-pagination
            style="margin-top: 8px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="(next: number) => { page = next; load() }"
          />
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never" class="agent-card">
          <template #header><span>运行智能体</span></template>
          <el-descriptions v-if="selectedAgent" :column="1" border size="small" style="margin-bottom: 8px">
            <el-descriptions-item label="名称">{{ selectedAgent.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="编码">{{ selectedAgent.code || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ selectedAgent.status || '-' }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ selectedAgent.description || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="从左侧选择一个智能体" :image-size="60" />

          <el-alert
            v-if="selectedAgent && selectedAgent.status !== 'PUBLISHED'"
            type="warning"
            :closable="false"
            show-icon
            title="该智能体不是 PUBLISHED 状态，运行时后端会返回“智能体未发布”。"
            style="margin-bottom: 8px"
          />

          <el-form label-width="70px" size="small">
            <el-form-item label="输入">
              <el-input
                v-model="input"
                type="textarea"
                :rows="4"
                maxlength="1000"
                show-word-limit
                placeholder="描述要交给智能体处理的任务"
              />
            </el-form-item>
            <el-form-item>
              <el-button v-if="canRun" type="primary" :loading="running" :disabled="!selectedCode" @click="run">
                运行
              </el-button>
              <span v-else class="fl-muted">缺少权限 ai:agent:run，无法运行智能体。</span>
              <el-button @click="clearResult">清空</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>运行结果</span>
          <span v-if="runResult" class="fl-muted">执行ID {{ runResult.id ?? '-' }}</span>
        </div>
      </template>
      <el-alert v-if="runError" type="error" :closable="false" show-icon :title="runError" />
      <template v-if="runResult">
        <el-descriptions :column="3" border size="small" style="margin-bottom: 8px">
          <el-descriptions-item label="状态">{{ runResult.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="步骤数">{{ runResult.step_count ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="Token">{{ runResult.token_total ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="智能体ID">{{ runResult.agent_id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="追踪ID">{{ runResult.trace_id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行时间">{{ runResult.created_at || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="answerText" class="answer-box">{{ answerText }}</div>
        <el-empty v-else description="后端未返回文本回答，请查看下方原始结果" :image-size="60" />
        <el-divider content-position="left">原始返回</el-divider>
        <pre class="result-json">{{ JSON.stringify(runResult, null, 2) }}</pre>
      </template>
      <el-empty v-else-if="!runError" description="运行后在此查看结果" :image-size="70" />
    </el-card>
  </div>
</template>

<style scoped>
.agent-card {
  min-height: 420px;
  margin-bottom: 12px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.answer-box {
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.result-json {
  margin: 0;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 260px;
  overflow: auto;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
