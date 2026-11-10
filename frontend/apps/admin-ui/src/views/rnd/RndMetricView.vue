<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  rndApi,
  type RndAlert,
  type RndContribution,
  type RndDora,
  type RndId,
  type RndInsight,
  type RndMetricConfig,
  type RndMetricValue,
  type RndProduct,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const metrics = ref<RndMetricValue[]>([])
const contributions = ref<RndContribution[]>([])
const dora = ref<RndDora | null>(null)
const insight = ref<RndInsight | null>(null)
const configs = ref<RndMetricConfig[]>([])
const alerts = ref<RndAlert[]>([])
const selectedProductId = ref<RndId | ''>('')
const periodType = ref('MONTH')
const periodKey = ref(new Date().toISOString().slice(0, 7))
const activeTab = ref('metrics')
const loading = ref(false)
const error = ref('')

const alertVisible = ref(false)
const alertSaving = ref(false)
const alertError = ref('')
const alertForm = reactive({ metric_code: '', scope_type: 'PRODUCT', threshold: '', compare_op: 'GT', notify_channel: 'INBOX' })

const gapItems = [
  '度量口径与数据质量依赖已入库的提交/任务/缺陷/发布数据，缺少数据时后端返回空或 0，本页不补默认值。',
  '度量配置与预警需要 tenant:devops:metric:manage / tenant:devops:alert:manage，普通研发账号会收到真实 403。',
  'DORA 等级由后端按阈值计算，前端只展示 level 与四项指标，不重新计算。',
]

