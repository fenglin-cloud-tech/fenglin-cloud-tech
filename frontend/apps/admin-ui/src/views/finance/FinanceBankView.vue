<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  financeApi,
  type FinanceBankAccount,
  type FinanceBankTransaction,
  type FinanceReconciliation,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const accounts = ref<FinanceBankAccount[]>([])
const transactions = ref<FinanceBankTransaction[]>([])
const reconciliations = ref<FinanceReconciliation[]>([])
const selectedAccountId = ref<number | ''>('')
const activeTab = ref('accounts')
const loading = ref(false)
const error = ref('')

const accountVisible = ref(false)
const accountError = ref('')
const accountForm = reactive({
  account_no: '',
  account_name: '',
  bank_name: '',
  bank_code: '',
  currency_code: 'CNY',
  account_type: 'BASIC',
  subject_code: '',
  balance: '',
})

const importVisible = ref(false)
const importError = ref('')
const importRows = ref<Array<{ txn_no: string; txn_date: string; direction: 'IN' | 'OUT'; amount: string; counterparty: string; summary: string }>>([])

const reconVisible = ref(false)
const reconError = ref('')
const reconForm = reactive({ account_id: '' as number | '', period_code: '' })

const gapItems = [
  '银行账户没有修改/停用接口，只能新增与查询；账户余额由后端维护。',
  '流水导入只接受后端 JSON 结构，没有 Excel/CSV 解析接口，页面按行录入后提交。',
  '对账结果没有未达项明细接口，无法在页面勾选未达账项；只能查看对账汇总与未匹配数量。',
]

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadAccounts() {
  loading.value = true
  error.value = ''
  try {
    accounts.value = await financeApi.bank.accounts()
    if (!selectedAccountId.value && accounts.value.length) {
      selectedAccountId.value = accounts.value[0].id
    }
    await loadTransactions()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadTransactions() {
  if (!selectedAccountId.value) {
    transactions.value = []
    return
  }
  try {
    transactions.value = await financeApi.bank.transactions(Number(selectedAccountId.value), { size: 100 })
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadReconciliations() {
  try {
    reconciliations.value = await financeApi.bank.reconciliations({ size: 50 })
  } catch (err) {
    error.value = describeError(err)
  }
}

async function submitAccount() {
  if (!accountForm.account_no.trim() || !accountForm.account_name.trim() || !accountForm.bank_name.trim()) {
    accountError.value = '账号、账户名称与开户行不能为空'
    return
  }
  try {
    await financeApi.bank.createAccount({
      account_no: accountForm.account_no.trim(),
      account_name: accountForm.account_name.trim(),
      bank_name: accountForm.bank_name.trim(),
      bank_code: accountForm.bank_code.trim() || undefined,
      currency_code: accountForm.currency_code,
      account_type: accountForm.account_type,
      subject_code: accountForm.subject_code.trim() || undefined,
      balance: accountForm.balance || undefined,
    })
    ElMessage.success('银行账户已创建')
    accountVisible.value = false
    await loadAccounts()
  } catch (err) {
    accountError.value = describeError(err)
  }
}

function addImportRow() {
  importRows.value.push({
    txn_no: '',
    txn_date: new Date().toISOString().slice(0, 10),
    direction: 'IN',
    amount: '',
    counterparty: '',
    summary: '',
  })
}

async function submitImport() {
  if (!selectedAccountId.value) {
    importError.value = '请先选择银行账户'
    return
  }
  if (!importRows.value.length) {
    importError.value = '请至少添加一条流水'
    return
  }
  if (importRows.value.some((row) => !row.txn_no || !row.txn_date || !row.amount)) {
    importError.value = '流水号、日期与金额不能为空'
    return
  }
  try {
    const result = await financeApi.bank.importTransactions({
      account_id: Number(selectedAccountId.value),
      transactions: importRows.value.map((row) => ({
        txn_no: row.txn_no.trim(),
        txn_date: row.txn_date,
        direction: row.direction,
        amount: row.amount,
        counterparty: row.counterparty.trim() || undefined,
        summary: row.summary.trim() || undefined,
      })),
    })
    ElMessage.success(`导入 ${result.imported} 条，跳过 ${result.skipped} 条`)
    importVisible.value = false
    importRows.value = []
    await loadTransactions()
  } catch (err) {
    importError.value = describeError(err)
  }
}

async function submitReconcile() {
  if (!reconForm.account_id || !reconForm.period_code.trim()) {
    reconError.value = '账户与期间不能为空'
    return
  }
  try {
    const result = await financeApi.bank.reconcile({
      account_id: Number(reconForm.account_id),
      period_code: reconForm.period_code.trim(),
    })
    ElMessage.success(`对账单 ${result.recon_no} 已生成，未匹配 ${result.unmatched_count} 条`)
    reconVisible.value = false
    await loadReconciliations()
  } catch (err) {
    reconError.value = describeError(err)
  }
}

function changeTab(name: string | number) {
  if (name === 'accounts') loadAccounts()
  else if (name === 'transactions') loadTransactions()
  else loadReconciliations()
}

onMounted(async () => {
  await loadAccounts()
  await loadReconciliations()
})
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="银企对账与资金"
      description="银行账户、流水导入与查询、按期间生成银行余额调节表。"
    >
      <template #actions>
        <el-button v-perm="'biz:finance:bank:manage'" type="primary" @click="accountVisible = true">新增账户</el-button>
        <el-button :loading="loading" @click="changeTab(activeTab)">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="changeTab">
        <el-tab-pane label="银行账户" name="accounts">
          <el-table :data="accounts" v-loading="loading" border stripe>
            <el-table-column prop="account_no_masked" label="账号" width="200" />
            <el-table-column prop="account_name" label="账户名称" min-width="180" />
            <el-table-column prop="bank_name" label="开户行" min-width="180" />
            <el-table-column prop="currency_code" label="币种" width="80" />
            <el-table-column prop="account_type" label="账户类型" width="110" />
            <el-table-column label="余额" width="140" align="right"><template #default="{ row }">{{ money(row.balance) }}</template></el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="银行流水" name="transactions">
          <div class="fl-toolbar">
            <el-select v-model="selectedAccountId" placeholder="选择账户" style="width: 260px" @change="loadTransactions">
              <el-option v-for="account in accounts" :key="account.id" :label="`${account.account_no_masked} ${account.account_name}`" :value="account.id" />
            </el-select>
            <el-button v-perm="'biz:finance:bank:manage'" type="primary" plain @click="importVisible = true; if (!importRows.length) addImportRow()">导入流水</el-button>
            <span class="fl-muted">共 {{ transactions.length }} 条</span>
          </div>
          <el-table :data="transactions" v-loading="loading" border stripe max-height="520">
            <el-table-column prop="txn_no" label="流水号" width="170" />
            <el-table-column prop="txn_date" label="交易日期" width="120" />
            <el-table-column label="方向" width="80">
              <template #default="{ row }">{{ row.direction === 'IN' ? '收入' : '支出' }}</template>
            </el-table-column>
            <el-table-column label="金额" width="130" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column label="余额" width="130" align="right"><template #default="{ row }">{{ money(row.balance) }}</template></el-table-column>
            <el-table-column prop="counterparty" label="对方户名" min-width="160" />
            <el-table-column prop="summary" label="摘要" min-width="150" show-overflow-tooltip />
            <el-table-column prop="reconcile_status" label="对账状态" width="110" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="银行对账" name="reconcile">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:finance:reconcile:manage'" type="primary" plain @click="reconVisible = true">生成对账单</el-button>
            <span class="fl-muted">共 {{ reconciliations.length }} 份对账单</span>
          </div>
          <el-table :data="reconciliations" border stripe>
            <el-table-column prop="recon_no" label="对账单号" width="170" />
            <el-table-column prop="period_code" label="期间" width="100" />
            <el-table-column label="账面余额" width="130" align="right"><template #default="{ row }">{{ money(row.book_balance) }}</template></el-table-column>
            <el-table-column label="银行余额" width="130" align="right"><template #default="{ row }">{{ money(row.bank_balance) }}</template></el-table-column>
            <el-table-column label="未达收入" width="130" align="right"><template #default="{ row }">{{ money(row.unsettled_in) }}</template></el-table-column>
            <el-table-column label="未达支出" width="130" align="right"><template #default="{ row }">{{ money(row.unsettled_out) }}</template></el-table-column>
            <el-table-column label="调整后余额" width="140" align="right"><template #default="{ row }">{{ money(row.adjusted_balance) }}</template></el-table-column>
            <el-table-column prop="unmatched_count" label="未匹配" width="90" />
            <el-table-column prop="status" label="状态" width="110" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="accountVisible" title="新增银行账户" width="520px">
      <el-alert v-if="accountError" type="error" :closable="false" show-icon :title="accountError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="银行账号" required><el-input v-model="accountForm.account_no" maxlength="64" /></el-form-item>
        <el-form-item label="账户名称" required><el-input v-model="accountForm.account_name" maxlength="128" /></el-form-item>
        <el-form-item label="开户行" required><el-input v-model="accountForm.bank_name" maxlength="128" /></el-form-item>
        <el-form-item label="银行编码"><el-input v-model="accountForm.bank_code" maxlength="32" /></el-form-item>
        <el-form-item label="币种"><el-input v-model="accountForm.currency_code" maxlength="8" /></el-form-item>
        <el-form-item label="账户类型"><el-input v-model="accountForm.account_type" maxlength="32" /></el-form-item>
        <el-form-item label="会计科目"><el-input v-model="accountForm.subject_code" maxlength="32" /></el-form-item>
        <el-form-item label="期初余额"><el-input v-model="accountForm.balance" placeholder="0.00" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAccount">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="导入银行流水" width="800px">
      <el-alert v-if="importError" type="error" :closable="false" show-icon :title="importError" style="margin-bottom: 12px" />
      <el-table :data="importRows" border size="small">
        <el-table-column label="流水号" width="150"><template #default="{ row }"><el-input v-model="row.txn_no" /></template></el-table-column>
        <el-table-column label="日期" width="140">
          <template #default="{ row }"><el-date-picker v-model="row.txn_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></template>
        </el-table-column>
        <el-table-column label="方向" width="90">
          <template #default="{ row }">
            <el-select v-model="row.direction">
              <el-option label="收入" value="IN" />
              <el-option label="支出" value="OUT" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120"><template #default="{ row }"><el-input v-model="row.amount" /></template></el-table-column>
        <el-table-column label="对方户名" min-width="130"><template #default="{ row }"><el-input v-model="row.counterparty" /></template></el-table-column>
        <el-table-column label="摘要" min-width="130"><template #default="{ row }"><el-input v-model="row.summary" /></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="addImportRow">增加一行</el-button>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="submitImport">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reconVisible" title="生成银行对账单" width="460px">
      <el-alert v-if="reconError" type="error" :closable="false" show-icon :title="reconError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="银行账户" required>
          <el-select v-model="reconForm.account_id" style="width: 100%">
            <el-option v-for="account in accounts" :key="account.id" :label="account.account_name" :value="account.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="期间" required><el-input v-model="reconForm.period_code" placeholder="202611" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reconVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReconcile">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>
