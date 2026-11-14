<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GapNotice, PageHeader, formatDateTime } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canView = auth.can('ai:bi:view')

const dashboards = ref<AiRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const error = ref('')

const activeId = ref<string | number | null>(null)
const detail = ref<AiRecord | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const gapItems = [
  '平台未提供图表渲染数据 / 查询执行接口，本页只能展示图表元数据（名称、类型、数据集与查询配置）。',
  '图表类型仅为字符串（如 TABLE、LINE），没有枚举约束与样式预览接口。',
  '页面只读：新建或编辑看板需要 ai:bi:dashboard:manage 等管理权限，员工端不提供入口。',
]

const charts = computed<AiRecord[]>(() => {
  const value = detail.value?.charts
  return Array.isArray(value) ? value : []
})

const activeDashboard = computed(() => dashboards.value.find((item) => item.id === activeId.value))

function layoutText(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

function queryText(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

async function load() {
  if (!canView) return
  loading.value = true
  error.value = ''
  try {
    const result = await intelligenceApi.dashboards.list({ page: page.value, size: size.value })
    dashboards.value = result.records
    total.value = result.total
    if (activeId.value && !result.records.some((item) => item.id === activeId.value)) {
      activeId.value = null
      detail.value = null
    }
    if (!activeId.value && result.records.length > 0) {
      await openDashboard(result.records[0])
    }
  } catch (err) {
    error.value = describeError(err)
    dashboards.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function openDashboard(row: AiRecord) {
  const id = row.id
  activeId.value = id
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    detail.value = await intelligenceApi.dashboards.detail(id)
  } catch (err) {
    detailError.value = describeError(err)
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="丰麟云智"
      title="数据看板"
      description="查看看板元数据与图表配置；数据来自 /dashboards 与 /dashboards/{id}。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="!canView" type="info" :closable="false" show-icon
      title="缺少权限 ai:bi:view，无法查看看板。" style="margin-bottom: 12px" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12">
      <el-col :span="9">
        <el-card shadow="never" class="dashboard-panel">
          <template #header>
            <div class="card-head">
              <span>看板列表</span>
              <span class="fl-muted">共 {{ total }} 个</span>
            </div>
          </template>
          <div v-loading="loading">
            <div
              v-for="item in dashboards"
              :key="String(item.id)"
              class="dashboard-item"
              :class="{ 'dashboard-item--active': item.id === activeId }"
              @click="openDashboard(item)"
            >
              <div class="dashboard-item__main">
                <span class="dashboard-item__title">{{ item.name || '未命名看板' }}</span>
                <span class="fl-muted">{{ item.code || '-' }} · {{ item.theme || '-' }}</span>
              </div>
              <el-tag size="small" type="info">{{ item.status || '-' }}</el-tag>
            </div>
            <el-empty v-if="dashboards.length === 0" description="暂无看板" :image-size="70" />
          </div>
          <el-pagination
            style="margin-top: 8px; justify-content: flex-end"
            layout="prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="(next: number) => { page = next; load() }"
          />
        </el-card>
      </el-col>

      <el-col :span="15">
        <el-card shadow="never" class="dashboard-panel">
          <template #header>
            <div class="card-head">
              <span>{{ activeDashboard?.name || '看板详情' }}</span>
              <span class="fl-muted">共 {{ charts.length }} 个图表</span>
            </div>
          </template>

          <el-alert v-if="detailError" type="error" :closable="false" show-icon :title="detailError" />

          <div v-loading="detailLoading">
            <template v-if="detail">
              <el-descriptions :column="2" border size="small" style="margin-bottom: 8px">
                <el-descriptions-item label="编码">{{ detail.code || '-' }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ detail.status || '-' }}</el-descriptions-item>
                <el-descriptions-item label="主题">{{ detail.theme || '-' }}</el-descriptions-item>
                <el-descriptions-item label="共享范围">{{ detail.share_type || '-' }}</el-descriptions-item>
                <el-descriptions-item label="移动端">{{ detail.mobile_enabled ? '启用' : '未启用' }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ formatDateTime(detail.created_at) }}</el-descriptions-item>
                <el-descriptions-item label="布局" :span="2">{{ layoutText(detail.layout) }}</el-descriptions-item>
              </el-descriptions>

              <el-divider content-position="left">图表元数据</el-divider>
              <el-table :data="charts" border stripe size="small">
                <el-table-column prop="name" label="图表名称" min-width="160" />
                <el-table-column prop="chart_type" label="类型" width="110" />
                <el-table-column prop="dataset_id" label="数据集ID" width="120" />
                <el-table-column label="查询配置" min-width="200">
                  <template #default="{ row }">
                    <span class="query-text">{{ queryText(row.query) }}</span>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="charts.length === 0" description="该看板暂无图表" :image-size="70" />
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="图表仅展示元数据，平台未提供图表数据查询与渲染接口。"
                style="margin-top: 8px"
              />
            </template>
            <el-empty v-else-if="!detailLoading && !detailError" description="从左侧选择一个看板" :image-size="70" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard-panel {
  min-height: 480px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.dashboard-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
}
.dashboard-item:hover {
  background: var(--el-fill-color-light);
}
.dashboard-item--active {
  background: var(--el-color-primary-light-9);
}
.dashboard-item__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.dashboard-item__title {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.query-text {
  font-size: 12px;
  color: var(--fl-color-text-secondary);
  word-break: break-all;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
