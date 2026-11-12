<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  financeApi,
  type FinanceCurrency,
  type FinanceExchangeRate,
  type FinanceLedger,
  type FinancePeriod,
  type FinanceSubject,
  type FinanceTrialBalance,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const ledgers = ref<FinanceLedger[]>([])
const subjects = ref<FinanceSubject[]>([])
const periods = ref<FinancePeriod[]>([])
const currencies = ref<FinanceCurrency[]>([])
const exchangeRates = ref<FinanceExchangeRate[]>([])
const trialBalance = ref<FinanceTrialBalance | null>(null)

const selectedLedgerId = ref<string | number | ''>('')
const selectedPeriod = ref('')
const activeTab = ref('subjects')
const loading = ref(false)
const error = ref('')

const ledgerVisible = ref(false)
const ledgerSaving = ref(false)
const ledgerError = ref('')
const ledgerForm = reactive({
  ledger_code: '',
  ledger_name: '',
  company_name: '',
  base_currency: 'CNY',
  fiscal_year_start_month: 1,
  accounting_standard: 'CAS',
  tax_no: '',
})

const subjectVisible = ref(false)
const subjectSaving = ref(false)
const subjectError = ref('')
const subjectForm = reactive({
  subject_code: '',
  subject_name: '',
  subject_category: 'ASSET',
  balance_direction: 'DEBIT',
  is_leaf: true,
  auxiliary_types: '',
})

const currencyVisible = ref(false)
const currencyError = ref('')
const currencyForm = reactive({ currency_code: '', currency_name: '', symbol: '', decimal_places: 2 })

const rateVisible = ref(false)
const rateError = ref('')
const rateForm = reactive({ from_currency: 'USD', to_currency: 'CNY', rate: '', effective_date: '', source: '' })

const categoryOptions = [
  { value: 'ASSET', label: '资产' },
  { value: 'LIABILITY', label: '负债' },
  { value: 'EQUITY', label: '权益' },
  { value: 'COST', label: '成本' },
  { value: 'PROFIT', label: '损益' },
]
const periodStatusLabels: Record<string, string> = {
  OPEN: '开启',
  CLOSED: '已结账',
  SEALED: '已封存',
}

