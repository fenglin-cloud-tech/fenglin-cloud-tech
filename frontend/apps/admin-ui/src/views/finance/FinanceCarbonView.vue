<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  financeApi,
  type FinanceCarbonActivity,
  type FinanceCarbonCalculateResult,
  type FinanceCarbonTarget,
  type FinanceEsgIndicator,
  type FinanceEsgReport,
  type FinanceFactor,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const activeTab = ref('activities')
const loading = ref(false)
const error = ref('')

const factors = ref<FinanceFactor[]>([])
const activities = ref<FinanceCarbonActivity[]>([])
const targets = ref<FinanceCarbonTarget[]>([])
const indicators = ref<FinanceEsgIndicator[]>([])
const reports = ref<FinanceEsgReport[]>([])
const calcResult = ref<FinanceCarbonCalculateResult | null>(null)

const activityQuery = reactive({ periodCode: '', scope: '', status: '' })

const factorVisible = ref(false)
const factorError = ref('')
const factorForm = reactive({
  factor_code: '',
  factor_name: '',
  scope: 'SCOPE1',
  activity_type: 'FUEL',
  activity_unit: 'L',
  factor_value: '',
  factor_unit: 'kgCO2e/L',
  factor_version: 'v1',
  source: '',
})

const activityVisible = ref(false)
const activityError = ref('')
const activityForm = reactive({
  scope: 'SCOPE1',
  activity_type: 'FUEL',
  activity_date: new Date().toISOString().slice(0, 10),
  amount: '',
  unit: 'L',
  factor_id: '' as number | '',
  data_source: 'MANUAL',
})

const calcForm = reactive({ period_code: '', scope: '', recalc: false })

const targetVisible = ref(false)
const targetError = ref('')
const targetForm = reactive({
  target_name: '',
  target_year: new Date().getFullYear() + 1,
  base_year: new Date().getFullYear(),
  base_emission: '',
  target_emission: '',
})

const indicatorVisible = ref(false)
const indicatorError = ref('')
const indicatorForm = reactive({
  indicator_code: '',
  indicator_name: '',
  category: 'ENV',
  unit: 'tCO2e',
  period_year: new Date().getFullYear(),
  target_value: '',
  actual_value: '',
  data_source: 'CARBON',
})

const reportVisible = ref(false)
const reportError = ref('')
const reportForm = reactive({
  report_name: '',
  period_year: new Date().getFullYear(),
  report_type: 'ANNUAL',
  report_boundary: '运营控制权',
})

const scopeLabels: Record<string, string> = { SCOPE1: '范围一', SCOPE2: '范围二', SCOPE3: '范围三' }
const activityStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待核验',
  VERIFIED: '已核验',
  LOCKED: '已锁定',
}

const gapItems = [
  '碳核算只返回批次汇总（总量/分范围），没有排放趋势、排放源排名等分析接口。',
  'ESG 报告只有列表、创建、发布与文本导出，没有报告章节/指标快照明细接口。',
  '碳活动没有“我的活动”过滤参数，员工端只能按租户/状态查看后在前端筛选。',
]

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

function scopeLabel(value?: string) {
  return value ? scopeLabels[value] ?? value : '-'
}

function activityStatusLabel(value?: string) {
  return value ? activityStatusLabels[value] ?? value : '-'
}

