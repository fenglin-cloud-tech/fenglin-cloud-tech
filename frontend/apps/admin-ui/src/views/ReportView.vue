<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { reportApi, type DashboardData, type DatasetQueryResult, type MetricCreateRequest, type MetricView } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canMetric = () => auth.can('tenant:report:metric:manage')
const canView = () => auth.can('biz:report:view')
const canExport = () => auth.can('biz:report:export')

const metrics = ref<MetricView[]>([])
const metricTotal = ref(0)
const metricLoading = ref(false)
const metricError = ref('')
const metricQuery = reactive({ category: '', status: '', page: 1, size: 10 })

const metricDialog = ref(false)
const metricSaving = ref(false)
const metricFormError = ref('')
const metricForm = reactive<MetricCreateRequest>({
  code: '',
  name: '',
  metric_type: 'ATOMIC',
  formula: '',
  unit: '',
  grain: 'DAY',
  dimensions: [],
})

const datasetId = ref<number | undefined>(undefined)
const datasetForm = reactive({ dimensions: '', measures: '', filters: '', limit: 100 })
const datasetLoading = ref(false)
const datasetError = ref('')
const datasetResult = ref<DatasetQueryResult | null>(null)

const dashboardId = ref<number | undefined>(undefined)
const chartIds = ref('')
const dashboardLoading = ref(false)
const dashboardError = ref('')
const dashboardResult = ref<DashboardData | null>(null)

const exportForm = reactive({ report_id: undefined as number | undefined, format: 'XLSX' })
const exportLoading = ref(false)
const exportError = ref('')
const exportResult = ref<{ file_id: string; download_url: string; watermark: boolean; format: string } | null>(null)

async function loadMetrics() {
  metricLoading.value = true
  metricError.value = ''
  try {
    const result = await reportApi.listMetrics(metricQuery)
    metrics.value = result.records
    metricTotal.value = result.total
  } catch (err) {
    metricError.value = describeError(err)
  } finally {
    metricLoading.value = false
  }
}

function openMetric() {
  Object.assign(metricForm, {
    code: '',
    name: '',
    metric_type: 'ATOMIC',
    formula: '',
    unit: '',
    grain: 'DAY',
    dimensions: [],
  })
  metricFormError.value = ''
  metricDialog.value = true
}

function updateDimensions(value: string) {
  metricForm.dimensions = value.split(',').map((item) => item.trim()).filter(Boolean)
}

async function submitMetric() {
  if (!metricForm.code || !metricForm.name || !metricForm.formula) {
    metricFormError.value = '指标编码、名称、口径公式为必填项'
    return
  }
  metricSaving.value = true
  metricFormError.value = ''
  try {
    await reportApi.createMetric({ ...metricForm })
    ElMessage.success('指标创建成功')
    metricDialog.value = false
    await loadMetrics()
  } catch (err) {
    metricFormError.value = describeError(err)
  } finally {
    metricSaving.value = false
  }
}

async function queryDataset() {
  if (!datasetId.value) return
  datasetLoading.value = true
  datasetError.value = ''
  datasetResult.value = null
  try {
    let filters: unknown[] = []
    if (datasetForm.filters.trim()) {
      filters = JSON.parse(datasetForm.filters)
    }
    datasetResult.value = await reportApi.queryDataset(datasetId.value, {
      dimensions: datasetForm.dimensions ? datasetForm.dimensions.split(',').map((i) => i.trim()).filter(Boolean) : undefined,
      measures: datasetForm.measures ? datasetForm.measures.split(',').map((i) => i.trim()).filter(Boolean) : undefined,
      filters,
      limit: datasetForm.limit,
    })
  } catch (err) {
    datasetError.value = err instanceof SyntaxError ? 'filters 不是合法 JSON' : describeError(err)
  } finally {
    datasetLoading.value = false
  }
}

async function loadDashboard() {
  if (!dashboardId.value) return
  dashboardLoading.value = true
  dashboardError.value = ''
  dashboardResult.value = null
  try {
    dashboardResult.value = await reportApi.dashboardData(dashboardId.value, {
      chart_ids: chartIds.value ? chartIds.value.split(',').map((i) => i.trim()).filter(Boolean) : undefined,
    })
  } catch (err) {
    dashboardError.value = describeError(err)
  } finally {
    dashboardLoading.value = false
  }
}

