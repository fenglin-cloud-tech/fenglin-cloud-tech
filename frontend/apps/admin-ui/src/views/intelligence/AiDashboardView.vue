<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<AiRecord[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({ code: '', name: '', description: '', layoutText: '' })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<AiRecord | null>(null)

const chartVisible = ref(false)
const chartSaving = ref(false)
const chartError = ref('')
const chartForm = reactive({
  name: '',
  chart_type: 'LINE',
  dataset_id: '',
  dimension: '',
  optionsText: '',
})

const charts = computed<AiRecord[]>(() => {
  const list = detail.value?.charts
  return Array.isArray(list) ? list : []
})

const gapItems = [
  '没有图表删除、排序接口，本页只能追加图表并查看详情。',
  'layout 由后端透传 JSON 存储，前端不做结构化编排。',
  '看板与详情要求 ai:bi:view；新建看板要求 ai:bi:dashboard:manage，新建图表要求 ai:bi:chart:manage。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await intelligenceApi.dashboards.list({ page: page.value, size: size.value })
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
  form.layoutText = ''
  createVisible.value = true
}

function parseObject(text: string, label: string): AiRecord | undefined {
  const value = text.trim()
  if (!value) return undefined
  const parsed = JSON.parse(value) as unknown
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${label} 必须是 JSON 对象`)
  }
  return parsed as AiRecord
}

async function submitCreate() {
  createError.value = ''
  if (!form.code.trim() || !form.name.trim()) {
    createError.value = '请填写看板编码与名称'
    return
  }
  let layout: AiRecord | undefined
  try {
    layout = parseObject(form.layoutText, 'layout')
  } catch (err) {
    createError.value = (err as Error).message
    return
  }
  createSaving.value = true
  try {
    const created = await intelligenceApi.dashboards.create({
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      layout,
    })
    ElMessage.success(`看板已创建：${created?.name ?? created?.id ?? ''}`)
    createVisible.value = false
    page.value = 1
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function openDetail(row: AiRecord) {
  detailVisible.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await intelligenceApi.dashboards.detail(Number(row.id))
  } catch (err) {
    detailError.value = describeError(err)
  } finally {
    detailLoading.value = false
  }
}

function openChart() {
  chartError.value = ''
  chartForm.name = ''
  chartForm.chart_type = 'LINE'
  chartForm.dataset_id = ''
  chartForm.dimension = ''
  chartForm.optionsText = ''
  chartVisible.value = true
}

async function submitChart() {
  chartError.value = ''
  if (!detail.value) {
    chartError.value = '缺少看板信息'
    return
  }
  if (!chartForm.name.trim() || !chartForm.dataset_id.trim()) {
    chartError.value = '请填写图表名称与数据集 ID'
    return
  }
  let options: AiRecord | undefined
  try {
    options = parseObject(chartForm.optionsText, 'options')
  } catch (err) {
    chartError.value = (err as Error).message
    return
  }
  chartSaving.value = true
  try {
    await intelligenceApi.dashboards.createChart(Number(detail.value.id), {
      name: chartForm.name.trim(),
      chart_type: chartForm.chart_type,
      dataset_id: Number(chartForm.dataset_id),
      dimension: chartForm.dimension.trim() || undefined,
      options,
    })
    ElMessage.success('图表已创建')
    chartVisible.value = false
    detail.value = await intelligenceApi.dashboards.detail(Number(detail.value.id))
  } catch (err) {
    chartError.value = describeError(err)
  } finally {
    chartSaving.value = false
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="看板与图表"
      description="看板列表、布局透传与图表追加；数据来自 /api/intelligence/v1/dashboards。"
    >
      <template #actions>
        <el-button v-perm="'ai:bi:dashboard:manage'" type="primary" @click="openCreate">新建看板</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe max-height="580" @row-click="openDetail">
        <el-table-column prop="id" label="ID" width="170" />
        <el-table-column prop="code" label="编码" min-width="150" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="theme" label="主题" width="100" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="share_type" label="共享范围" width="120" />
        <el-table-column label="移动端" width="100">
          <template #default="{ row }">{{ row.mobile_enabled ? '启用' : '关闭' }}</template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无看板" :image-size="60" />
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

    <el-drawer v-model="detailVisible" title="看板详情" size="680px">
      <el-alert v-if="detailError" type="error" :closable="false" show-icon :title="detailError" style="margin-bottom: 12px" />
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="编码">{{ detail.code ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ detail.name ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="主题">{{ detail.theme ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="共享范围">{{ detail.share_type ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="移动端">{{ detail.mobile_enabled ? '启用' : '关闭' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail.created_at ?? '-' }}</el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">图表</el-divider>
          <div class="fl-toolbar" style="margin-bottom: 12px">
            <el-button v-perm="'ai:bi:chart:manage'" type="primary" size="small" @click="openChart">新建图表</el-button>
          </div>
          <el-table :data="charts" border size="small" max-height="300">
            <el-table-column prop="id" label="ID" width="170" />
            <el-table-column prop="name" label="标题" min-width="160" />
            <el-table-column prop="chart_type" label="类型" width="100" />
            <el-table-column prop="dataset_id" label="数据集" width="170" />
            <el-table-column prop="created_at" label="创建时间" width="170" />
            <template #empty>
              <el-empty description="暂无图表" :image-size="60" />
            </template>
          </el-table>

          <el-divider content-position="left">布局 JSON</el-divider>
          <pre class="fl-mono layout-json">{{ JSON.stringify(detail.layout ?? {}, null, 2) }}</pre>
        </template>
        <el-empty v-else-if="!detailLoading" description="暂无详情" :image-size="60" />
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" title="新建看板" width="560px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="form.code" maxlength="64" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="布局 JSON">
          <el-input
            v-model="form.layoutText"
            type="textarea"
            :rows="4"
            spellcheck="false"
            class="fl-mono"
            placeholder='选填，JSON 对象，如 {"cols":12}'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chartVisible" title="新建图表" width="580px">
      <el-alert v-if="chartError" type="error" :closable="false" show-icon :title="chartError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="名称" required><el-input v-model="chartForm.name" maxlength="128" /></el-form-item>
        <el-form-item label="图表类型">
          <el-select v-model="chartForm.chart_type" style="width: 100%">
            <el-option label="折线图（LINE）" value="LINE" />
            <el-option label="柱状图（BAR）" value="BAR" />
            <el-option label="饼图（PIE）" value="PIE" />
            <el-option label="表格（TABLE）" value="TABLE" />
            <el-option label="数值卡（NUMBER）" value="NUMBER" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据集 ID" required><el-input v-model="chartForm.dataset_id" placeholder="ai_dataset 主键" /></el-form-item>
        <el-form-item label="维度"><el-input v-model="chartForm.dimension" placeholder="选填，如 channel" /></el-form-item>
        <el-form-item label="选项 JSON">
          <el-input
            v-model="chartForm.optionsText"
            type="textarea"
            :rows="4"
            spellcheck="false"
            class="fl-mono"
            placeholder="选填，JSON 对象"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chartVisible = false">取消</el-button>
        <el-button type="primary" :loading="chartSaving" @click="submitChart">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.layout-json {
  margin: 0;
  padding: 12px;
  max-height: 240px;
  overflow: auto;
  background: var(--fl-color-fill-light, #f5f7fa);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
