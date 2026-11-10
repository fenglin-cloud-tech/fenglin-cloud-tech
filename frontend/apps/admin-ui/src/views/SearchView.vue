<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { BarChart } from '@fenglin/charts'
import { searchApi, type AnalyticsResponse, type IndexTaskView } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canManage = computed(() => auth.can('platform:search:index:manage'))
const canQuery = computed(() => auth.can('biz:search:query'))

const taskForm = reactive({ index_name: '', task_type: 'FULL' })
const taskCreating = ref(false)
const taskError = ref('')
const task = ref<IndexTaskView | null>(null)
const taskId = ref<string | undefined>(undefined)
const taskQuerying = ref(false)

const analytics = ref<AnalyticsResponse | null>(null)
const analyticsLoading = ref(false)
const analyticsError = ref('')
const analyticsForm = reactive({ type: 'HOT', from: '', to: '' })

const query = reactive({ q: '', domains: '', mode: 'BM25', page: 1, size: 20 })
const queryLoading = ref(false)
const queryError = ref('')
const queryResult = ref<unknown>(null)

const analyticsChart = computed(() =>
  (analytics.value?.items ?? []).slice(0, 10).map((item) => ({ label: item.keyword, value: item.count })),
)

async function createTask() {
  if (!taskForm.index_name) {
    taskError.value = '索引名称不能为空'
    return
  }
  taskCreating.value = true
  taskError.value = ''
  try {
    task.value = await searchApi.createIndexTask({ ...taskForm })
    taskId.value = task.value.task_id
    ElMessage.success('索引任务已创建')
  } catch (err) {
    taskError.value = describeError(err)
  } finally {
    taskCreating.value = false
  }
}

async function queryTask() {
  if (!taskId.value) return
  taskQuerying.value = true
  try {
    task.value = await searchApi.getIndexTask(taskId.value)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    taskQuerying.value = false
  }
}

async function loadAnalytics() {
  analyticsLoading.value = true
  analyticsError.value = ''
  try {
    analytics.value = await searchApi.analytics({
      type: analyticsForm.type,
      from: analyticsForm.from ? new Date(analyticsForm.from).toISOString() : undefined,
      to: analyticsForm.to ? new Date(analyticsForm.to).toISOString() : undefined,
    })
  } catch (err) {
    analyticsError.value = describeError(err)
  } finally {
    analyticsLoading.value = false
  }
}

async function runSearch() {
  if (!query.q) return
  queryLoading.value = true
  queryError.value = ''
  queryResult.value = null
  try {
    queryResult.value = await searchApi.query({
      query: query.q,
      domains: query.domains ? query.domains.split(',').map((item) => item.trim()).filter(Boolean) : undefined,
      mode: query.mode,
      page: query.page,
      size: query.size,
    })
  } catch (err) {
    queryError.value = describeError(err)
  } finally {
    queryLoading.value = false
  }
}

onMounted(loadAnalytics)
</script>

<template>
  <div>
    <PageHeader module="搜索中台 · A-22" title="搜索中心" description="索引任务、检索分析与检索试验；租户与密级过滤由后端强制注入。">
      <template #actions>
        <el-button @click="loadAnalytics">刷新分析</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        'HUB-API-903 仅定义索引任务创建，任务状态查询为契约缺口、由后端补充实现。',
        '后端未提供索引列表、删除、重建全量索引的查询接口，本页只能按任务 ID 跟踪。',
        'HUB-API-901 检索需 biz:search:query 权限；HUB-API-904 分析需 platform:search:index:manage 权限。',
        '当前权限：索引管理=' + (canManage ? '具备' : '缺失') + '，检索=' + (canQuery ? '具备' : '缺失') + '。ES/OpenSearch 索引引擎由后端适配，前端不直连。',
      ]"
    />

    <div class="fl-grid fl-grid--2" style="margin-bottom: 16px">
      <div class="fl-card">
        <div class="fl-section-title"><span>索引任务</span><span class="fl-muted">HUB-API-903/904</span></div>
        <el-alert v-if="taskError" type="error" :closable="false" show-icon :title="taskError" style="margin-bottom: 12px" />
        <el-form label-width="100px">
          <el-form-item label="索引名称" required><el-input v-model="taskForm.index_name" placeholder="例如 hub_document" /></el-form-item>
          <el-form-item label="任务类型">
            <el-select v-model="taskForm.task_type" style="width: 100%">
              <el-option label="FULL（不存在则建索引）" value="FULL" />
              <el-option label="INCREMENTAL（索引须已存在）" value="INCREMENTAL" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="taskCreating" @click="createTask">创建任务</el-button>
          </el-form-item>
        </el-form>
        <div class="fl-toolbar">
          <el-input-number v-model="taskId" :min="1" placeholder="任务 ID" />
          <el-button :loading="taskQuerying" @click="queryTask">查询状态</el-button>
        </div>
        <el-descriptions v-if="task" :column="1" border size="small">
          <el-descriptions-item label="任务 ID">{{ task.task_id }}</el-descriptions-item>
          <el-descriptions-item label="索引">{{ task.index_name }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ task.task_type }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ task.status }}</el-descriptions-item>
          <el-descriptions-item label="文档数">{{ task.doc_count ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误">{{ task.error_msg || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="fl-card">
        <div class="fl-section-title"><span>检索分析</span><span class="fl-muted">HUB-API-904</span></div>
        <div class="fl-toolbar">
          <el-select v-model="analyticsForm.type" style="width: 140px">
            <el-option label="HOT 热词" value="HOT" />
            <el-option label="NO_RESULT 无结果" value="NO_RESULT" />
            <el-option label="ZERO_CLICK 零点击" value="ZERO_CLICK" />
          </el-select>
          <el-date-picker v-model="analyticsForm.from" type="datetime" placeholder="开始" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 180px" />
          <el-date-picker v-model="analyticsForm.to" type="datetime" placeholder="结束" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 180px" />
          <el-button :loading="analyticsLoading" @click="loadAnalytics">查询</el-button>
        </div>
        <el-alert v-if="analyticsError" type="error" :closable="false" show-icon :title="analyticsError" style="margin-bottom: 12px" />
        <BarChart :data="analyticsChart" :height="200" empty-text="暂无分析数据或接口不可用" />
        <el-table v-if="analytics?.items?.length" :data="analytics.items" size="small" max-height="200" border>
          <el-table-column prop="keyword" label="关键词" />
          <el-table-column prop="count" label="次数" width="100" />
        </el-table>
      </div>
    </div>

    <div class="fl-card">
      <div class="fl-section-title"><span>检索试验</span><span class="fl-muted">HUB-API-901</span></div>
      <div class="fl-toolbar">
        <el-input v-model="query.q" placeholder="检索关键词" style="width: 260px" @keyup.enter="runSearch" />
        <el-input v-model="query.domains" placeholder="域，逗号分隔（可选）" style="width: 220px" />
        <el-select v-model="query.mode" style="width: 130px">
          <el-option label="BM25" value="BM25" />
          <el-option label="HYBRID" value="HYBRID" />
        </el-select>
        <el-button type="primary" :loading="queryLoading" @click="runSearch">检索</el-button>
      </div>
      <el-alert v-if="queryError" type="error" :closable="false" show-icon :title="queryError" style="margin-bottom: 12px" />
      <pre v-if="queryResult" class="fl-pre">{{ JSON.stringify(queryResult, null, 2) }}</pre>
      <el-empty v-else description="输入关键词后检索，结果按后端 SearchGroup 原样展示" :image-size="70" />
    </div>
  </div>
</template>
