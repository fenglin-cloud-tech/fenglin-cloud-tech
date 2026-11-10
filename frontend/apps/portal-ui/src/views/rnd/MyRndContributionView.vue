<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { rndApi, type RndContribution, type RndId, type RndProduct } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const products = ref<RndProduct[]>([])
const contributions = ref<RndContribution[]>([])
const selectedProductId = ref<RndId | ''>('')
const periodType = ref('MONTH')
const periodKey = ref(new Date().toISOString().slice(0, 7))
const loading = ref(false)
const error = ref('')

const myContribution = computed(() =>
  contributions.value.find((item) => String(item.user_id) === String(auth.userId)),
)

const gapItems = [
  '贡献度接口返回产品下全员数据，没有“仅本人”查询参数，本页在浏览器中定位当前用户记录。',
  '贡献度依赖已入库的提交数据，仓库未同步或提交未入库时返回空对象。',
  '缺少按周/自定义日期区间的贡献度查询，当前仅支持 MONTH/WEEK/QUARTER 周期类型。',
]

function value(value?: string | number) {
  return value === undefined || value === null || value === '' ? '-' : value
}

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
    await loadContributions()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadContributions() {
  if (!selectedProductId.value) {
    contributions.value = []
    return
  }
  const productId = selectedProductId.value
  loading.value = true
  error.value = ''
  try {
    contributions.value = await rndApi.metrics.contributions({
      productId,
      periodType: periodType.value || undefined,
      periodKey: periodKey.value || undefined,
    })
  } catch (err) {
    contributions.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(loadProducts)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="我的贡献"
      description="查看本人研发贡献指标；数据来自 /api/rnd/v1/metrics/contributions。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 200px" @change="loadContributions">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-select v-model="periodType" style="width: 110px">
          <el-option label="月" value="MONTH" />
          <el-option label="周" value="WEEK" />
          <el-option label="季" value="QUARTER" />
        </el-select>
        <el-input v-model="periodKey" placeholder="2026-11" style="width: 130px" />
        <el-button :loading="loading" @click="loadContributions">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header><span>我的贡献（{{ periodKey }}）</span></template>
      <el-descriptions v-if="myContribution" :column="4" border>
        <el-descriptions-item label="提交数">{{ value(myContribution.commit_count) }}</el-descriptions-item>
        <el-descriptions-item label="有效提交">{{ value(myContribution.effective_commits) }}</el-descriptions-item>
        <el-descriptions-item label="有效率">{{ value(myContribution.effective_commit_rate) }}</el-descriptions-item>
        <el-descriptions-item label="复杂度">{{ value(myContribution.complexity_score) }}</el-descriptions-item>
        <el-descriptions-item label="新增行">{{ value(myContribution.add_lines) }}</el-descriptions-item>
        <el-descriptions-item label="删除行">{{ value(myContribution.del_lines) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="当前周期暂无本人贡献数据" :image-size="70" />
    </el-card>

    <el-card shadow="never">
      <template #header><span>产品贡献排行</span></template>
      <el-table :data="contributions" v-loading="loading" border stripe size="small" max-height="420">
        <el-table-column prop="user_id" label="用户ID" width="110">
          <template #default="{ row }">
            <span :class="{ 'fl-mine': String(row.user_id) === String(auth.userId) }">{{ row.user_id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="commit_count" label="提交数" width="100" />
        <el-table-column prop="effective_commits" label="有效提交" width="110" />
        <el-table-column prop="effective_commit_rate" label="有效率" width="110" />
        <el-table-column prop="add_lines" label="新增行" width="100" />
        <el-table-column prop="del_lines" label="删除行" width="100" />
        <el-table-column prop="complexity_score" label="复杂度" width="110" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.fl-mine {
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