async function loadFactors() {
  try {
    factors.value = await financeApi.carbon.factors()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadActivities() {
  loading.value = true
  error.value = ''
  try {
    activities.value = await financeApi.carbon.activities({
      periodCode: activityQuery.periodCode || undefined,
      scope: activityQuery.scope || undefined,
      status: activityQuery.status || undefined,
      size: 100,
    })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadTargets() {
  try {
    targets.value = await financeApi.carbon.targets()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadIndicators() {
  try {
    indicators.value = await financeApi.carbon.indicators()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadReports() {
  try {
    reports.value = await financeApi.carbon.reports({ size: 50 })
  } catch (err) {
    error.value = describeError(err)
  }
}

async function submitFactor() {
  if (!factorForm.factor_code.trim() || !factorForm.factor_name.trim() || !factorForm.factor_value) {
    factorError.value = '因子编码、名称与因子值不能为空'
    return
  }
  try {
    await financeApi.carbon.createFactor({
      factor_code: factorForm.factor_code.trim(),
      factor_name: factorForm.factor_name.trim(),
      scope: factorForm.scope,
      activity_type: factorForm.activity_type,
      activity_unit: factorForm.activity_unit,
      factor_value: factorForm.factor_value,
      factor_unit: factorForm.factor_unit,
      factor_version: factorForm.factor_version,
      source: factorForm.source.trim() || undefined,
    })
    ElMessage.success('排放因子已创建')
    factorVisible.value = false
    await loadFactors()
  } catch (err) {
    factorError.value = describeError(err)
  }
}

async function submitActivity() {
  if (!activityForm.amount || !activityForm.activity_date) {
    activityError.value = '活动数据量与日期不能为空'
    return
  }
  try {
    await financeApi.carbon.createActivity({
      scope: activityForm.scope,
      activity_type: activityForm.activity_type,
      activity_date: activityForm.activity_date,
      amount: activityForm.amount,
      unit: activityForm.unit,
      factor_id: activityForm.factor_id ? activityForm.factor_id : null,
      data_source: activityForm.data_source,
    })
    ElMessage.success('碳活动已提交')
    activityVisible.value = false
    await loadActivities()
  } catch (err) {
    activityError.value = describeError(err)
  }
}

async function verifyActivity(row: FinanceCarbonActivity) {
  try {
    const updated = await financeApi.carbon.verifyActivity(row.id)
    ElMessage.success(`活动 ${updated.activity_no} 已核验`)
    await loadActivities()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function lockActivity(row: FinanceCarbonActivity) {
  try {
    const updated = await financeApi.carbon.lockActivity(row.id)
    ElMessage.success(`活动 ${updated.activity_no} 已锁定`)
    await loadActivities()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function calculate() {
  if (!calcForm.period_code.trim()) {
    ElMessage.warning('请填写核算期间')
    return
  }
  try {
    calcResult.value = await financeApi.carbon.calculate({
      period_code: calcForm.period_code.trim(),
      scope: calcForm.scope || undefined,
      recalc: calcForm.recalc,
    })
    ElMessage.success(`核算批次 ${calcResult.value.batch_no} 完成，排放总量 ${money(calcResult.value.total_emission)}`)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitTarget() {
  if (!targetForm.target_name.trim() || !targetForm.base_emission || !targetForm.target_emission) {
    targetError.value = '目标名称、基准排放与目标排放不能为空'
    return
  }
  try {
    await financeApi.carbon.createTarget({
      target_name: targetForm.target_name.trim(),
      target_year: Number(targetForm.target_year),
      base_year: Number(targetForm.base_year),
      base_emission: targetForm.base_emission,
      target_emission: targetForm.target_emission,
    })
    ElMessage.success('减排目标已创建')
    targetVisible.value = false
    await loadTargets()
  } catch (err) {
    targetError.value = describeError(err)
  }
}

async function submitIndicator() {
  if (!indicatorForm.indicator_code.trim() || !indicatorForm.indicator_name.trim()) {
    indicatorError.value = '指标编码与名称不能为空'
    return
  }
  try {
    await financeApi.carbon.createIndicator({
      indicator_code: indicatorForm.indicator_code.trim(),
      indicator_name: indicatorForm.indicator_name.trim(),
      category: indicatorForm.category,
      unit: indicatorForm.unit,
      period_year: Number(indicatorForm.period_year),
      target_value: indicatorForm.target_value || undefined,
      actual_value: indicatorForm.actual_value || undefined,
      data_source: indicatorForm.data_source.trim() || undefined,
    })
    ElMessage.success('ESG 指标已保存')
    indicatorVisible.value = false
    await loadIndicators()
  } catch (err) {
    indicatorError.value = describeError(err)
  }
}

async function submitReport() {
  if (!reportForm.report_name.trim()) {
    reportError.value = '报告名称不能为空'
    return
  }
  try {
    await financeApi.carbon.createReport({
      report_name: reportForm.report_name.trim(),
      period_year: Number(reportForm.period_year),
      report_type: reportForm.report_type,
      report_boundary: reportForm.report_boundary.trim() || undefined,
    })
    ElMessage.success('ESG 报告已创建')
    reportVisible.value = false
    await loadReports()
  } catch (err) {
    reportError.value = describeError(err)
  }
}

async function publishReport(row: FinanceEsgReport) {
  try {
    const updated = await financeApi.carbon.publishReport(row.id)
    ElMessage.success(`报告 ${updated.report_no} 已发布`)
    await loadReports()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function exportReport(row: FinanceEsgReport) {
  try {
    await financeApi.carbon.exportReport(row.id)
    ElMessage.success('报告已开始下载')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function changeTab(name: string | number) {
  if (name === 'activities') loadActivities()
  else if (name === 'factors') loadFactors()
  else if (name === 'targets') loadTargets()
  else if (name === 'esg') {
    loadIndicators()
    loadReports()
  }
}

onMounted(async () => {
  await loadActivities()
  await loadFactors()
})
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="双碳与 ESG"
      description="排放因子、碳活动填报与核验、期间碳核算、减排目标与 ESG 指标/报告。"
    >
      <template #actions>
        <el-button v-perm="'biz:finance:carbon:submit'" type="primary" @click="activityVisible = true">填报碳活动</el-button>
        <el-button :loading="loading" @click="changeTab(activeTab)">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="changeTab">
        <el-tab-pane label="碳活动" name="activities">
          <div class="fl-toolbar">
            <el-input v-model="activityQuery.periodCode" placeholder="期间，如 2026" clearable style="width: 150px" />
            <el-select v-model="activityQuery.scope" placeholder="范围" clearable style="width: 120px">
              <el-option label="范围一" value="SCOPE1" />
              <el-option label="范围二" value="SCOPE2" />
              <el-option label="范围三" value="SCOPE3" />
            </el-select>
            <el-select v-model="activityQuery.status" placeholder="状态" clearable style="width: 130px">
              <el-option v-for="(label, value) in activityStatusLabels" :key="value" :label="label" :value="value" />
            </el-select>
            <el-button type="primary" plain @click="loadActivities">查询</el-button>
          </div>
          <el-table :data="activities" v-loading="loading" border stripe>
            <el-table-column prop="activity_no" label="活动编号" width="160" />
            <el-table-column prop="activity_date" label="活动日期" width="120" />
            <el-table-column prop="activity_type" label="活动类型" width="120" />
            <el-table-column label="范围" width="100"><template #default="{ row }">{{ scopeLabel(row.scope) }}</template></el-table-column>
            <el-table-column prop="amount" label="活动数据" width="110" align="right" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column prop="period_code" label="期间" width="100" />
            <el-table-column prop="data_source" label="数据来源" width="110" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag size="small">{{ activityStatusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'SUBMITTED'" v-perm="'biz:finance:carbon:verify'" link type="primary" @click="verifyActivity(row)">核验</el-button>
                <el-button v-if="row.status === 'VERIFIED'" v-perm="'biz:finance:carbon:verify'" link type="warning" @click="lockActivity(row)">锁定</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="排放因子" name="factors">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:finance:carbon:factor:manage'" type="primary" plain @click="factorVisible = true">新增因子</el-button>
            <span class="fl-muted">共 {{ factors.length }} 个因子</span>
          </div>
          <el-table :data="factors" border stripe>
            <el-table-column prop="factor_code" label="因子编码" width="150" />
            <el-table-column prop="factor_name" label="因子名称" min-width="180" />
            <el-table-column label="范围" width="100"><template #default="{ row }">{{ scopeLabel(row.scope) }}</template></el-table-column>
            <el-table-column prop="activity_type" label="活动类型" width="120" />
            <el-table-column prop="factor_value" label="因子值" width="120" align="right" />
            <el-table-column prop="factor_unit" label="单位" width="140" />
            <el-table-column prop="factor_version" label="版本" width="90" />
            <el-table-column prop="source" label="来源" min-width="120" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="碳核算" name="calc">
          <el-form inline>
            <el-form-item label="期间">
              <el-input v-model="calcForm.period_code" placeholder="2026" style="width: 140px" />
            </el-form-item>
            <el-form-item label="范围">
              <el-select v-model="calcForm.scope" clearable placeholder="全部" style="width: 130px">
                <el-option label="范围一" value="SCOPE1" />
                <el-option label="范围二" value="SCOPE2" />
                <el-option label="范围三" value="SCOPE3" />
              </el-select>
            </el-form-item>
            <el-form-item label="重算"><el-switch v-model="calcForm.recalc" /></el-form-item>
            <el-form-item>
              <el-button v-perm="'biz:finance:carbon:calculate'" type="primary" @click="calculate">执行核算</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="calcResult" :column="3" border size="small" style="margin-top: 8px">
            <el-descriptions-item label="批次号">{{ calcResult.batch_no }}</el-descriptions-item>
            <el-descriptions-item label="期间">{{ calcResult.period_code }}</el-descriptions-item>
            <el-descriptions-item label="活动数">{{ calcResult.activity_count }}</el-descriptions-item>
            <el-descriptions-item label="排放总量">{{ money(calcResult.total_emission) }}</el-descriptions-item>
            <el-descriptions-item v-for="(value, key) in calcResult.by_scope" :key="key" :label="scopeLabel(key)">{{ money(value) }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="减排目标" name="targets">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:finance:carbon:target:manage'" type="primary" plain @click="targetVisible = true">新增目标</el-button>
            <span class="fl-muted">共 {{ targets.length }} 个目标</span>
          </div>
          <el-table :data="targets" border stripe>
            <el-table-column prop="target_no" label="目标编号" width="160" />
            <el-table-column prop="target_name" label="目标名称" min-width="180" />
            <el-table-column prop="target_year" label="目标年" width="90" />
            <el-table-column prop="base_year" label="基准年" width="90" />
            <el-table-column prop="base_emission" label="基准排放" width="120" align="right" />
            <el-table-column prop="target_emission" label="目标排放" width="120" align="right" />
            <el-table-column prop="reduction_rate" label="减排率" width="100" />
            <el-table-column prop="current_emission" label="当前排放" width="120" align="right" />
            <el-table-column prop="progress" label="进度" width="100" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="ESG 指标与报告" name="esg">
          <div class="fl-toolbar">
            <el-button v-perm="'biz:finance:esg:manage'" type="primary" plain @click="indicatorVisible = true">新增指标</el-button>
            <el-button v-perm="'biz:finance:esg:manage'" plain @click="reportVisible = true">新建报告</el-button>
          </div>
          <el-table :data="indicators" border stripe size="small" style="margin-bottom: 16px">
            <el-table-column prop="indicator_code" label="指标编码" width="140" />
            <el-table-column prop="indicator_name" label="指标名称" min-width="170" />
            <el-table-column prop="category" label="类别" width="90" />
            <el-table-column prop="unit" label="单位" width="90" />
            <el-table-column prop="period_year" label="年度" width="90" />
            <el-table-column prop="target_value" label="目标值" width="110" align="right" />
            <el-table-column prop="actual_value" label="实际值" width="110" align="right" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
          <el-table :data="reports" border stripe size="small">
            <el-table-column prop="report_no" label="报告编号" width="160" />
            <el-table-column prop="report_name" label="报告名称" min-width="180" />
            <el-table-column prop="period_year" label="年度" width="90" />
            <el-table-column prop="report_type" label="类型" width="100" />
            <el-table-column prop="report_boundary" label="边界" min-width="130" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'PUBLISHED'" v-perm="'tenant:finance:esg:report:publish'" link type="primary" @click="publishReport(row)">发布</el-button>
                <el-button v-perm="'tenant:finance:esg:report:publish'" link @click="exportReport(row)">导出</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="factorVisible" title="新增排放因子" width="540px">
      <el-alert v-if="factorError" type="error" :closable="false" show-icon :title="factorError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="因子编码" required><el-input v-model="factorForm.factor_code" maxlength="32" /></el-form-item>
        <el-form-item label="因子名称" required><el-input v-model="factorForm.factor_name" maxlength="128" /></el-form-item>
        <el-form-item label="范围">
          <el-select v-model="factorForm.scope" style="width: 100%">
            <el-option label="范围一" value="SCOPE1" />
            <el-option label="范围二" value="SCOPE2" />
            <el-option label="范围三" value="SCOPE3" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动类型"><el-input v-model="factorForm.activity_type" maxlength="32" /></el-form-item>
        <el-form-item label="活动单位"><el-input v-model="factorForm.activity_unit" maxlength="16" /></el-form-item>
        <el-form-item label="因子值" required><el-input v-model="factorForm.factor_value" placeholder="0.0000" /></el-form-item>
        <el-form-item label="因子单位"><el-input v-model="factorForm.factor_unit" maxlength="32" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="factorForm.factor_version" maxlength="16" /></el-form-item>
        <el-form-item label="来源"><el-input v-model="factorForm.source" maxlength="128" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="factorVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFactor">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="activityVisible" title="填报碳活动" width="500px">
      <el-alert v-if="activityError" type="error" :closable="false" show-icon :title="activityError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="范围">
          <el-select v-model="activityForm.scope" style="width: 100%">
            <el-option label="范围一" value="SCOPE1" />
            <el-option label="范围二" value="SCOPE2" />
            <el-option label="范围三" value="SCOPE3" />
          </el-select>
        </el-form-item>
        <el-form-item label="活动类型"><el-input v-model="activityForm.activity_type" maxlength="32" /></el-form-item>
        <el-form-item label="活动日期">
          <el-date-picker v-model="activityForm.activity_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="活动数据" required><el-input v-model="activityForm.amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="activityForm.unit" maxlength="16" /></el-form-item>
        <el-form-item label="排放因子">
          <el-select v-model="activityForm.factor_id" clearable filterable style="width: 100%" placeholder="可选">
            <el-option v-for="factor in factors" :key="factor.id" :label="`${factor.factor_code} ${factor.factor_name}`" :value="factor.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据来源"><el-input v-model="activityForm.data_source" maxlength="32" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="activityVisible = false">取消</el-button>
        <el-button type="primary" @click="submitActivity">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="targetVisible" title="新增减排目标" width="500px">
      <el-alert v-if="targetError" type="error" :closable="false" show-icon :title="targetError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="目标名称" required><el-input v-model="targetForm.target_name" maxlength="128" /></el-form-item>
        <el-form-item label="目标年"><el-input-number v-model="targetForm.target_year" :min="2000" :max="2100" style="width: 100%" /></el-form-item>
        <el-form-item label="基准年"><el-input-number v-model="targetForm.base_year" :min="2000" :max="2100" style="width: 100%" /></el-form-item>
        <el-form-item label="基准排放" required><el-input v-model="targetForm.base_emission" placeholder="tCO2e" /></el-form-item>
        <el-form-item label="目标排放" required><el-input v-model="targetForm.target_emission" placeholder="tCO2e" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="targetVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTarget">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="indicatorVisible" title="新增 ESG 指标" width="500px">
      <el-alert v-if="indicatorError" type="error" :closable="false" show-icon :title="indicatorError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="指标编码" required><el-input v-model="indicatorForm.indicator_code" maxlength="32" /></el-form-item>
        <el-form-item label="指标名称" required><el-input v-model="indicatorForm.indicator_name" maxlength="128" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="indicatorForm.category" style="width: 100%">
            <el-option label="环境" value="ENV" />
            <el-option label="社会" value="SOCIAL" />
            <el-option label="治理" value="GOVERNANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位"><el-input v-model="indicatorForm.unit" maxlength="16" /></el-form-item>
        <el-form-item label="年度"><el-input-number v-model="indicatorForm.period_year" :min="2000" :max="2100" style="width: 100%" /></el-form-item>
        <el-form-item label="目标值"><el-input v-model="indicatorForm.target_value" /></el-form-item>
        <el-form-item label="实际值"><el-input v-model="indicatorForm.actual_value" /></el-form-item>
        <el-form-item label="数据来源"><el-input v-model="indicatorForm.data_source" maxlength="64" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="indicatorVisible = false">取消</el-button>
        <el-button type="primary" @click="submitIndicator">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportVisible" title="新建 ESG 报告" width="500px">
      <el-alert v-if="reportError" type="error" :closable="false" show-icon :title="reportError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="报告名称" required><el-input v-model="reportForm.report_name" maxlength="128" /></el-form-item>
        <el-form-item label="年度"><el-input-number v-model="reportForm.period_year" :min="2000" :max="2100" style="width: 100%" /></el-form-item>
        <el-form-item label="报告类型"><el-input v-model="reportForm.report_type" maxlength="32" /></el-form-item>
        <el-form-item label="报告边界"><el-input v-model="reportForm.report_boundary" maxlength="128" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReport">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
