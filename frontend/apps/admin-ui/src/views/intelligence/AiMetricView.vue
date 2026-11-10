<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
const activeTab = ref('metrics')
const metricRows = ref<AiRecord[]>([])
const metricTotal = ref(0)
const metricLoading = ref(false)
const metricError = ref('')
const metricPage = ref(1)
const metricSize = ref(20)
const keyword = ref('')
const metricVisible = ref(false)
const metricSaving = ref(false)
const metricCreateError = ref('')
const metricForm = reactive({
  code: '',
  name: '',
  category: '',
  metric_type: 'ATOMIC',
  formula: '',
  unit: '',
  grain: 'DAY',
})
const datasetRows = ref<AiRecord[]>([])
const datasetTotal = ref(0)
const datasetLoading = ref(false)
const datasetError = ref('')
const datasetPage = ref(1)
const datasetSize = ref(20)
const datasetVisible = ref(false)
const datasetSaving = ref(false)
const datasetCreateError = ref('')
const datasetForm = reactive({
  code: '',
  name: '',
  data_source_id: '',
  mode: 'DIRECT',
  sql_text: '',
  refresh_cron: '',
})
const selectedDataset = ref<AiRecord | null>(null)
const fields = ref<AiRecord[]>([])
const fieldsLoading = ref(false)
const fieldsError = ref('')
const fieldVisible = ref(false)
const fieldSaving = ref(false)
const fieldError = ref('')
const fieldForm = reactive({
  field_name: '',
  field_type: 'DIMENSION',
  data_type: 'STRING',
  expression: '',
  is_sensitive: false,
})
const gapItems = [
  '数据集没有预览、行级采样接口，字段列表只返回元数据，不能查看真实数据。',
  '字段没有删除接口，只能追加；新增字段需先选中数据集。',
  '指标列表与数据集列表要求 ai:bi:view；指标创建要求 tenant:ai:bi:metric:manage，数据集与字段要求 ai:bi:dataset:manage。',
]
async function loadMetrics() {
  metricLoading.value = true
  metricError.value = ''
  try {
    const result = await intelligenceApi.metrics.list({
      keyword: keyword.value.trim() || undefined,
      page: metricPage.value,
      size: metricSize.value,
    })
    metricRows.value = result.records ?? []
    metricTotal.value = result.total ?? 0
  } catch (err) {
    metricRows.value = []
    metricTotal.value = 0
    metricError.value = describeError(err)
  } finally {
    metricLoading.value = false
  }
}
function openMetric() {
  metricCreateError.value = ''
  metricVisible.value = true
}
async function submitMetric() {
  metricCreateError.value = ''
  if (!metricForm.code.trim() || !metricForm.name.trim() || !metricForm.formula.trim()) {
    metricCreateError.value = '编码、名称与口径公式为必填项'
    return
  }
  metricSaving.value = true
  try {
    const created = await intelligenceApi.metrics.create({
      code: metricForm.code.trim(),
      name: metricForm.name.trim(),
      category: metricForm.category.trim() || undefined,
      metric_type: metricForm.metric_type,
      formula: metricForm.formula.trim(),
      unit: metricForm.unit.trim() || undefined,
      grain: metricForm.grain,
    })
    ElMessage.success(`指标已创建：${created?.code ?? created?.id ?? ''}`)
    metricVisible.value = false
    metricForm.code = ''
    metricForm.name = ''
    metricForm.category = ''
    metricForm.metric_type = 'ATOMIC'
    metricForm.formula = ''
    metricForm.unit = ''
    metricForm.grain = 'DAY'
    metricPage.value = 1
    await loadMetrics()
  } catch (err) {
    metricCreateError.value = describeError(err)
  } finally {
    metricSaving.value = false
  }
}
async function loadDatasets() {
  datasetLoading.value = true
  datasetError.value = ''
  try {
    const result = await intelligenceApi.datasets.list({ page: datasetPage.value, size: datasetSize.value })
    datasetRows.value = result.records ?? []
    datasetTotal.value = result.total ?? 0
  } catch (err) {
    datasetRows.value = []
    datasetTotal.value = 0
    datasetError.value = describeError(err)
  } finally {
    datasetLoading.value = false
  }
}
function openDataset() {
  datasetCreateError.value = ''
  datasetVisible.value = true
}
async function submitDataset() {
  datasetCreateError.value = ''
  if (!datasetForm.code.trim() || !datasetForm.name.trim() || !datasetForm.data_source_id.trim()) {
    datasetCreateError.value = '编码、名称与数据源 ID 为必填项'
    return
  }
  datasetSaving.value = true
  try {
    const created = await intelligenceApi.datasets.create({
      code: datasetForm.code.trim(),
      name: datasetForm.name.trim(),
      data_source_id: Number(datasetForm.data_source_id),
      mode: datasetForm.mode,
      sql_text: datasetForm.sql_text.trim() || undefined,
      refresh_cron: datasetForm.refresh_cron.trim() || undefined,
    })
    ElMessage.success(`数据集已创建：${created?.code ?? created?.id ?? ''}`)
    datasetVisible.value = false
    datasetForm.code = ''
    datasetForm.name = ''
    datasetForm.data_source_id = ''
    datasetForm.mode = 'DIRECT'
    datasetForm.sql_text = ''
    datasetForm.refresh_cron = ''
    datasetPage.value = 1
    await loadDatasets()
  } catch (err) {
    datasetCreateError.value = describeError(err)
  } finally {
    datasetSaving.value = false
  }
}
async function selectDataset(row: AiRecord) {
  selectedDataset.value = row
  fields.value = []
  fieldsError.value = ''
  fieldsLoading.value = true
  try {
    fields.value = await intelligenceApi.datasets.fields(Number(row.id))
  } catch (err) {
    fieldsError.value = describeError(err)
  } finally {
    fieldsLoading.value = false
  }
}
function openField() {
  fieldError.value = ''
  fieldForm.field_name = ''
  fieldForm.field_type = 'DIMENSION'
  fieldForm.data_type = 'STRING'
  fieldForm.expression = ''
  fieldForm.is_sensitive = false
  fieldVisible.value = true
}
async function submitField() {
  fieldError.value = ''
  if (!selectedDataset.value) {
    fieldError.value = '请先选择数据集'
    return
  }
  if (!fieldForm.field_name.trim()) {
    fieldError.value = '字段名称为必填项'
    return
  }
  fieldSaving.value = true
  try {
    await intelligenceApi.datasets.addField(Number(selectedDataset.value.id), {
      field_name: fieldForm.field_name.trim(),
      field_type: fieldForm.field_type,
      data_type: fieldForm.data_type,
      expression: fieldForm.expression.trim() || undefined,
      is_sensitive: fieldForm.is_sensitive,
    })
    ElMessage.success('字段已添加')
    fieldVisible.value = false
    await selectDataset(selectedDataset.value)
  } catch (err) {
    fieldError.value = describeError(err)
  } finally {
    fieldSaving.value = false
  }
}

