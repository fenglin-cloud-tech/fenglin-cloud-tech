<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH_COMFORT, formatDateTime } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canView = auth.can('ai:bi:view')
const canSubscribe = auth.can('ai:bi:insight:subscribe')
const canNl2sql = auth.can('ai:bi:nl2sql:use')

const tasks = ref<AiRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const error = ref('')

const resultsVisible = ref(false)
const resultsLoading = ref(false)
const resultsError = ref('')
const results = ref<AiRecord[]>([])
const resultsTask = ref<AiRecord | null>(null)

const subscriptions = ref<AiRecord[]>([])
const subLoading = ref(false)
const subError = ref('')
const subscribeVisible = ref(false)
const subscribeSaving = ref(false)
const subscribeForm = reactive({
  task_id: undefined as number | undefined,
  frequency: 'DAILY',
  channels: ['STATION'] as string[],
  receivers: '',
})

const datasets = ref<AiRecord[]>([])
const nlForm = reactive({ question: '', dataset_id: undefined as number | undefined })
const nlLoading = ref(false)
const nlError = ref('')
const nlResult = ref<AiRecord | null>(null)

const gapItems = [
  '问数为只读生成：后端按数据集字段模板生成 SQL，可能返回 degraded / note 受限提示，页面不执行写操作。',
  '平台未提供订阅取消 / 修改接口，只能新增订阅；订阅列表按最新记录展示。',
  '洞察结果来自任务运行产物，若无运行记录则为空，不提供模拟数据。',
]

const frequencyOptions = [
  { value: 'DAILY', label: '每日' },
  { value: 'WEEKLY', label: '每周' },
  { value: 'MONTHLY', label: '每月' },
  { value: 'REALTIME', label: '实时' },
]

const channelOptions = [
  { value: 'STATION', label: '站内信' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'IM', label: '即时消息' },
]

const subscribeTask = computed(() => tasks.value.find((item) => item.id === subscribeForm.task_id))

const nlRows = computed<AiRecord[]>(() => {
  const data = nlResult.value
  const found = data ? [data.rows, data.records, data.data, data.result].find((item) => Array.isArray(item)) : undefined
  return found ? (found as AiRecord[]) : []
})

const nlColumns = computed(() => (nlRows.value.length > 0 ? Object.keys(nlRows.value[0]) : []))

const nlSql = computed(() => String(nlResult.value?.sql ?? nlResult.value?.generated_sql ?? ''))

const nlNote = computed(() => String(nlResult.value?.note ?? nlResult.value?.message ?? ''))

function channelText(value: unknown) {
  if (!value) return '-'
  return Array.isArray(value) ? value.join('、') : String(value)
}

