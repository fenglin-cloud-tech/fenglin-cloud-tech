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
const ledgerId = ref<string | number | ''>('')
const periodCode = ref('')
const subjectCode = ref('')
const activeTab = ref('balance')
const loading = ref(false)
const error = ref('')

const balanceSheet = ref<FinanceBalanceSheet | null>(null)
const incomeStatement = ref<FinanceIncomeStatement | null>(null)
const trialBalance = ref<FinanceTrialBalance | null>(null)
const generalLedger = ref<Record<string, unknown> | null>(null)

const gapItems = [
  '后端只提供资产负债表/利润表/试算平衡/总账查询，没有财务报表导出（Excel/PDF）接口，也没有多期对比接口。',
  '总账接口返回后端原始 Map（科目、期间、分录明细），本页按 JSON 原样展示，不臆造列定义。',
  '报表口径以后端账簿为准；期间未结账时报表仍可查询，但可能包含未记账凭证之外的数据差异。',
]

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
    <GapNotice :items="gapItems" />

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