onMounted(() => {
  loadMetrics()
  loadDatasets()
})
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="指标与数据集"
      description="指标口径、数据集定义与字段；数据来自 /api/intelligence/v1/metrics 与 /datasets。"
    >
      <template #actions>
        <el-button v-if="activeTab === 'metrics'" v-perm="'tenant:ai:bi:metric:manage'" type="primary" @click="openMetric">
          新建指标
        </el-button>
        <el-button v-else v-perm="'ai:bi:dataset:manage'" type="primary" @click="openDataset">新建数据集</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="指标" name="metrics">
          <el-alert v-if="metricError" type="error" :closable="false" show-icon :title="metricError" style="margin-bottom: 12px" />
          <div class="fl-toolbar" style="margin-bottom: 12px">
            <el-input v-model="keyword" placeholder="指标编码或名称" clearable style="width: 220px" @keyup.enter="metricPage = 1; loadMetrics()" />
            <el-button type="primary" :loading="metricLoading" @click="metricPage = 1; loadMetrics()">查询</el-button>
          </div>
          <el-table v-loading="metricLoading" :data="metricRows" border stripe max-height="500">
            <el-table-column prop="code" label="编码" min-width="140" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="metric_type" label="类型" width="110" />
            <el-table-column prop="formula" label="公式" min-width="200" show-overflow-tooltip />
            <el-table-column prop="unit" label="单位" width="90" />
            <el-table-column prop="grain" label="粒度" width="90" />
            <el-table-column prop="scope" label="范围" width="100" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="version_no" label="版本" width="80" />
            <template #empty><el-empty description="暂无指标" :image-size="60" /></template>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="metricTotal"
            :page-size="metricSize"
            :current-page="metricPage"
            @current-change="metricPage = $event; loadMetrics()"
          />
        </el-tab-pane>
        <el-tab-pane label="数据集" name="datasets">
          <el-alert v-if="datasetError" type="error" :closable="false" show-icon :title="datasetError" style="margin-bottom: 12px" />
          <el-table v-loading="datasetLoading" :data="datasetRows" border stripe max-height="360" @row-click="selectDataset">
            <el-table-column prop="id" label="ID" width="170" />
            <el-table-column prop="code" label="编码" min-width="140" />
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="data_source_id" label="数据源" width="170" />
            <el-table-column prop="mode" label="模式" width="100" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="created_at" label="创建时间" width="170" />
            <template #empty><el-empty description="暂无数据集" :image-size="60" /></template>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="datasetTotal"
            :page-size="datasetSize"
            :current-page="datasetPage"
            @current-change="datasetPage = $event; loadDatasets()"
          />
          <template v-if="selectedDataset">
            <el-divider content-position="left">
              字段：{{ selectedDataset.name ?? selectedDataset.code ?? selectedDataset.id }}
            </el-divider>
            <el-alert v-if="fieldsError" type="error" :closable="false" show-icon :title="fieldsError" style="margin-bottom: 12px" />
            <div class="fl-toolbar" style="margin-bottom: 12px">
              <el-button v-perm="'ai:bi:dataset:manage'" type="primary" size="small" @click="openField">新增字段</el-button>
            </div>
            <el-table v-loading="fieldsLoading" :data="fields" border size="small" max-height="300">
              <el-table-column prop="field_name" label="字段名" min-width="150" />
              <el-table-column prop="field_type" label="字段类型" width="120" />
              <el-table-column prop="data_type" label="数据类型" width="120" />
              <el-table-column prop="expression" label="表达式" min-width="180" show-overflow-tooltip />
              <el-table-column prop="is_sensitive" label="敏感" width="90" />
              <el-table-column prop="sort_no" label="排序" width="80" />
              <template #empty><el-empty description="暂无字段" :image-size="60" /></template>
            </el-table>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <el-dialog v-model="metricVisible" title="新建指标" width="600px">
      <el-alert v-if="metricCreateError" type="error" :closable="false" show-icon :title="metricCreateError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="metricForm.code" maxlength="64" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="metricForm.name" maxlength="128" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="metricForm.category" placeholder="选填，如 交易 / 用户" /></el-form-item>
        <el-form-item label="指标类型">
          <el-select v-model="metricForm.metric_type" style="width: 100%">
            <el-option label="原子指标（ATOMIC）" value="ATOMIC" />
            <el-option label="派生指标（DERIVED）" value="DERIVED" />
            <el-option label="复合指标（COMPOSITE）" value="COMPOSITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="口径公式" required>
          <el-input v-model="metricForm.formula" type="textarea" :rows="3" placeholder="如 SUM(amount)" class="fl-mono" />
        </el-form-item>
        <el-form-item label="时间粒度">
          <el-select v-model="metricForm.grain" style="width: 100%">
            <el-option v-for="grain in ['DAY', 'WEEK', 'MONTH', 'QUARTER', 'YEAR']" :key="grain" :label="grain" :value="grain" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位"><el-input v-model="metricForm.unit" placeholder="如 CNY / %" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metricVisible = false">取消</el-button>
        <el-button type="primary" :loading="metricSaving" @click="submitMetric">创建</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="datasetVisible" title="新建数据集" width="600px">
      <el-alert v-if="datasetCreateError" type="error" :closable="false" show-icon :title="datasetCreateError" style="margin-bottom: 12px" />
      <el-form label-width="120px">
        <el-form-item label="编码" required><el-input v-model="datasetForm.code" maxlength="64" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="datasetForm.name" maxlength="128" /></el-form-item>
        <el-form-item label="数据源 ID" required><el-input v-model="datasetForm.data_source_id" placeholder="ai_data_source 主键" /></el-form-item>
        <el-form-item label="模式">
          <el-select v-model="datasetForm.mode" style="width: 100%">
            <el-option label="直连（DIRECT）" value="DIRECT" />
            <el-option label="抽取（EXTRACT）" value="EXTRACT" />
            <el-option label="加速（ACCEL）" value="ACCEL" />
          </el-select>
        </el-form-item>
        <el-form-item label="SQL"><el-input v-model="datasetForm.sql_text" type="textarea" :rows="4" class="fl-mono" /></el-form-item>
        <el-form-item label="刷新 Cron"><el-input v-model="datasetForm.refresh_cron" placeholder="选填，如 0 0 2 * * ?" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetVisible = false">取消</el-button>
        <el-button type="primary" :loading="datasetSaving" @click="submitDataset">创建</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="fieldVisible" title="新增字段" width="520px">
      <el-alert v-if="fieldError" type="error" :closable="false" show-icon :title="fieldError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="字段名称" required><el-input v-model="fieldForm.field_name" maxlength="128" /></el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="fieldForm.field_type" style="width: 100%">
            <el-option label="维度（DIMENSION）" value="DIMENSION" />
            <el-option label="度量（MEASURE）" value="MEASURE" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select v-model="fieldForm.data_type" style="width: 100%">
            <el-option label="STRING" value="STRING" />
            <el-option label="NUMBER" value="NUMBER" />
            <el-option label="DATE" value="DATE" />
            <el-option label="BOOLEAN" value="BOOLEAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="表达式"><el-input v-model="fieldForm.expression" placeholder="选填，如 SUM(amount)" /></el-form-item>
        <el-form-item label="敏感字段"><el-switch v-model="fieldForm.is_sensitive" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldVisible = false">取消</el-button>
        <el-button type="primary" :loading="fieldSaving" @click="submitField">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>