function frequencyText(value?: string) {
  return frequencyOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

async function loadTasks() {
  if (!canView) return
  loading.value = true; error.value = ''
  try {
    const result = await intelligenceApi.insight.tasks({ page: page.value, size: size.value })
    tasks.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
    tasks.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadSubscriptions() {
  if (!canView) return
  subLoading.value = true; subError.value = ''
  try {
    subscriptions.value = await intelligenceApi.insight.subscriptions(20)
  } catch (err) {
    subError.value = describeError(err)
    subscriptions.value = []
  } finally {
    subLoading.value = false
  }
}

async function loadDatasets() {
  if (!canView) return
  try {
    datasets.value = (await intelligenceApi.datasets.list({ page: 1, size: 100 })).records
  } catch {
    datasets.value = []
  }
}

async function openResults(task: AiRecord) {
  resultsTask.value = task
  resultsVisible.value = true
  resultsLoading.value = true
  resultsError.value = ''; results.value = []
  try {
    results.value = await intelligenceApi.insight.results(task.id)
  } catch (err) {
    resultsError.value = describeError(err)
  } finally {
    resultsLoading.value = false
  }
}

function openSubscribe(task?: AiRecord) {
  subscribeForm.task_id = task ? task.id : undefined
  subscribeForm.frequency = 'DAILY'; subscribeForm.channels = ['STATION']; subscribeForm.receivers = ''
  subscribeVisible.value = true
}

async function submitSubscribe() {
  if (!subscribeForm.task_id) {
    ElMessage.warning('请选择要订阅的洞察任务')
    return
  }
  subscribeSaving.value = true
  try {
    const task = subscribeTask.value
    await intelligenceApi.insight.subscribe({
      task_id: subscribeForm.task_id,
      metric_id: task?.target_type === 'METRIC' ? task.target_id : undefined,
      frequency: subscribeForm.frequency,
      channels: subscribeForm.channels.join(',') || 'STATION',
      receivers: subscribeForm.receivers || undefined,
    })
    ElMessage.success('订阅已创建')
    subscribeVisible.value = false
    await loadSubscriptions()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    subscribeSaving.value = false
  }
}

async function ask() {
  if (!nlForm.question.trim() || !nlForm.dataset_id) {
    ElMessage.warning(!nlForm.question.trim() ? '请输入问题' : '请选择数据集')
    return
  }
  nlLoading.value = true
  nlError.value = ''
  nlResult.value = null
  try {
    nlResult.value = await intelligenceApi.insight.nl2sql({
      question: nlForm.question.trim(),
      dataset_id: nlForm.dataset_id,
    })
  } catch (err) {
    nlError.value = describeError(err)
  } finally {
    nlLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadTasks(), loadSubscriptions(), loadDatasets()])
})
</script>

<template>
  <div>
    <PageHeader module="丰麟云智" title="洞察与问数" description="洞察任务、结果订阅与自然语言问数；数据来自 /insight-tasks、/subscriptions 与 /analytics/nl2sql。">
      <template #actions><el-button :loading="loading" @click="loadTasks">刷新</el-button></template>
    </PageHeader>

    <el-alert v-if="!canView" type="info" :closable="false" show-icon title="缺少权限 ai:bi:view，无法查看洞察任务。" style="margin-bottom: 12px" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div class="card-head"><span>洞察任务</span><span class="fl-muted">共 {{ total }} 个</span></div>
      </template>
      <el-table v-loading="loading" :data="tasks" border stripe size="small">
        <el-table-column prop="name" label="任务名称" min-width="200" />
        <el-table-column prop="target_type" label="对象" width="100" />
        <el-table-column prop="target_id" label="对象ID" :width="ID_COLUMN_WIDTH_COMFORT" show-overflow-tooltip />
        <el-table-column prop="trigger_type" label="触发方式" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="last_run_at" label="最近运行" width="150" :formatter="(row: AiRecord) => formatDateTime(row.last_run_at)" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openResults(row)">结果</el-button>
            <el-button v-if="canSubscribe" link type="success" @click="openSubscribe(row)">订阅</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="canView" description="暂无洞察任务" :image-size="70" />
        </template>
      </el-table>
      <el-pagination style="margin-top: 8px; justify-content: flex-end" layout="total, prev, pager, next"
        :total="total" :page-size="size" :current-page="page"
        @current-change="(next: number) => { page = next; loadTasks() }" />
    </el-card>

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div class="card-head">
          <span>洞察订阅</span>
          <el-button v-if="canSubscribe" link type="primary" @click="openSubscribe()">新建订阅</el-button>
        </div>
      </template>
      <el-alert v-if="subError" type="error" :closable="false" show-icon :title="subError" style="margin-bottom: 8px" />
      <el-table v-loading="subLoading" :data="subscriptions" border stripe size="small">
        <el-table-column prop="id" label="订阅ID" :width="ID_COLUMN_WIDTH_COMFORT" show-overflow-tooltip />
        <el-table-column prop="task_id" label="任务ID" :width="ID_COLUMN_WIDTH_COMFORT" show-overflow-tooltip />
        <el-table-column label="渠道" width="130">
          <template #default="{ row }">{{ channelText(row.channels) }}</template>
        </el-table-column>
        <el-table-column label="接收人" min-width="160">
          <template #default="{ row }">{{ row.receivers || '-' }}</template>
        </el-table-column>
        <el-table-column label="频率" width="100" :formatter="(row: AiRecord) => frequencyText(row.frequency)" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="created_at" label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="canView" description="暂无订阅" :image-size="70" />
        </template>
      </el-table>
      <span v-if="!canSubscribe" class="fl-muted">缺少权限 ai:bi:insight:subscribe，不显示新建订阅入口。</span>
    </el-card>

    <el-card shadow="never">
      <template #header><span>自然语言问数</span></template>
      <el-form label-width="80px" size="small">
        <el-form-item label="数据集">
          <el-select v-model="nlForm.dataset_id" clearable filterable style="width: 300px" placeholder="选择数据集"><el-option v-for="item in datasets" :key="String(item.id)" :label="item.name || item.code" :value="item.id" /></el-select>
        </el-form-item>
        <el-form-item label="问题">
          <el-input v-model="nlForm.question" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="例如：按渠道统计最近一个月各指标总量" />
        </el-form-item>
        <el-form-item>
          <el-button v-if="canNl2sql" type="primary" :loading="nlLoading" @click="ask">生成并查询</el-button>
          <span v-else class="fl-muted">缺少权限 ai:bi:nl2sql:use，无法使用问数。</span>
        </el-form-item>
      </el-form>

      <el-alert v-if="nlError" type="error" :closable="false" show-icon :title="nlError" style="margin-bottom: 8px" />

      <div v-loading="nlLoading">
        <template v-if="nlResult">
          <el-alert v-if="nlResult.degraded" type="warning" :closable="false" show-icon
            :title="nlNote || '后端返回 degraded=true，当前为模板解析结果。'" style="margin-bottom: 8px" />
          <el-divider content-position="left">生成的 SQL</el-divider>
          <pre class="sql-box">{{ nlSql || '后端未返回 SQL' }}</pre>
          <el-table v-if="nlRows.length > 0" :data="nlRows" border stripe size="small">
            <el-table-column v-for="column in nlColumns" :key="column" :prop="column" :label="column" min-width="120" />
          </el-table>
          <el-empty v-else description="后端未返回结果行（当前问数接口只生成 SQL）" :image-size="70" />
          <div v-if="nlNote && !nlResult.degraded" class="fl-muted" style="margin-top: 8px">{{ nlNote }}</div>
        </template>
        <el-empty v-else-if="!nlLoading && !nlError" description="输入问题后生成 SQL 与结果" :image-size="70" />
      </div>
    </el-card>

    <el-drawer v-model="resultsVisible" :title="`洞察结果 · ${resultsTask?.name || ''}`" size="640px">
      <div v-loading="resultsLoading">
        <el-alert v-if="resultsError" type="error" :closable="false" show-icon :title="resultsError" style="margin-bottom: 8px" />
        <div v-for="item in results" :key="String(item.id)" class="result-item">
          <div class="result-item__head">
            <el-tag size="small" type="warning">{{ item.anomaly_type || '-' }}</el-tag>
            <span class="fl-muted">置信度 {{ item.confidence ?? '-' }} · {{ formatDateTime(item.created_at) }}</span>
          </div>
          <div class="result-item__text">{{ item.conclusion || '-' }}</div>
          <div class="fl-muted">建议：{{ item.suggestion || '-' }}</div>
          <div class="fl-muted">指标ID {{ item.metric_id ?? '-' }} · 幅度 {{ item.magnitude ?? '-' }}</div>
        </div>
        <el-empty v-if="results.length === 0 && !resultsLoading" description="该任务暂无运行结果" :image-size="70" />
      </div>
    </el-drawer>

    <el-dialog v-model="subscribeVisible" title="新建洞察订阅" width="520px">
      <el-form label-width="90px">
        <el-form-item label="洞察任务">
          <el-select v-model="subscribeForm.task_id" filterable style="width: 100%" placeholder="选择订阅的洞察任务"><el-option v-for="item in tasks" :key="String(item.id)" :label="item.name || String(item.id)" :value="item.id" /></el-select>
        </el-form-item>
        <el-form-item label="指标ID"><span>{{ subscribeTask?.target_type === 'METRIC' ? subscribeTask?.target_id : '该任务不是指标类型，可不传' }}</span></el-form-item>
        <el-form-item label="频率">
          <el-select v-model="subscribeForm.frequency" style="width: 100%"><el-option v-for="item in frequencyOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        </el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="subscribeForm.channels" multiple style="width: 100%"><el-option v-for="item in channelOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        </el-form-item>
        <el-form-item label="接收人"><el-input v-model="subscribeForm.receivers" placeholder="可选，多个接收人用逗号分隔" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subscribeVisible = false">取消</el-button>
        <el-button type="primary" :loading="subscribeSaving" @click="submitSubscribe">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-head { display: flex; align-items: center; justify-content: space-between; }
.sql-box { margin: 0; padding: 12px; background: var(--el-fill-color-lighter); border-radius: 4px; font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
.result-item { padding: 10px 0; border-bottom: 1px dashed var(--fl-color-border); }
.result-item__head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.result-item__text { font-size: 13px; line-height: 1.7; margin-bottom: 4px; }
.fl-muted { color: var(--fl-color-text-secondary); font-size: 12px; }
</style>
