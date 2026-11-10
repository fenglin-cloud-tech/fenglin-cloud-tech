<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canBiView = computed(() => auth.can('ai:bi:view'))
const canAnalytics = computed(() => auth.can('tenant:ai:search:analytics'))

const overview = ref<AiRecord | null>(null)
const overviewLoading = ref(false)
const error = ref('')

const analytics = ref<AiRecord | null>(null)
const analyticsLoading = ref(false)
const analyticsError = ref('')
const limit = ref(10)

const stats: Array<{ key: string; label: string }> = [
  { key: 'knowledge_bases', label: '知识库' },
  { key: 'data_sources', label: '数据源' },
  { key: 'chunks', label: '文档分块' },
  { key: 'datasets', label: '数据集' },
  { key: 'dashboards', label: '看板' },
  { key: 'insight_tasks', label: '洞察任务' },
]

const topQueries = computed<AiRecord[]>(() => {
  const list = analytics.value?.top_queries
  return Array.isArray(list) ? list : []
})

const zeroRate = computed(() => {
  const totalCount = Number(analytics.value?.total ?? 0)
  const zeroCount = Number(analytics.value?.zero_result ?? 0)
  if (totalCount <= 0) return 0
  return Math.round((zeroCount / totalCount) * 1000) / 10
})

const gapItems = [
  '概览与检索分析均为租户级聚合，接口没有时间区间参数，本页无法按日期筛选。',
  '提示词、模型供应商、监控告警等页面在 intelligence-service 无对应接口，由平台 AI 中台托管，本页不提供入口。',
  '检索分析需要 tenant:ai:search:analytics；无权限时该区块不发起请求，仅展示提示。',
]

function statValue(key: string): number {
  const value = overview.value?.[key]
  return value === undefined || value === null ? 0 : Number(value)
}

async function loadOverview() {
  overviewLoading.value = true
  error.value = ''
  try {
    overview.value = await intelligenceApi.overview()
  } catch (err) {
    overview.value = null
    error.value = describeError(err)
  } finally {
    overviewLoading.value = false
  }
}

async function loadAnalytics() {
  if (!canAnalytics.value) return
  analyticsLoading.value = true
  analyticsError.value = ''
  try {
    analytics.value = await intelligenceApi.searchAnalytics(limit.value)
  } catch (err) {
    analytics.value = null
    analyticsError.value = describeError(err)
  } finally {
    analyticsLoading.value = false
  }
}

async function refresh() {
  await Promise.all([loadOverview(), loadAnalytics()])
}

onMounted(refresh)
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="智能运营概览"
      description="知识库、数据源、数据集、看板与洞察任务规模，以及检索质量分析；数据来自 /api/intelligence/v1。"
    >
      <template #actions>
        <el-button :loading="overviewLoading || analyticsLoading" @click="refresh">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="!canBiView" type="warning" :closable="false" show-icon title="当前账号缺少 ai:bi:view，概览接口会返回真实 403。" style="margin-bottom: 12px" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card v-loading="overviewLoading" shadow="never" style="margin-bottom: 12px">
      <template #header>知识资产总览</template>
      <el-row :gutter="16">
        <el-col v-for="item in stats" :key="item.key" :xs="12" :sm="8" :md="4">
          <el-statistic :title="item.label" :value="statValue(item.key)" />
        </el-col>
      </el-row>
      <el-empty v-if="!overview && !overviewLoading" description="暂无概览数据" :image-size="60" />
      <div class="fl-muted" style="margin-top: 8px">
        数据来源：/api/intelligence/v1/report/overview；返回 0 表示租户内暂无对应资产。
      </div>
    </el-card>

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>统计口径</template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="knowledge_bases">租户内未删除的知识库数量</el-descriptions-item>
        <el-descriptions-item label="data_sources">租户内未删除的数据源数量</el-descriptions-item>
        <el-descriptions-item label="chunks">文档分块总数</el-descriptions-item>
        <el-descriptions-item label="datasets">BI 数据集数量</el-descriptions-item>
        <el-descriptions-item label="dashboards">BI 看板数量</el-descriptions-item>
        <el-descriptions-item label="insight_tasks">自动洞察任务数量</el-descriptions-item>
      </el-descriptions>
      <div class="fl-muted" style="margin-top: 8px">
        接口：GET /api/intelligence/v1/report/overview，要求 ai:bi:view；返回值为租户级聚合。
      </div>
    </el-card>

    <el-card v-if="canAnalytics" v-loading="analyticsLoading" shadow="never">
      <template #header>
        <div class="fl-toolbar">
          <span>检索分析</span>
          <div class="fl-toolbar">
            <el-select v-model="limit" style="width: 110px" @change="loadAnalytics">
              <el-option :value="10" label="Top 10" />
              <el-option :value="20" label="Top 20" />
              <el-option :value="50" label="Top 50" />
            </el-select>
            <el-button :loading="analyticsLoading" @click="loadAnalytics">查询</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="analyticsError" type="error" :closable="false" show-icon :title="analyticsError" style="margin-bottom: 12px" />

      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="接口">GET /api/intelligence/v1/admin/search/analytics</el-descriptions-item>
        <el-descriptions-item label="权限">tenant:ai:search:analytics</el-descriptions-item>
        <el-descriptions-item label="统计口径">租户级 ai_search_log 聚合，无时间区间</el-descriptions-item>
        <el-descriptions-item label="Top N">{{ limit }}</el-descriptions-item>
      </el-descriptions>

      <el-row :gutter="16">
        <el-col :xs="24" :sm="8">
          <el-statistic title="检索总次数" :value="Number(analytics?.total ?? 0)" />
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-statistic title="平均延迟 (ms)" :value="Number(analytics?.avg_latency_ms ?? 0)" />
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-statistic title="零结果次数" :value="Number(analytics?.zero_result ?? 0)" />
        </el-col>
      </el-row>

      <el-progress
        :percentage="zeroRate"
        :status="zeroRate > 30 ? 'warning' : 'success'"
        :stroke-width="14"
        style="margin-top: 16px"
      >
        <span>零结果占比 {{ zeroRate }}%</span>
      </el-progress>

      <el-divider content-position="left">高频查询</el-divider>
      <el-table :data="topQueries" border stripe size="small" max-height="420">
        <el-table-column label="查询词" min-width="260">
          <template #default="{ row }">{{ row?.query ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="次数" width="120">
          <template #default="{ row }">{{ row?.total ?? 0 }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无检索记录" :image-size="60" />
        </template>
      </el-table>
      <div class="fl-muted" style="margin-top: 8px">
        top_queries 取后端返回的 query 与 total 字段；缺失字段按空值展示，不做本地补数。
      </div>
    </el-card>

    <el-alert
      v-else
      type="info"
      :closable="false"
      show-icon
      title="当前账号缺少 tenant:ai:search:analytics，检索分析区块未发起请求。"
    />
  </div>
</template>
