<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const tasks = ref<AiRecord[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({
  name: '',
  target_type: 'METRIC',
  target_id: '',
  trigger_type: 'MANUAL',
  cron_expr: '',
  ruleText: '',
})

const resultsVisible = ref(false)
const resultsLoading = ref(false)
const resultsError = ref('')
const results = ref<AiRecord[]>([])
const resultsTask = ref<AiRecord | null>(null)

const attrForm = reactive({ metric_id: '', dimension_field: '', algorithm: 'CONTRIBUTION' })
const attrLoading = ref(false)
const attrError = ref('')
const attrResult = ref<AiRecord | null>(null)
const historyLoading = ref(false)
const historyError = ref('')
const attributions = ref<AiRecord[]>([])

const exportForm = reactive({ report_type: '', metric_id: '', format: 'XLSX' })
const exportLoading = ref(false)
const exportError = ref('')
const exportResult = ref<AiRecord | null>(null)

const gapItems = [
  '洞察结果为同步计算，接口没有异步任务状态轮询，运行完成后直接读取结果。',
  '导出只返回后端结果对象（export_id/status/delegate），没有文件下载接口。',
  '任务列表与结果要求 ai:bi:view；创建与运行要求 ai:bi:insight:run；归因计算要求 ai:bi:attribution:run；导出要求 ai:bi:export。',
]

const attrRows = computed<AiRecord[]>(() => attributions.value)

async function loadTasks() {
  loading.value = true
  error.value = ''
  try {
    const result = await intelligenceApi.insight.tasks({ page: page.value, size: size.value })
    tasks.value = result.records ?? []
    total.value = result.total ?? 0
  } catch (err) {
    tasks.value = []
    total.value = 0
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createError.value = ''
  form.name = ''
  form.target_type = 'METRIC'
  form.target_id = ''
  form.trigger_type = 'MANUAL'
  form.cron_expr = ''
  form.ruleText = ''
  createVisible.value = true
}

async function submitCreate() {
  createError.value = ''
  if (!form.name.trim() || !form.target_id.trim()) {
    createError.value = '名称与目标 ID 为必填项'
    return
  }
  let rule: AiRecord | undefined
  try {
    rule = form.ruleText.trim() ? (JSON.parse(form.ruleText) as AiRecord) : undefined
  } catch {
    createError.value = '规则 JSON 不合法'
    return
  }
  createSaving.value = true
  try {
    await intelligenceApi.insight.createTask({
      name: form.name.trim(),
      target_type: form.target_type,
      target_id: form.target_id,
      trigger_type: form.trigger_type,
      cron_expr: form.cron_expr.trim() || undefined,
      rule,
    })
    ElMessage.success('洞察任务已创建')
    createVisible.value = false
    page.value = 1
    await loadTasks()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function runTask(row: AiRecord) {
  try {
    const result = await intelligenceApi.insight.runTask(row.id)
    ElMessage.success(`任务已运行：${result?.anomaly_type ?? '完成'}`)
    await loadTasks()
    await openResults(row)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function openResults(row: AiRecord) {
  resultsTask.value = row
  resultsVisible.value = true
  results.value = []
  resultsError.value = ''
  resultsLoading.value = true
  try {
    results.value = await intelligenceApi.insight.results(row.id)
  } catch (err) {
    resultsError.value = describeError(err)
  } finally {
    resultsLoading.value = false
  }
}

async function runAttribution() {
  attrError.value = ''
  if (!attrForm.metric_id || !attrForm.dimension_field.trim()) {
    attrError.value = '指标 ID 与归因维度字段为必填项'
    return
  }
  attrLoading.value = true
  attrResult.value = null
  try {
    attrResult.value = await intelligenceApi.insight.attribution({
      metric_id: attrForm.metric_id,
      dimension_field: attrForm.dimension_field.trim(),
      algorithm: attrForm.algorithm,
    })
    ElMessage.success('归因已提交')
  } catch (err) {
    attrError.value = describeError(err)
  } finally {
    attrLoading.value = false
  }
}

async function loadAttributions() {
  historyError.value = ''
  if (!attrForm.metric_id) {
    historyError.value = '请先填写指标 ID'
    return
  }
  historyLoading.value = true
  try {
    attributions.value = await intelligenceApi.insight.attributions(attrForm.metric_id, 20)
  } catch (err) {
    attributions.value = []
    historyError.value = describeError(err)
  } finally {
    historyLoading.value = false
  }
}

async function submitExport() {
  exportError.value = ''
  exportLoading.value = true
  exportResult.value = null
  try {
    exportResult.value = await intelligenceApi.insight.exportReport({
      report_type: exportForm.report_type.trim() || undefined,
      metric_id: exportForm.metric_id ? exportForm.metric_id : undefined,
      format: exportForm.format,
    })
    ElMessage.success('导出请求已提交')
  } catch (err) {
    exportError.value = describeError(err)
  } finally {
    exportLoading.value = false
  }
}

function changePage(next: number) {
  page.value = next
  loadTasks()
}

onMounted(loadTasks)
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="自动洞察与归因"
      description="洞察任务、运行结果、归因分析与报表导出；数据来自 /api/intelligence/v1/insight-tasks 与 /analytics。"
    >
      <template #actions>
        <el-button v-perm="'ai:bi:insight:run'" type="primary" @click="openCreate">新建洞察任务</el-button>
        <el-button :loading="loading" @click="loadTasks">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>洞察任务</template>
      <el-table v-loading="loading" :data="tasks" border stripe max-height="360">
        <el-table-column prop="id" label="ID" width="170" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="target_type" label="目标类型" width="110" />
        <el-table-column prop="target_id" label="目标 ID" width="170" />
        <el-table-column prop="trigger_type" label="触发方式" width="110" />
        <el-table-column prop="cron_expr" label="Cron" min-width="140" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="last_run_at" label="最近运行" width="170" />
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'ai:bi:insight:run'" link type="primary" @click="runTask(row)">运行</el-button>
            <el-button link type="primary" @click="openResults(row)">结果</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无洞察任务" :image-size="60" />
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
    </el-card>

    <el-row :gutter="12">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header>归因分析</template>
          <el-alert v-if="attrError" type="error" :closable="false" show-icon :title="attrError" style="margin-bottom: 12px" />
          <el-form label-width="90px">
            <el-form-item label="指标 ID" required><el-input v-model="attrForm.metric_id" /></el-form-item>
            <el-form-item label="归因维度" required><el-input v-model="attrForm.dimension_field" placeholder="如 channel" /></el-form-item>
            <el-form-item label="算法">
              <el-select v-model="attrForm.algorithm" style="width: 100%">
                <el-option label="贡献度（CONTRIBUTION）" value="CONTRIBUTION" />
                <el-option label="对比（COMPARE）" value="COMPARE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'ai:bi:attribution:run'" type="primary" :loading="attrLoading" @click="runAttribution">计算归因</el-button>
              <el-button :loading="historyLoading" @click="loadAttributions">历史归因</el-button>
            </el-form-item>
          </el-form>
          <pre v-if="attrResult" class="fl-mono result-json">{{ JSON.stringify(attrResult, null, 2) }}</pre>
          <el-alert v-if="historyError" type="error" :closable="false" show-icon :title="historyError" style="margin: 12px 0" />
          <el-table v-if="attrRows.length" :data="attrRows" border size="small" max-height="240">
            <el-table-column prop="id" label="ID" width="170" />
            <el-table-column prop="metric_id" label="指标" width="170" />
            <el-table-column prop="dimension_field" label="维度" width="110" />
            <el-table-column prop="algorithm" label="算法" width="120" />
            <el-table-column prop="main_factor" label="主因" min-width="120" />
            <el-table-column prop="created_at" label="时间" width="170" />
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header>报表导出</template>
          <el-alert v-if="exportError" type="error" :closable="false" show-icon :title="exportError" style="margin-bottom: 12px" />
          <el-form label-width="90px">
            <el-form-item label="报表类型"><el-input v-model="exportForm.report_type" placeholder="选填，如 INSIGHT" /></el-form-item>
            <el-form-item label="指标 ID"><el-input v-model="exportForm.metric_id" placeholder="选填" /></el-form-item>
            <el-form-item label="格式">
              <el-select v-model="exportForm.format" style="width: 100%">
                <el-option label="XLSX" value="XLSX" />
                <el-option label="CSV" value="CSV" />
                <el-option label="PDF" value="PDF" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'ai:bi:export'" type="primary" :loading="exportLoading" @click="submitExport">提交导出</el-button>
            </el-form-item>
          </el-form>
          <pre v-if="exportResult" class="fl-mono result-json">{{ JSON.stringify(exportResult, null, 2) }}</pre>
          <el-empty v-else description="尚未提交导出" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="resultsVisible" title="洞察结果" size="680px">
      <el-alert v-if="resultsError" type="error" :closable="false" show-icon :title="resultsError" style="margin-bottom: 12px" />
      <div class="fl-muted" style="margin-bottom: 8px">任务：{{ resultsTask?.name ?? resultsTask?.id ?? '-' }}</div>
      <el-table v-loading="resultsLoading" :data="results" border size="small" max-height="520">
        <el-table-column prop="id" label="ID" width="170" />
        <el-table-column prop="anomaly_type" label="异常类型" width="110" />
        <el-table-column prop="magnitude" label="幅度" width="90" />
        <el-table-column prop="conclusion" label="结论" min-width="240" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="建议" min-width="240" show-overflow-tooltip />
        <el-table-column prop="confidence" label="置信度" width="90" />
        <el-table-column prop="created_at" label="时间" width="170" />
        <template #empty>
          <el-empty description="暂无结果" :image-size="60" />
        </template>
      </el-table>
    </el-drawer>

    <el-dialog v-model="createVisible" title="新建洞察任务" width="620px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="120px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="form.target_type" style="width: 100%">
            <el-option label="指标（METRIC）" value="METRIC" />
            <el-option label="数据集（DATASET）" value="DATASET" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标 ID" required><el-input v-model="form.target_id" placeholder="对应指标/数据集主键" /></el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="form.trigger_type" style="width: 100%">
            <el-option label="手动（MANUAL）" value="MANUAL" />
            <el-option label="定时（CRON）" value="CRON" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron"><el-input v-model="form.cron_expr" placeholder="触发方式为 CRON 时填写" /></el-form-item>
        <el-form-item label="规则 JSON">
          <el-input v-model="form.ruleText" type="textarea" :rows="3" class="fl-mono" placeholder='选填，如 {"threshold":2.4}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.result-json {
  margin: 8px 0 0;
  padding: 12px;
  max-height: 240px;
  overflow: auto;
  background: var(--fl-color-fill-light, #f5f7fa);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