const doraLevelType: Record<string, string> = {
  ELITE: 'success',
  HIGH: 'success',
  MEDIUM: 'warning',
  LOW: 'danger',
}

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
    await loadMetrics()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadMetrics() {
  if (!selectedProductId.value) return
  loading.value = true
  error.value = ''
  const params = {
    productId: selectedProductId.value,
    periodType: periodType.value || undefined,
    periodKey: periodKey.value || undefined,
  }
  try {
    const [metricList, contributionList, doraView, insightView] = await Promise.all([
      rndApi.metrics.snapshots(params),
      rndApi.metrics.contributions(params).catch(() => [] as RndContribution[]),
      rndApi.metrics.dora(params).catch(() => null),
      rndApi.metrics.insight(params).catch(() => null),
    ])
    metrics.value = metricList
    contributions.value = contributionList
    dora.value = doraView
    insight.value = insightView
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadConfigs() {
  try {
    const [configList, alertList] = await Promise.all([
      rndApi.metrics.configs().catch(() => [] as RndMetricConfig[]),
      rndApi.metrics.alerts().catch(() => [] as RndAlert[]),
    ])
    configs.value = configList
    alerts.value = alertList
  } catch (err) {
    error.value = describeError(err)
  }
}

async function compute() {
  if (!selectedProductId.value) {
    ElMessage.warning('请选择产品')
    return
  }
  try {
    const result = await rndApi.metrics.compute({
      product_id: selectedProductId.value,
      period_type: periodType.value || undefined,
      period_key: periodKey.value || undefined,
    })
    ElMessage.success(`已重算 ${result.metrics.length} 项指标${result.dora_level ? `，DORA ${result.dora_level}` : ''}`)
    await loadMetrics()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function evaluateAlerts() {
  if (!selectedProductId.value) {
    ElMessage.warning('请选择产品')
    return
  }
  try {
    const triggered = await rndApi.metrics.evaluateAlerts({
      productId: selectedProductId.value,
      periodType: periodType.value || undefined,
      periodKey: periodKey.value || undefined,
    })
    ElMessage.success(`预警评估完成，触发 ${triggered.length} 条`)
    alerts.value = await rndApi.metrics.alerts().catch(() => alerts.value)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitAlert() {
  if (!alertForm.metric_code.trim() || !alertForm.threshold) {
    alertError.value = '请填写指标编码与阈值'
    return
  }
  alertSaving.value = true
  alertError.value = ''
  try {
    await rndApi.metrics.createAlert({
      metric_code: alertForm.metric_code.trim(),
      scope_type: alertForm.scope_type,
      scope_id: selectedProductId.value ? selectedProductId.value : undefined,
      threshold: alertForm.threshold,
      compare_op: alertForm.compare_op,
      notify_channel: alertForm.notify_channel,
    })
    ElMessage.success('预警规则已创建')
    alertVisible.value = false
    await loadConfigs()
  } catch (err) {
    alertError.value = describeError(err)
  } finally {
    alertSaving.value = false
  }
}

async function resolveAlert(row: RndAlert) {
  try {
    await rndApi.metrics.resolveAlert(row.id)
    ElMessage.success('预警已解除')
    await loadConfigs()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(async () => {
  await loadProducts()
  await loadConfigs()
})
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="效能度量"
      description="研发指标、DORA、贡献度、度量配置与预警；数据来自 /api/rnd/v1/metrics。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 200px" @change="loadMetrics">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-select v-model="periodType" style="width: 110px">
          <el-option label="月" value="MONTH" />
          <el-option label="周" value="WEEK" />
          <el-option label="季" value="QUARTER" />
        </el-select>
        <el-input v-model="periodKey" placeholder="2026-11" style="width: 130px" />
        <el-button v-perm="'biz:devops:metric:view'" type="primary" @click="compute">重算指标</el-button>
        <el-button v-perm="'tenant:devops:alert:manage'" plain @click="alertVisible = true">新增预警</el-button>
        <el-button :loading="loading" @click="loadMetrics">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="fl-muted">DORA 等级</div>
          <el-tag v-if="dora?.level" :type="doraLevelType[dora.level] ?? 'info'" size="large">{{ dora.level }}</el-tag>
          <span v-else class="fl-muted">暂无</span>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="fl-muted">部署频率</div>
          <strong>{{ dora?.deploy_freq ?? '-' }}</strong>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="fl-muted">变更前置时间(小时)</div>
          <strong>{{ dora?.lead_time_hours ?? '-' }}</strong>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="fl-muted">变更失败率 / MTTR</div>
          <strong>{{ dora?.change_failure_rate ?? '-' }} / {{ dora?.mttr_hours ?? '-' }}</strong>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="指标快照" name="metrics">
          <el-table :data="metrics" border stripe size="small" max-height="420">
            <el-table-column prop="metric_code" label="指标编码" width="180" />
            <el-table-column prop="metric_name" label="名称" min-width="160" />
            <el-table-column prop="metric_group" label="分组" width="120" />
            <el-table-column prop="metric_value" label="值" width="120" />
            <el-table-column prop="unit" label="单位" width="90" />
            <el-table-column prop="data_quality" label="数据质量" width="110" />
            <el-table-column prop="caliber_version" label="口径版本" width="120" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="贡献度" name="contributions">
          <el-table :data="contributions" border stripe size="small" max-height="420">
            <el-table-column prop="user_id" label="用户ID" width="110" />
            <el-table-column prop="commit_count" label="提交数" width="100" />
            <el-table-column prop="effective_commits" label="有效提交" width="110" />
            <el-table-column prop="effective_commit_rate" label="有效率" width="110" />
            <el-table-column prop="add_lines" label="新增行" width="100" />
            <el-table-column prop="del_lines" label="删除行" width="100" />
            <el-table-column prop="complexity_score" label="复杂度" width="110" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="洞察" name="insight">
          <el-descriptions v-if="insight" :column="3" border>
            <el-descriptions-item label="周期">{{ insight.period_key }}</el-descriptions-item>
            <el-descriptions-item label="需求数">{{ insight.requirement_count ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="缺陷数">{{ insight.defect_count ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="提交数">{{ insight.commit_count ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="发布数">{{ insight.release_count ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="数据质量">{{ insight.data_quality ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="暂无洞察数据" :image-size="70" />
        </el-tab-pane>

        <el-tab-pane label="度量配置" name="configs">
          <el-table :data="configs" border stripe size="small" max-height="420">
            <el-table-column prop="metric_code" label="指标编码" width="170" />
            <el-table-column prop="metric_name" label="名称" min-width="150" />
            <el-table-column prop="metric_group" label="分组" width="110" />
            <el-table-column prop="formula" label="公式" min-width="200" />
            <el-table-column prop="weight" label="权重" width="90" />
            <el-table-column prop="target_value" label="目标值" width="100" />
            <el-table-column prop="status" label="状态" width="90" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="预警" name="alerts">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:devops:alert:manage'" type="primary" plain @click="evaluateAlerts">执行预警评估</el-button>
          </div>
          <el-table :data="alerts" border stripe size="small" max-height="420">
            <el-table-column prop="metric_code" label="指标" width="170" />
            <el-table-column prop="scope_type" label="范围" width="110" />
            <el-table-column prop="threshold" label="阈值" width="100" />
            <el-table-column prop="compare_op" label="比较" width="90" />
            <el-table-column prop="triggered_value" label="触发值" width="110" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="triggered_at" label="触发时间" width="170" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'RESOLVED'" v-perm="'tenant:devops:alert:manage'" link type="primary" @click="resolveAlert(row)">解除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="alertVisible" title="新增预警规则" width="480px">
      <el-alert v-if="alertError" type="error" :closable="false" show-icon :title="alertError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="指标编码" required><el-input v-model="alertForm.metric_code" placeholder="如 commit_count" /></el-form-item>
        <el-form-item label="范围">
          <el-select v-model="alertForm.scope_type" style="width: 100%">
            <el-option label="产品" value="PRODUCT" />
            <el-option label="租户" value="TENANT" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值" required><el-input v-model="alertForm.threshold" /></el-form-item>
        <el-form-item label="比较">
          <el-select v-model="alertForm.compare_op" style="width: 100%">
            <el-option label="大于" value="GT" />
            <el-option label="小于" value="LT" />
            <el-option label="大于等于" value="GTE" />
            <el-option label="小于等于" value="LTE" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-select v-model="alertForm.notify_channel" style="width: 100%">
            <el-option label="站内信" value="INBOX" />
            <el-option label="邮件" value="EMAIL" />
            <el-option label="IM" value="IM" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="alertVisible = false">取消</el-button>
        <el-button type="primary" :loading="alertSaving" @click="submitAlert">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