async function submitExport() {
  if (!exportForm.report_id) return
  exportLoading.value = true
  exportError.value = ''
  exportResult.value = null
  try {
    exportResult.value = await reportApi.exportReport({ report_id: exportForm.report_id, format: exportForm.format })
    ElMessage.success('报表导出完成')
  } catch (err) {
    exportError.value = describeError(err)
  } finally {
    exportLoading.value = false
  }
}

onMounted(loadMetrics)
</script>

<template>
  <div>
    <PageHeader module="报表中台 · A-24" title="报表中心" description="指标定义、数据集查询、看板数据与报表导出。">
      <template #actions>
        <el-button :loading="metricLoading" @click="loadMetrics">刷新指标</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '后端未提供数据集、看板列表接口，本页需手工输入 dataset_id / dashboard_id 进行查询。',
        '报表服务实际路径为 /api/hub/v1/report/** 与 /api/hub/v1/report:export，原网关仅配置 /reports/**；本轮已补齐网关路由。',
        '指标列表接口要求 tenant:report:metric:manage，数据集/看板查询要求 biz:report:view，导出要求 biz:report:export。',
        '当前权限：指标=' + (canMetric() ? '具备' : '缺失') + '，查看=' + (canView() ? '具备' : '缺失') + '，导出=' + (canExport() ? '具备' : '缺失') + '。',
      ]"
    />

    <div class="fl-card" style="margin-bottom: 16px">
      <div class="fl-section-title"><span>指标定义</span><span class="fl-muted">HUB-API-1101/1102</span></div>
      <div class="fl-toolbar">
        <el-input v-model="metricQuery.category" placeholder="指标类型" clearable style="width: 160px" />
        <el-select v-model="metricQuery.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="ACTIVE" value="ACTIVE" />
          <el-option label="DRAFT" value="DRAFT" />
        </el-select>
        <el-button type="primary" plain @click="metricQuery.page = 1; loadMetrics()">查询</el-button>
        <el-button v-perm="'tenant:report:metric:manage'" type="primary" @click="openMetric">新增指标</el-button>
      </div>
      <el-alert v-if="metricError" type="error" :closable="false" show-icon :title="metricError" style="margin-bottom: 12px" />
      <el-table :data="metrics" v-loading="metricLoading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="指标编码" width="180" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="metric_type" label="类型" width="110" />
        <el-table-column prop="formula" label="口径公式" min-width="200" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="90" />
        <el-table-column prop="grain" label="粒度" width="90" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
      <el-pagination
        v-model:current-page="metricQuery.page"
        v-model:page-size="metricQuery.size"
        :total="metricTotal"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="loadMetrics"
      />
    </div>

    <div class="fl-grid fl-grid--2" style="margin-bottom: 16px">
      <div class="fl-card">
        <div class="fl-section-title"><span>数据集查询</span><span class="fl-muted">HUB-API-1103</span></div>
        <el-alert v-if="datasetError" type="error" :closable="false" show-icon :title="datasetError" style="margin-bottom: 12px" />
        <el-form label-width="100px">
          <el-form-item label="数据集 ID"><el-input-number v-model="datasetId" :min="1" /></el-form-item>
          <el-form-item label="维度"><el-input v-model="datasetForm.dimensions" placeholder="逗号分隔，例如 dept,month" /></el-form-item>
          <el-form-item label="度量"><el-input v-model="datasetForm.measures" placeholder="逗号分隔，例如 amount,cnt" /></el-form-item>
          <el-form-item label="过滤条件"><el-input v-model="datasetForm.filters" type="textarea" :rows="3" placeholder='JSON 数组，例如 [{"field":"status","op":"EQ","value":"ACTIVE"}]' /></el-form-item>
          <el-form-item label="limit"><el-input-number v-model="datasetForm.limit" :min="1" :max="5000" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="datasetLoading" @click="queryDataset">查询</el-button></el-form-item>
        </el-form>
        <el-table v-if="datasetResult" :data="datasetResult.rows.map((row) => Object.fromEntries(datasetResult!.columns.map((c, i) => [c, row[i]])))" border size="small" max-height="260">
          <el-table-column v-for="column in datasetResult.columns" :key="column" :prop="column" :label="column" min-width="120" />
        </el-table>
      </div>

      <div class="fl-card">
        <div class="fl-section-title"><span>看板数据</span><span class="fl-muted">HUB-API-1104</span></div>
        <el-alert v-if="dashboardError" type="error" :closable="false" show-icon :title="dashboardError" style="margin-bottom: 12px" />
        <el-form label-width="100px">
          <el-form-item label="看板 ID"><el-input-number v-model="dashboardId" :min="1" /></el-form-item>
          <el-form-item label="图表 ID"><el-input v-model="chartIds" placeholder="逗号分隔，可选" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="dashboardLoading" @click="loadDashboard">加载</el-button></el-form-item>
        </el-form>
        <template v-if="dashboardResult">
          <el-alert v-if="dashboardResult.degraded" type="warning" :closable="false" show-icon title="看板存在降级图表" style="margin-bottom: 12px" />
          <div v-for="chart in dashboardResult.charts" :key="chart.chart_id" class="chart-block">
            <strong>图表 {{ chart.chart_id }}</strong>
            <el-tag v-if="chart.degraded" size="small" type="warning" style="margin-left: 6px">降级 {{ chart.error_code }}</el-tag>
            <el-table :data="chart.rows.map((row) => Object.fromEntries(chart.columns.map((c, i) => [c, row[i]])))" border size="small" max-height="220" style="margin-top: 8px">
              <el-table-column v-for="column in chart.columns" :key="column" :prop="column" :label="column" min-width="110" />
            </el-table>
          </div>
          <el-empty v-if="dashboardResult.charts.length === 0" description="无图表数据" :image-size="60" />
        </template>
      </div>
    </div>

    <div class="fl-card">
      <div class="fl-section-title"><span>报表导出</span><span class="fl-muted">HUB-API-1105</span></div>
      <el-alert v-if="exportError" type="error" :closable="false" show-icon :title="exportError" style="margin-bottom: 12px" />
      <div class="fl-toolbar">
        <el-input-number v-model="exportForm.report_id" :min="1" placeholder="report_id" />
        <el-select v-model="exportForm.format" style="width: 130px">
          <el-option label="XLSX" value="XLSX" />
          <el-option label="CSV" value="CSV" />
          <el-option label="PDF" value="PDF" />
        </el-select>
        <el-button v-perm="'biz:report:export'" type="primary" :loading="exportLoading" @click="submitExport">导出</el-button>
      </div>
      <el-descriptions v-if="exportResult" :column="2" border>
        <el-descriptions-item label="file_id">{{ exportResult.file_id }}</el-descriptions-item>
        <el-descriptions-item label="格式">{{ exportResult.format }}</el-descriptions-item>
        <el-descriptions-item label="水印">{{ exportResult.watermark ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="下载地址">
          <a :href="exportResult.download_url" target="_blank" rel="noopener">打开签名下载地址</a>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <el-dialog v-model="metricDialog" title="新增指标" width="560px">
      <el-alert v-if="metricFormError" type="error" :closable="false" show-icon :title="metricFormError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="指标编码" required><el-input v-model="metricForm.code" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="metricForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="metricForm.metric_type" style="width: 100%">
            <el-option label="ATOMIC" value="ATOMIC" />
            <el-option label="DERIVED" value="DERIVED" />
            <el-option label="COMPOSITE" value="COMPOSITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="口径公式" required><el-input v-model="metricForm.formula" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="metricForm.unit" /></el-form-item>
        <el-form-item label="粒度">
          <el-select v-model="metricForm.grain" style="width: 100%">
            <el-option label="DAY" value="DAY" />
            <el-option label="MONTH" value="MONTH" />
            <el-option label="YEAR" value="YEAR" />
          </el-select>
        </el-form-item>
        <el-form-item label="维度">
          <el-input
            :model-value="(metricForm.dimensions || []).join(',')"
            placeholder="逗号分隔"
            @update:model-value="updateDimensions"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metricDialog = false">取消</el-button>
        <el-button type="primary" :loading="metricSaving" @click="submitMetric">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.chart-block {
  padding: 10px 0;
  border-bottom: 1px dashed var(--fl-color-border);
}
.chart-block:last-child {
  border-bottom: none;
}
</style>