const gapItems = [
  '后端没有账套删除/停用接口，创建后的账套只能保留；也没有单独的新增会计期间接口，期间随账套初始化。',
  '总账（general-ledger）返回后端原始 Map 结构，本页不做图表化，避免前端臆造字段。',
  '币种与汇率列表要求 biz:finance:budget:view，创建要求 tenant:finance:ledger:manage，权限不足时后端返回真实 403。',
]

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function categoryLabel(value?: string) {
  return categoryOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function directionLabel(value?: string) {
  return value === 'CREDIT' ? '贷' : '借'
}

async function loadLedgers() {
  loading.value = true
  error.value = ''
  try {
    ledgers.value = await financeApi.ledgers.list()
    if (!selectedLedgerId.value && ledgers.value.length) {
      selectedLedgerId.value = ledgers.value[0].id
    }
    await loadLedgerDetail()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadLedgerDetail() {
  if (!selectedLedgerId.value) {
    subjects.value = []
    periods.value = []
    return
  }
  const ledgerId = selectedLedgerId.value
  try {
    const [subjectList, periodList] = await Promise.all([
      financeApi.ledgers.subjects(ledgerId),
      financeApi.ledgers.periods(ledgerId),
    ])
    subjects.value = subjectList
    periods.value = periodList
    if (!selectedPeriod.value && periodList.length) {
      selectedPeriod.value = periodList[periodList.length - 1].period_code
    }
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadBaseData() {
  try {
    const [currencyList, rateList] = await Promise.all([
      financeApi.arap.currencies(),
      financeApi.arap.exchangeRates(),
    ])
    currencies.value = currencyList
    exchangeRates.value = rateList
  } catch (err) {
    currencies.value = []
    exchangeRates.value = []
    error.value = describeError(err)
  }
}

async function loadTrialBalance() {
  if (!selectedLedgerId.value || !selectedPeriod.value) {
    ElMessage.warning('请选择账套与会计期间')
    return
  }
  try {
    trialBalance.value = await financeApi.ledgers.trialBalance(
      selectedLedgerId.value,
      selectedPeriod.value,
    )
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitLedger() {
  if (!ledgerForm.ledger_code.trim() || !ledgerForm.ledger_name.trim() || !ledgerForm.company_name.trim()) {
    ledgerError.value = '账套编码、名称与核算主体不能为空'
    return
  }
  ledgerSaving.value = true
  ledgerError.value = ''
  try {
    const created = await financeApi.ledgers.create({
      ledger_code: ledgerForm.ledger_code.trim(),
      ledger_name: ledgerForm.ledger_name.trim(),
      company_name: ledgerForm.company_name.trim(),
      base_currency: ledgerForm.base_currency,
      fiscal_year_start_month: Number(ledgerForm.fiscal_year_start_month),
      accounting_standard: ledgerForm.accounting_standard,
      tax_no: ledgerForm.tax_no.trim() || undefined,
    })
    ElMessage.success(`账套 ${created.ledger_code} 已创建`)
    ledgerVisible.value = false
    await loadLedgers()
  } catch (err) {
    ledgerError.value = describeError(err)
  } finally {
    ledgerSaving.value = false
  }
}

async function submitSubject() {
  if (!selectedLedgerId.value || !subjectForm.subject_code.trim() || !subjectForm.subject_name.trim()) {
    subjectError.value = '科目编码与名称不能为空'
    return
  }
  subjectSaving.value = true
  subjectError.value = ''
  try {
    await financeApi.ledgers.createSubject(selectedLedgerId.value, {
      subject_code: subjectForm.subject_code.trim(),
      subject_name: subjectForm.subject_name.trim(),
      subject_category: subjectForm.subject_category,
      balance_direction: subjectForm.balance_direction,
      is_leaf: subjectForm.is_leaf,
      auxiliary_types: subjectForm.auxiliary_types.trim() || undefined,
    })
    ElMessage.success('科目已创建')
    subjectVisible.value = false
    Object.assign(subjectForm, { subject_code: '', subject_name: '', auxiliary_types: '' })
    await loadLedgerDetail()
  } catch (err) {
    subjectError.value = describeError(err)
  } finally {
    subjectSaving.value = false
  }
}

async function periodAction(period: FinancePeriod, action: 'close' | 'reopen' | 'seal') {
  const ledgerId = selectedLedgerId.value
  try {
    if (action === 'close') {
      const result = await financeApi.ledgers.closePeriod(ledgerId, period.period_code)
      ElMessage.success(`期间 ${result.period_code} 已结账（借贷合计 ${money(result.trial_debit)} / ${money(result.trial_credit)}）`)
    } else if (action === 'reopen') {
      await financeApi.ledgers.reopenPeriod(ledgerId, period.period_code)
      ElMessage.success('期间已反结账')
    } else {
      await financeApi.ledgers.sealPeriod(ledgerId, period.period_code)
      ElMessage.success('期间已封存')
    }
    await loadLedgerDetail()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitCurrency() {
  if (!currencyForm.currency_code.trim() || !currencyForm.currency_name.trim()) {
    currencyError.value = '币种编码与名称不能为空'
    return
  }
  try {
    await financeApi.arap.createCurrency({
      currency_code: currencyForm.currency_code.trim().toUpperCase(),
      currency_name: currencyForm.currency_name.trim(),
      symbol: currencyForm.symbol.trim() || undefined,
      decimal_places: Number(currencyForm.decimal_places),
    })
    ElMessage.success('币种已保存')
    currencyVisible.value = false
    await loadBaseData()
  } catch (err) {
    currencyError.value = describeError(err)
  }
}

async function submitRate() {
  if (!rateForm.rate || !rateForm.effective_date) {
    rateError.value = '汇率与生效日期不能为空'
    return
  }
  try {
    await financeApi.arap.createExchangeRate({
      from_currency: rateForm.from_currency.toUpperCase(),
      to_currency: rateForm.to_currency.toUpperCase(),
      rate: rateForm.rate,
      effective_date: rateForm.effective_date,
      source: rateForm.source.trim() || undefined,
    })
    ElMessage.success('汇率已保存')
    rateVisible.value = false
    await loadBaseData()
  } catch (err) {
    rateError.value = describeError(err)
  }
}

onMounted(async () => {
  await loadLedgers()
  await loadBaseData()
})
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="账套与会计期间"
      description="多账套、会计科目、期间结账/反结账/封存与试算平衡；金额均来自真实账簿接口。"
    >
      <template #actions>
        <el-select v-model="selectedLedgerId" placeholder="选择账套" style="width: 220px" @change="loadLedgerDetail">
          <el-option v-for="ledger in ledgers" :key="ledger.id" :label="`${ledger.ledger_code} ${ledger.ledger_name}`" :value="ledger.id" />
        </el-select>
        <el-button v-perm="'tenant:finance:ledger:manage'" type="primary" @click="ledgerVisible = true">新建账套</el-button>
        <el-button :loading="loading" @click="loadLedgers">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="会计科目" name="subjects">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:finance:subject:manage'" type="primary" plain @click="subjectVisible = true">新增科目</el-button>
            <span class="fl-muted">共 {{ subjects.length }} 个科目</span>
          </div>
          <el-table :data="subjects" v-loading="loading" border stripe max-height="520">
            <el-table-column prop="subject_code" label="科目编码" width="140" />
            <el-table-column prop="subject_name" label="科目名称" min-width="180" />
            <el-table-column label="类别" width="100">
              <template #default="{ row }">{{ categoryLabel(row.subject_category) }}</template>
            </el-table-column>
            <el-table-column label="余额方向" width="100">
              <template #default="{ row }">{{ directionLabel(row.balance_direction) }}</template>
            </el-table-column>
            <el-table-column label="级次" width="80">
              <template #default="{ row }">{{ row.subject_level }}</template>
            </el-table-column>
            <el-table-column label="末级" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.is_leaf ? 'success' : 'info'">{{ row.is_leaf ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="auxiliary_types" label="辅助核算" min-width="140" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="会计期间" name="periods">
          <el-table :data="periods" border stripe>
            <el-table-column prop="period_code" label="期间" width="120" />
            <el-table-column prop="start_date" label="开始日期" width="140" />
            <el-table-column prop="end_date" label="结束日期" width="140" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'OPEN' ? 'success' : row.status === 'SEALED' ? 'info' : 'warning'">
                  {{ periodStatusLabels[row.status] ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260">
              <template #default="{ row }">
                <el-button v-if="row.status === 'OPEN'" v-perm="'tenant:finance:period:manage'" link type="primary" @click="periodAction(row, 'close')">结账</el-button>
                <el-button v-if="row.status === 'CLOSED'" v-perm="'tenant:finance:period:manage'" link type="warning" @click="periodAction(row, 'reopen')">反结账</el-button>
                <el-button v-if="row.status === 'CLOSED'" v-perm="'tenant:finance:period:manage'" link type="info" @click="periodAction(row, 'seal')">封存</el-button>
                <el-button v-perm="'biz:finance:report:view'" link @click="selectedPeriod = row.period_code; activeTab = 'trial'; loadTrialBalance()">试算平衡</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="试算平衡" name="trial">
          <div class="fl-toolbar">
            <el-select v-model="selectedPeriod" placeholder="选择期间" style="width: 160px">
              <el-option v-for="period in periods" :key="period.period_code" :label="period.period_code" :value="period.period_code" />
            </el-select>
            <el-button type="primary" plain @click="loadTrialBalance">查询</el-button>
            <el-tag v-if="trialBalance" size="small" :type="trialBalance.balanced ? 'success' : 'danger'">
              {{ trialBalance.balanced ? '借贷平衡' : '借贷不平' }}
            </el-tag>
            <span v-if="trialBalance" class="fl-muted">
              借方合计 {{ money(trialBalance.total_debit) }} / 贷方合计 {{ money(trialBalance.total_credit) }}
            </span>
          </div>
          <el-table :data="trialBalance?.lines ?? []" border stripe max-height="520">
            <el-table-column prop="subject_code" label="科目编码" width="130" />
            <el-table-column prop="subject_name" label="科目名称" min-width="170" />
            <el-table-column label="期初借方" width="130" align="right">
              <template #default="{ row }">{{ money(row.opening_debit) }}</template>
            </el-table-column>
            <el-table-column label="期初贷方" width="130" align="right">
              <template #default="{ row }">{{ money(row.opening_credit) }}</template>
            </el-table-column>
            <el-table-column label="本期借方" width="130" align="right">
              <template #default="{ row }">{{ money(row.period_debit) }}</template>
            </el-table-column>
            <el-table-column label="本期贷方" width="130" align="right">
              <template #default="{ row }">{{ money(row.period_credit) }}</template>
            </el-table-column>
            <el-table-column label="期末借方" width="130" align="right">
              <template #default="{ row }">{{ money(row.closing_debit) }}</template>
            </el-table-column>
            <el-table-column label="期末贷方" width="130" align="right">
              <template #default="{ row }">{{ money(row.closing_credit) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="币种与汇率" name="currency">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:finance:ledger:manage'" type="primary" plain @click="currencyVisible = true">新增币种</el-button>
            <el-button v-perm="'tenant:finance:ledger:manage'" plain @click="rateVisible = true">录入汇率</el-button>
          </div>
          <el-table :data="currencies" border stripe size="small" style="margin-bottom: 16px">
            <el-table-column prop="currency_code" label="币种" width="100" />
            <el-table-column prop="currency_name" label="名称" min-width="140" />
            <el-table-column prop="symbol" label="符号" width="80" />
            <el-table-column prop="decimal_places" label="小数位" width="90" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
          <el-table :data="exchangeRates" border stripe size="small">
            <el-table-column prop="from_currency" label="源币种" width="100" />
            <el-table-column prop="to_currency" label="目标币种" width="100" />
            <el-table-column prop="rate" label="汇率" width="140" />
            <el-table-column prop="effective_date" label="生效日期" width="140" />
            <el-table-column prop="source" label="来源" min-width="140" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="ledgerVisible" title="新建账套" width="560px">
      <el-alert v-if="ledgerError" type="error" :closable="false" show-icon :title="ledgerError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="账套编码" required><el-input v-model="ledgerForm.ledger_code" maxlength="32" /></el-form-item>
        <el-form-item label="账套名称" required><el-input v-model="ledgerForm.ledger_name" maxlength="64" /></el-form-item>
        <el-form-item label="核算主体" required><el-input v-model="ledgerForm.company_name" maxlength="128" /></el-form-item>
        <el-form-item label="本位币"><el-input v-model="ledgerForm.base_currency" maxlength="8" /></el-form-item>
        <el-form-item label="会计年度起始月">
          <el-input-number v-model="ledgerForm.fiscal_year_start_month" :min="1" :max="12" style="width: 100%" />
        </el-form-item>
        <el-form-item label="会计准则"><el-input v-model="ledgerForm.accounting_standard" maxlength="32" /></el-form-item>
        <el-form-item label="税号"><el-input v-model="ledgerForm.tax_no" maxlength="32" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ledgerVisible = false">取消</el-button>
        <el-button type="primary" :loading="ledgerSaving" @click="submitLedger">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="subjectVisible" title="新增会计科目" width="520px">
      <el-alert v-if="subjectError" type="error" :closable="false" show-icon :title="subjectError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="科目编码" required><el-input v-model="subjectForm.subject_code" maxlength="32" /></el-form-item>
        <el-form-item label="科目名称" required><el-input v-model="subjectForm.subject_name" maxlength="64" /></el-form-item>
        <el-form-item label="科目类别">
          <el-select v-model="subjectForm.subject_category" style="width: 100%">
            <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="余额方向">
          <el-select v-model="subjectForm.balance_direction" style="width: 100%">
            <el-option label="借方" value="DEBIT" />
            <el-option label="贷方" value="CREDIT" />
          </el-select>
        </el-form-item>
        <el-form-item label="末级科目"><el-switch v-model="subjectForm.is_leaf" /></el-form-item>
        <el-form-item label="辅助核算"><el-input v-model="subjectForm.auxiliary_types" placeholder="如 CUSTOMER,DEPT" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subjectVisible = false">取消</el-button>
        <el-button type="primary" :loading="subjectSaving" @click="submitSubject">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="currencyVisible" title="新增币种" width="460px">
      <el-alert v-if="currencyError" type="error" :closable="false" show-icon :title="currencyError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="币种编码" required><el-input v-model="currencyForm.currency_code" maxlength="8" /></el-form-item>
        <el-form-item label="币种名称" required><el-input v-model="currencyForm.currency_name" maxlength="32" /></el-form-item>
        <el-form-item label="符号"><el-input v-model="currencyForm.symbol" maxlength="8" /></el-form-item>
        <el-form-item label="小数位"><el-input-number v-model="currencyForm.decimal_places" :min="0" :max="6" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="currencyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCurrency">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rateVisible" title="录入汇率" width="480px">
      <el-alert v-if="rateError" type="error" :closable="false" show-icon :title="rateError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="源币种"><el-input v-model="rateForm.from_currency" maxlength="8" /></el-form-item>
        <el-form-item label="目标币种"><el-input v-model="rateForm.to_currency" maxlength="8" /></el-form-item>
        <el-form-item label="汇率" required><el-input v-model="rateForm.rate" placeholder="如 7.1234" /></el-form-item>
        <el-form-item label="生效日期" required>
          <el-date-picker v-model="rateForm.effective_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="来源"><el-input v-model="rateForm.source" maxlength="64" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rateVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
