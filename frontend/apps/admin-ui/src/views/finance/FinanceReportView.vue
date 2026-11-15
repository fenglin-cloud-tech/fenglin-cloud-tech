<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  financeApi,
  type FinanceBalanceSheet,
  type FinanceIncomeStatement,
  type FinanceLedger,
  type FinancePeriod,
  type FinanceTrialBalance,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const ledgers = ref<FinanceLedger[]>([])
const periods = ref<FinancePeriod[]>([])
const ledgerId = ref<string>('')
const periodCode = ref('')
const subjectCode = ref('')
const activeTab = ref('balance')
const loading = ref(false)
const error = ref('')

const balanceSheet = ref<FinanceBalanceSheet | null>(null)
const incomeStatement = ref<FinanceIncomeStatement | null>(null)
const trialBalance = ref<FinanceTrialBalance | null>(null)
const generalLedger = ref<Record<string, unknown> | null>(null)

const comparePeriodA = ref(currentPeriod())
const comparePeriodB = ref(previousPeriod())
const compareLoading = ref(false)
const compareError = ref('')
const comparison = ref<Array<{ label: string; period_a: string; period_b: string; diff: string }>>([])

const gapItems = [
  '总账接口返回后端原始 Map（科目、期间、分录明细），本页按 JSON 原样展示，不臆造列定义。',
  '报表口径以后端账簿为准；期间未结账时报表仍可查询，但可能包含未记账凭证之外的数据差异。',
]

function currentPeriod() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

function previousPeriod() {
  const prev = new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1)
  return `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}`
}

