<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { rndApi, type RndContribution } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const contributions = ref<RndContribution[]>([])
const periodType = ref('month')
const periodKey = ref(new Date().toISOString().slice(0, 7))
const loading = ref(false)
const error = ref('')

const totals = computed(() => {
  const sum = contributions.value.reduce(
    (acc, item) => {
      acc.commits += Number(item.commit_count || 0)
      acc.effective += Number(item.effective_commits || 0)
      acc.add += Number(item.add_lines || 0)
      acc.del += Number(item.del_lines || 0)
      return acc
    },
    { commits: 0, effective: 0, add: 0, del: 0 },
  )
  return { ...sum, rate: sum.commits ? (sum.effective / sum.commits).toFixed(3) : '-' }
})

const gapItems = [
  '贡献度依赖已入库的提交数据，仓库未同步或提交未入库时返回空列表。',
  '缺少按周/自定义日期区间的贡献度查询，当前仅支持 month/week/quarter 周期类型。',
]

function value(value?: string | number) {
  return value === undefined || value === null || value === '' ? '-' : value
}

async function loadContributions() {
  loading.value = true
  error.value = ''
  try {
    contributions.value = await rndApi.metrics.myContributions({
      periodType: periodType.value || undefined,
      periodKey: periodKey.value || undefined,
      page: 1,
      size: 200,
    })
  } catch (err) {
    contributions.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(loadContributions)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="我的贡献"
      description="查看本人研发贡献指标；数据来自 /api/rnd/v1/metrics/contributions/my。"
    >
      <template #actions>
        <el-select v-model="periodType" style="width: 110px" @change="loadContributions">
          <el-option label="月" value="month" />
          <el-option label="周" value="week" />
          <el-option label="季" value="quarter" />
        </el-select>
        <el-input v-model="periodKey" placeholder="2026-11" style="width: 130px" />
        <el-button :loading="loading" @click="loadContributions">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header><span>我的贡献合计（{{ periodKey }}）</span></template>
      <el-descriptions v-if="contributions.length" :column="5" border>
        <el-descriptions-item label="提交数">{{ totals.commits }}</el-descriptions-item>
        <el-descriptions-item label="有效提交">{{ totals.effective }}</el-descriptions-item>
        <el-descriptions-item label="有效率">{{ totals.rate }}</el-descriptions-item>
        <el-descriptions-item label="新增行">{{ totals.add }}</el-descriptions-item>
        <el-descriptions-item label="删除行">{{ totals.del }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="当前周期暂无本人贡献数据" :image-size="70" />
    </el-card>

    <el-card shadow="never">
      <template #header><span>我的贡献明细</span></template>
      <el-table :data="contributions" v-loading="loading" border stripe size="small" max-height="420">
        <el-table-column prop="product_id" label="产品ID" width="180" />
        <el-table-column prop="period_type" label="周期类型" width="110" />
        <el-table-column prop="period_key" label="周期" width="110" />
        <el-table-column prop="commit_count" label="提交数" width="100" />
        <el-table-column prop="effective_commits" label="有效提交" width="110" />
        <el-table-column label="有效率" width="110">
          <template #default="{ row }">{{ value(row.effective_commit_rate) }}</template>
        </el-table-column>
        <el-table-column prop="add_lines" label="新增行" width="100" />
        <el-table-column prop="del_lines" label="删除行" width="100" />
        <el-table-column label="复杂度" min-width="110">
          <template #default="{ row }">{{ value(row.complexity_score) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