function csvCell(value: unknown) {
  const text = value === undefined || value === null ? '' : String(value)
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function downloadCsv(filename: string, rows: unknown[][]) {
  const content = '\uFEFF' + rows.map((row) => row.map(csvCell).join(',')).join('\r\n')
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function exportBalanceSheet() {
  const data = balanceSheet.value
  if (!data) {
    ElMessage.warning('暂无资产负债表数据')
    return
  }
  const rows: unknown[][] = [['科目编码', '科目名称', '类别', '金额']]
  for (const line of data.lines) rows.push([line.subject_code, line.subject_name, line.category, line.amount])
  rows.push(['资产合计', '', '', data.total_assets])
  rows.push(['负债权益合计', '', '', data.total_liabilities_equity])
  downloadCsv(`资产负债表-${data.period_code}.csv`, rows)
}

function exportIncomeStatement() {
  const data = incomeStatement.value
  if (!data) {
    ElMessage.warning('暂无利润表数据')
    return
  }
  const rows: unknown[][] = [['科目编码', '科目名称', '类别', '金额']]
  for (const line of data.lines) rows.push([line.subject_code, line.subject_name, line.category, line.amount])
  rows.push(['收入', '', '', data.revenue])
  rows.push(['成本', '', '', data.cost])
  rows.push(['净利润', '', '', data.net_profit])
  downloadCsv(`利润表-${data.period_code}.csv`, rows)
}

function exportTrialBalance() {
  const data = trialBalance.value
  if (!data) {
    ElMessage.warning('暂无试算平衡数据')
    return
  }
  const rows: unknown[][] = [['科目编码', '科目名称', '期初借方', '期初贷方', '本期借方', '本期贷方', '期末借方', '期末贷方']]
  for (const line of data.lines) {
    rows.push([
      line.subject_code,
      line.subject_name,
      line.opening_debit,
      line.opening_credit,
      line.period_debit,
      line.period_credit,
      line.closing_debit,
      line.closing_credit,
    ])
  }
  rows.push(['合计', '', '', '', data.total_debit, data.total_credit, '', ''])
  downloadCsv(`试算平衡-${data.period_code}.csv`, rows)
}

async function runCompare() {
  if (!ledgerId.value) {
    compareError.value = '请先选择账套'
    return
  }
  if (!/^\d{4}-\d{2}$/.test(comparePeriodA.value.trim()) || !/^\d{4}-\d{2}$/.test(comparePeriodB.value.trim())) {
    compareError.value = '期间格式应为 YYYY-MM'
    return
  }
  compareLoading.value = true
  compareError.value = ''
  comparison.value = []
  try {
    const [balanceA, balanceB, incomeA, incomeB] = await Promise.all([
      financeApi.reports.balanceSheet(ledgerId.value, comparePeriodA.value.trim()),
      financeApi.reports.balanceSheet(ledgerId.value, comparePeriodB.value.trim()),
      financeApi.reports.incomeStatement(ledgerId.value, comparePeriodA.value.trim()),
      financeApi.reports.incomeStatement(ledgerId.value, comparePeriodB.value.trim()),
    ])
    const rows = [
      { label: '资产合计', period_a: balanceA.total_assets, period_b: balanceB.total_assets },
      { label: '负债合计', period_a: balanceA.liabilities, period_b: balanceB.liabilities },
      { label: '权益合计', period_a: balanceA.equity, period_b: balanceB.equity },
      { label: '净利润', period_a: incomeA.net_profit, period_b: incomeB.net_profit },
    ]
    comparison.value = rows.map((row) => ({
      ...row,
      diff: (Number(row.period_a || 0) - Number(row.period_b || 0)).toFixed(2),
    }))
  } catch (err) {
    compareError.value = describeError(err)
  } finally {
    compareLoading.value = false
  }
}

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadLedgers() {
  try {
    ledgers.value = await financeApi.ledgers.list()
    if (!ledgerId.value && ledgers.value.length) ledgerId.value = ledgers.value[0].id
    await loadPeriods()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadPeriods() {
  if (!ledgerId.value) return
  try {
    periods.value = await financeApi.ledgers.periods(ledgerId.value)
    if (!periodCode.value && periods.value.length) {
      periodCode.value = periods.value[periods.value.length - 1].period_code
    }
    await loadActive()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadActive() {
  if (!ledgerId.value || !periodCode.value) return
  const id = ledgerId.value
  loading.value = true
  error.value = ''
  try {
    if (activeTab.value === 'balance') {
      balanceSheet.value = await financeApi.reports.balanceSheet(id, periodCode.value)
    } else if (activeTab.value === 'income') {
      incomeStatement.value = await financeApi.reports.incomeStatement(id, periodCode.value)
    } else if (activeTab.value === 'trial') {
      trialBalance.value = await financeApi.ledgers.trialBalance(id, periodCode.value)
    }
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadGeneralLedger() {
  if (!ledgerId.value || !periodCode.value || !subjectCode.value.trim()) {
    ElMessage.warning('请选择账套、期间并填写科目编码')
    return
  }
  loading.value = true
  error.value = ''
  try {
    generalLedger.value = await financeApi.ledgers.generalLedger(
      ledgerId.value,
      subjectCode.value.trim(),
      periodCode.value,
    )
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(loadLedgers)
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="账簿与财务报表"
      description="资产负债表、利润表、试算平衡与科目总账，数据直接取自财务服务账簿。"
    >
      <template #actions>
        <el-select v-model="ledgerId" placeholder="账套" style="width: 200px" @change="loadPeriods">
          <el-option v-for="ledger in ledgers" :key="ledger.id" :label="ledger.ledger_name" :value="ledger.id" />
        </el-select>
        <el-select v-model="periodCode" placeholder="期间" style="width: 130px" @change="loadActive">
          <el-option v-for="period in periods" :key="period.period_code" :label="period.period_code" :value="period.period_code" />
        </el-select>
        <el-button :loading="loading" @click="loadActive">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice v-if="gapItems.length" :items="gapItems" />

    <el-card shadow="never" v-loading="loading">
      <el-tabs v-model="activeTab" @tab-change="loadActive">
        <el-tab-pane label="资产负债表" name="balance">
          <div v-if="balanceSheet" class="fl-toolbar">
            <el-tag size="small" :type="balanceSheet.balanced ? 'success' : 'danger'">
              {{ balanceSheet.balanced ? '资产=负债+权益' : '不平衡' }}
            </el-tag>
            <span class="fl-muted">
              资产 {{ money(balanceSheet.assets) }} / 负债 {{ money(balanceSheet.liabilities) }} /
              权益 {{ money(balanceSheet.equity) }} / 损益 {{ money(balanceSheet.profit_loss) }}
            </span>
            <el-button size="small" plain style="margin-left: auto" @click="exportBalanceSheet">导出 CSV</el-button>
          </div>
          <el-table :data="balanceSheet?.lines ?? []" border stripe max-height="520">
            <el-table-column prop="subject_code" label="科目编码" width="130" />
            <el-table-column prop="subject_name" label="科目名称" min-width="200" />
            <el-table-column prop="category" label="类别" width="120" />
            <el-table-column label="金额" width="160" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="balanceSheet" class="fl-muted" style="margin-top: 8px">
            资产合计 {{ money(balanceSheet.total_assets) }}；负债权益合计 {{ money(balanceSheet.total_liabilities_equity) }}
          </div>
        </el-tab-pane>

        <el-tab-pane label="利润表" name="income">
          <div class="fl-toolbar">
            <el-button size="small" plain @click="exportIncomeStatement">导出 CSV</el-button>
          </div>
          <el-table :data="incomeStatement?.lines ?? []" border stripe max-height="520">
            <el-table-column prop="subject_code" label="科目编码" width="130" />
            <el-table-column prop="subject_name" label="科目名称" min-width="200" />
            <el-table-column prop="category" label="类别" width="120" />
            <el-table-column label="金额" width="160" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="incomeStatement" class="fl-muted" style="margin-top: 8px">
            收入 {{ money(incomeStatement.revenue) }} / 成本 {{ money(incomeStatement.cost) }} /
            净利润 {{ money(incomeStatement.net_profit) }}
          </div>
        </el-tab-pane>

        <el-tab-pane label="试算平衡" name="trial">
          <div v-if="trialBalance" class="fl-toolbar">
            <el-tag size="small" :type="trialBalance.balanced ? 'success' : 'danger'">
              {{ trialBalance.balanced ? '借贷平衡' : '借贷不平' }}
            </el-tag>
            <span class="fl-muted">借方 {{ money(trialBalance.total_debit) }} / 贷方 {{ money(trialBalance.total_credit) }}</span>
            <el-button size="small" plain style="margin-left: auto" @click="exportTrialBalance">导出 CSV</el-button>
          </div>
          <el-table :data="trialBalance?.lines ?? []" border stripe max-height="520">
            <el-table-column prop="subject_code" label="科目编码" width="130" />
            <el-table-column prop="subject_name" label="科目名称" min-width="170" />
            <el-table-column label="期初借方" width="125" align="right"><template #default="{ row }">{{ money(row.opening_debit) }}</template></el-table-column>
            <el-table-column label="期初贷方" width="125" align="right"><template #default="{ row }">{{ money(row.opening_credit) }}</template></el-table-column>
            <el-table-column label="本期借方" width="125" align="right"><template #default="{ row }">{{ money(row.period_debit) }}</template></el-table-column>
            <el-table-column label="本期贷方" width="125" align="right"><template #default="{ row }">{{ money(row.period_credit) }}</template></el-table-column>
            <el-table-column label="期末借方" width="125" align="right"><template #default="{ row }">{{ money(row.closing_debit) }}</template></el-table-column>
            <el-table-column label="期末贷方" width="125" align="right"><template #default="{ row }">{{ money(row.closing_credit) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="多期对比" name="compare">
          <div class="fl-toolbar">
            <el-input v-model="comparePeriodA" placeholder="YYYY-MM" style="width: 130px" />
            <span class="fl-muted">对比</span>
            <el-input v-model="comparePeriodB" placeholder="YYYY-MM" style="width: 130px" />
            <el-button type="primary" plain :loading="compareLoading" @click="runCompare">对比</el-button>
          </div>
          <el-alert v-if="compareError" type="error" :closable="false" show-icon :title="compareError" style="margin-bottom: 12px" />
          <el-table :data="comparison" v-loading="compareLoading" border stripe empty-text="选择两个期间后点击对比">
            <el-table-column prop="label" label="指标" width="160" />
            <el-table-column :label="`期间 ${comparePeriodA}`" width="180" align="right">
              <template #default="{ row }">{{ money(row.period_a) }}</template>
            </el-table-column>
            <el-table-column :label="`期间 ${comparePeriodB}`" width="180" align="right">
              <template #default="{ row }">{{ money(row.period_b) }}</template>
            </el-table-column>
            <el-table-column label="差额（A-B）" align="right">
              <template #default="{ row }">{{ money(row.diff) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="科目总账" name="general">
          <div class="fl-toolbar">
            <el-input v-model="subjectCode" placeholder="科目编码，如 1001" style="width: 200px" @keyup.enter="loadGeneralLedger" />
            <el-button type="primary" plain @click="loadGeneralLedger">查询总账</el-button>
          </div>
          <pre v-if="generalLedger" class="fl-json">{{ JSON.stringify(generalLedger, null, 2) }}</pre>
          <el-empty v-else description="输入科目编码后查询该科目总账" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.fl-json {
  max-height: 520px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--fl-color-border);
  border-radius: 6px;
  background: #fafafa;
  font-size: 12px;
  line-height: 1.6;
}
</style>
