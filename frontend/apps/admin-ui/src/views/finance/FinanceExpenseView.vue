<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  fileApi,
  financeApi,
  type FinanceBudget,
  type FinanceBudgetAdjustment,
  type FinanceBudgetControlResult,
  type FinanceExpense,
  type FinanceInvoice,
  type FinanceVoucherAttachment,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const activeTab = ref('expenses')
const loading = ref(false)
const error = ref('')

const expenses = ref<FinanceExpense[]>([])
const expenseStatus = ref('')
const budgets = ref<FinanceBudget[]>([])
const budgetYear = ref(new Date().getFullYear())
const invoices = ref<FinanceInvoice[]>([])

const detailVisible = ref(false)
const detail = ref<FinanceExpense | null>(null)

const budgetVisible = ref(false)
const budgetError = ref('')
const budgetForm = reactive({
  budget_no: '',
  budget_name: '',
  period_year: new Date().getFullYear(),
  dimension: 'DEPT',
  dimension_id: '',
  subject_code: '',
  budget_amount: '',
  control_mode: 'WARN',
  currency_code: 'CNY',
})

const checkVisible = ref(false)
const checkError = ref('')
const checkForm = reactive({
  period_year: new Date().getFullYear(),
  dimension: 'DEPT',
  dimension_id: '',
  subject_code: '',
  amount: '',
})
const checkResult = ref<FinanceBudgetControlResult | null>(null)

const invoiceVisible = ref(false)
const invoiceError = ref('')
const invoiceForm = reactive({
  invoice_code: '',
  invoice_number: '',
  invoice_type: 'VAT_SPECIAL',
  buyer_name: '',
  seller_name: '',
  amount: '',
  tax_rate: '0.06',
  invoice_date: new Date().toISOString().slice(0, 10),
})

const invoiceAttachmentVisible = ref(false)
const invoiceAttachmentLoading = ref(false)
const invoiceAttachmentUploading = ref(false)
const invoiceAttachmentTarget = ref<FinanceInvoice | null>(null)
const invoiceAttachments = ref<FinanceVoucherAttachment[]>([])

const adjustVisible = ref(false)
const adjustError = ref('')
const adjustSaving = ref(false)
const adjustRow = ref<FinanceBudget | null>(null)
const adjustForm = reactive<{ amount: string; adjust_type: 'INCREASE' | 'DECREASE'; reason: string }>({
  amount: '',
  adjust_type: 'INCREASE',
  reason: '',
})

const rolloverVisible = ref(false)
const rolloverError = ref('')
const rolloverSaving = ref(false)
const rolloverLoading = ref(false)
const rolloverRow = ref<FinanceBudget | null>(null)
const rolloverTargets = ref<FinanceBudget[]>([])
const rolloverForm = reactive({ target_budget_id: '', amount: '', reason: '' })

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRow = ref<FinanceBudget | null>(null)
const adjustments = ref<FinanceBudgetAdjustment[]>([])

const expenseStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '审批中',
  APPROVED: '已审批',
  REJECTED: '已驳回',
  PAID: '已付款',
}
const invoiceStatusLabels: Record<string, string> = {
  PENDING: '待查验',
  VERIFIED: '已查验',
  INVALID: '无效',
}
const budgetModeLabels: Record<string, string> = {
  STRICT: '严格控制',
  WARN: '超支预警',
  NONE: '不控制',
}
const adjustmentTypeLabels: Record<string, string> = {
  INCREASE: '追加',
  DECREASE: '调减',
}

const gapItems = [
  '报销单列表只支持状态过滤，没有按申请人/部门/项目过滤参数；员工端只能按 applicant_id 在前端过滤本人单据。',
  '发票查验为系统内部一致性校验（必填、金额、同租户代码+号码唯一），未接入税务/第三方查验服务。',
]

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function expenseStatusLabel(value?: string) {
  return value ? expenseStatusLabels[value] ?? value : '-'
}

function invoiceStatusLabel(value?: string) {
  return value ? invoiceStatusLabels[value] ?? value : '-'
}

async function loadExpenses() {
  loading.value = true
  error.value = ''
  try {
    expenses.value = await financeApi.expenses.list({ status: expenseStatus.value || undefined, size: 100 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadBudgets() {
  loading.value = true
  error.value = ''
  try {
    budgets.value = await financeApi.expenses.budgets({ periodYear: budgetYear.value, size: 100 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadInvoices() {
  loading.value = true
  error.value = ''
  try {
    invoices.value = await financeApi.expenses.invoices({ size: 100 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: FinanceExpense) {
  detailVisible.value = true
  detail.value = null
  try {
    detail.value = await financeApi.expenses.detail(row.id)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function confirmAction(message: string) {
  return ElMessageBox.confirm(message, '报销操作', { type: 'warning' })
}

async function action(row: FinanceExpense, type: 'submit' | 'approve' | 'reject' | 'pay') {
  const labels = { submit: '提交', approve: '审批通过', reject: '驳回', pay: '付款' }
  try {
    await confirmAction(`确认对报销单 ${row.claim_no} 执行「${labels[type]}」？`)
  } catch {
    return
  }
  try {
    if (type === 'submit') await financeApi.expenses.submit(row.id)
    else if (type === 'approve') await financeApi.expenses.approve(row.id)
    else if (type === 'reject') await financeApi.expenses.reject(row.id, '管理端驳回')
    else await financeApi.expenses.pay(row.id, { payment_date: new Date().toISOString().slice(0, 10) })
    ElMessage.success(`${row.claim_no} 已${labels[type]}`)
    await loadExpenses()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitBudget() {
  if (!budgetForm.budget_no.trim() || !budgetForm.budget_name.trim() || !budgetForm.budget_amount) {
    budgetError.value = '预算编号、名称与金额不能为空'
    return
  }
  try {
    await financeApi.expenses.createBudget({
      budget_no: budgetForm.budget_no.trim(),
      budget_name: budgetForm.budget_name.trim(),
      period_year: Number(budgetForm.period_year),
      dimension: budgetForm.dimension,
      dimension_id: budgetForm.dimension_id ? budgetForm.dimension_id : null,
      subject_code: budgetForm.subject_code.trim() || undefined,
      budget_amount: budgetForm.budget_amount,
      control_mode: budgetForm.control_mode,
      currency_code: budgetForm.currency_code,
    })
    ElMessage.success('预算已创建')
    budgetVisible.value = false
    await loadBudgets()
  } catch (err) {
    budgetError.value = describeError(err)
  }
}

async function releaseBudget(row: FinanceBudget) {
  try {
    await confirmAction(`确认释放预算 ${row.budget_no}（${row.budget_name}）？`)
  } catch {
    return
  }
  try {
    const updated = await financeApi.expenses.releaseBudget(row.id)
    ElMessage.success(`预算 ${updated.budget_no} 状态 ${updated.status}`)
    await loadBudgets()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openAdjust(row: FinanceBudget) {
  adjustRow.value = row
  adjustError.value = ''
  adjustForm.amount = ''
  adjustForm.adjust_type = 'INCREASE'
  adjustForm.reason = ''
  adjustVisible.value = true
}

async function submitAdjust() {
  if (!adjustRow.value) return
  if (!adjustForm.amount.trim()) {
    adjustError.value = '请输入调整金额'
    return
  }
  adjustSaving.value = true
  adjustError.value = ''
  try {
    const updated = await financeApi.expenses.adjustBudget(adjustRow.value.id, {
      amount: adjustForm.amount.trim(),
      adjust_type: adjustForm.adjust_type,
      reason: adjustForm.reason.trim() || undefined,
    })
    ElMessage.success(`预算 ${updated.budget_no} 已${adjustmentTypeLabels[adjustForm.adjust_type]}，当前预算额 ${money(updated.budget_amount)}`)
    adjustVisible.value = false
    await loadBudgets()
  } catch (err) {
    adjustError.value = describeError(err)
  } finally {
    adjustSaving.value = false
  }
}

async function openRollover(row: FinanceBudget) {
  rolloverRow.value = row
  rolloverError.value = ''
  rolloverForm.target_budget_id = ''
  rolloverForm.amount = ''
  rolloverForm.reason = ''
  rolloverTargets.value = []
  rolloverVisible.value = true
  rolloverLoading.value = true
  try {
    const list = await financeApi.expenses.budgets({ periodYear: row.period_year, size: 100 })
    rolloverTargets.value = list.filter((budget) => budget.id !== row.id && budget.period_year === row.period_year)
  } catch (err) {
    rolloverError.value = describeError(err)
  } finally {
    rolloverLoading.value = false
  }
}

async function submitRollover() {
  if (!rolloverRow.value) return
  if (!rolloverForm.target_budget_id) {
    rolloverError.value = '请选择目标预算'
    return
  }
  rolloverSaving.value = true
  rolloverError.value = ''
  try {
    const target = await financeApi.expenses.rolloverBudget(rolloverRow.value.id, {
      target_budget_id: rolloverForm.target_budget_id,
      amount: rolloverForm.amount.trim() || undefined,
      reason: rolloverForm.reason.trim() || undefined,
    })
    ElMessage.success(
      `结转完成，转入预算 ${target.budget_no}（${target.budget_name}）可用额 ${money(target.available)}`,
    )
    rolloverVisible.value = false
    await loadBudgets()
  } catch (err) {
    rolloverError.value = describeError(err)
  } finally {
    rolloverSaving.value = false
  }
}

async function openHistory(row: FinanceBudget) {
  historyRow.value = row
  adjustments.value = []
  historyVisible.value = true
  historyLoading.value = true
  try {
    adjustments.value = await financeApi.expenses.budgetAdjustments(row.id)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    historyLoading.value = false
  }
}

async function runControlCheck() {
  if (!checkForm.amount) {
    checkError.value = '请输入校验金额'
    return
  }
  checkError.value = ''
  try {
    checkResult.value = await financeApi.expenses.checkBudget({
      period_year: Number(checkForm.period_year),
      dimension: checkForm.dimension,
      dimension_id: checkForm.dimension_id ? checkForm.dimension_id : null,
      subject_code: checkForm.subject_code.trim() || undefined,
      amount: checkForm.amount,
    })
  } catch (err) {
    checkError.value = describeError(err)
    checkResult.value = null
  }
}

async function submitInvoice() {
  if (!invoiceForm.invoice_code.trim() || !invoiceForm.invoice_number.trim() || !invoiceForm.amount) {
    invoiceError.value = '发票代码、号码与金额不能为空'
    return
  }
  try {
    await financeApi.expenses.createInvoice({
      invoice_code: invoiceForm.invoice_code.trim(),
      invoice_number: invoiceForm.invoice_number.trim(),
      invoice_type: invoiceForm.invoice_type,
      buyer_name: invoiceForm.buyer_name.trim() || undefined,
      seller_name: invoiceForm.seller_name.trim() || undefined,
      amount: invoiceForm.amount,
      tax_rate: invoiceForm.tax_rate,
      invoice_date: invoiceForm.invoice_date,
    })
    ElMessage.success('发票已登记')
    invoiceVisible.value = false
    await loadInvoices()
  } catch (err) {
    invoiceError.value = describeError(err)
  }
}

async function verifyInvoice(row: FinanceInvoice) {
  try {
    const updated = await financeApi.expenses.verifyInvoice(row.id)
    ElMessage.success(`发票 ${updated.invoice_number} 查验结果：${invoiceStatusLabel(updated.verify_status)}`)
    await loadInvoices()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function openInvoiceAttachments(row: FinanceInvoice) {
  invoiceAttachmentTarget.value = row
  invoiceAttachmentVisible.value = true
  invoiceAttachmentLoading.value = true
  invoiceAttachments.value = []
  try {
    invoiceAttachments.value = await financeApi.expenses.invoiceAttachments(row.id)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    invoiceAttachmentLoading.value = false
  }
}

async function uploadInvoiceAttachment(file: { raw?: File }) {
  if (!invoiceAttachmentTarget.value || !file.raw || invoiceAttachmentUploading.value) return
  invoiceAttachmentUploading.value = true
  try {
    const uploaded = await fileApi.upload(file.raw, {
      biz_type: 'FINANCE_INVOICE',
      biz_id: invoiceAttachmentTarget.value.id,
    })
    invoiceAttachments.value = await financeApi.expenses.addInvoiceAttachment(invoiceAttachmentTarget.value.id, {
      file_id: uploaded.file_id,
      file_name: file.raw.name,
      file_size: file.raw.size,
    })
    ElMessage.success('发票附件已上传')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    invoiceAttachmentUploading.value = false
  }
}

async function removeInvoiceAttachment(row: FinanceVoucherAttachment) {
  if (!invoiceAttachmentTarget.value) return
  try {
    await ElMessageBox.confirm(`确认删除附件 ${row.file_name || row.file_id}？`, '删除附件', { type: 'warning' })
  } catch {
    return
  }
  try {
    invoiceAttachments.value = await financeApi.expenses.deleteInvoiceAttachment(
      invoiceAttachmentTarget.value.id,
      row.id,
    )
    ElMessage.success('发票附件已删除')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function downloadInvoiceAttachment(row: FinanceVoucherAttachment) {
  try {
    const result = await fileApi.download(row.file_id)
    window.open(result.download_url, '_blank', 'noopener')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function formatFileSize(size?: number | null) {
  if (size === undefined || size === null) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function changeTab(name: string | number) {
  if (name === 'expenses') loadExpenses()
  else if (name === 'budgets') loadBudgets()
  else loadInvoices()
}

onMounted(async () => {
  await loadExpenses()
})
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="费用报销与预算"
      description="报销审批与付款、预算编制/释放/控制校验、发票登记与查验；全部调用真实接口。"
    >
      <template #actions>
        <el-button :loading="loading" @click="changeTab(activeTab)">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice v-if="gapItems.length" :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="changeTab">
        <el-tab-pane label="报销单" name="expenses">
          <div class="fl-toolbar">
            <el-select v-model="expenseStatus" placeholder="状态" clearable style="width: 140px" @change="loadExpenses">
              <el-option v-for="(label, value) in expenseStatusLabels" :key="value" :label="label" :value="value" />
            </el-select>
            <span class="fl-muted">共 {{ expenses.length }} 张报销单</span>
          </div>
          <el-table :data="expenses" v-loading="loading" border stripe>
            <el-table-column prop="claim_no" label="报销单号" width="180" />
            <el-table-column prop="claim_date" label="申请日期" width="120" />
            <el-table-column prop="applicant_name" label="申请人" width="120" />
            <el-table-column prop="claim_type" label="类型" width="120" />
            <el-table-column prop="currency_code" label="币种" width="80" />
            <el-table-column label="金额" width="130" align="right"><template #default="{ row }">{{ money(row.total_amount) }}</template></el-table-column>
            <el-table-column label="税额" width="120" align="right"><template #default="{ row }">{{ money(row.tax_amount) }}</template></el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag size="small">{{ expenseStatusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link @click="openDetail(row)">详情</el-button>
                <el-button v-if="row.status === 'DRAFT'" v-perm="'biz:finance:expense:submit'" link type="primary" @click="action(row, 'submit')">提交</el-button>
                <el-button v-if="row.status === 'SUBMITTED'" v-perm="'biz:finance:expense:approve'" link type="success" @click="action(row, 'approve')">通过</el-button>
                <el-button v-if="row.status === 'SUBMITTED'" v-perm="'biz:finance:expense:approve'" link type="danger" @click="action(row, 'reject')">驳回</el-button>
                <el-button v-if="row.status === 'APPROVED'" v-perm="'biz:finance:expense:pay'" link type="warning" @click="action(row, 'pay')">付款</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="预算" name="budgets">
          <div class="fl-toolbar">
            <el-input-number v-model="budgetYear" :min="2000" :max="2100" @change="loadBudgets" />
            <el-button v-perm="'tenant:finance:budget:manage'" type="primary" plain @click="budgetVisible = true">新建预算</el-button>
            <el-button v-perm="'biz:finance:budget:control'" plain @click="checkVisible = true; checkResult = null">预算校验</el-button>
            <span class="fl-muted">共 {{ budgets.length }} 条预算</span>
          </div>
          <el-table :data="budgets" v-loading="loading" border stripe>
            <el-table-column prop="budget_no" label="预算编号" width="170" />
            <el-table-column prop="budget_name" label="预算名称" min-width="150" />
            <el-table-column prop="dimension" label="维度" width="100" />
            <el-table-column prop="subject_code" label="科目" width="110" />
            <el-table-column label="预算额" width="130" align="right"><template #default="{ row }">{{ money(row.budget_amount) }}</template></el-table-column>
            <el-table-column label="占用" width="130" align="right"><template #default="{ row }">{{ money(row.occupied_amount) }}</template></el-table-column>
            <el-table-column label="执行" width="130" align="right"><template #default="{ row }">{{ money(row.executed_amount) }}</template></el-table-column>
            <el-table-column label="可用" width="130" align="right"><template #default="{ row }">{{ money(row.available) }}</template></el-table-column>
            <el-table-column label="控制方式" width="110">
              <template #default="{ row }">{{ budgetModeLabels[row.control_mode] ?? row.control_mode }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'tenant:finance:budget:manage'" link type="primary" @click="openAdjust(row)">调整</el-button>
                <el-button v-perm="'tenant:finance:budget:manage'" link type="primary" @click="openRollover(row)">结转</el-button>
                <el-button link @click="openHistory(row)">调整历史</el-button>
                <el-button v-perm="'tenant:finance:budget:manage'" link type="warning" @click="releaseBudget(row)">释放</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="发票" name="invoices">
          <div class="fl-toolbar">
            <el-button v-perm="'biz:finance:invoice:manage'" type="primary" plain @click="invoiceVisible = true">登记发票</el-button>
            <span class="fl-muted">共 {{ invoices.length }} 张发票</span>
          </div>
          <el-table :data="invoices" v-loading="loading" border stripe>
            <el-table-column prop="invoice_code" label="发票代码" width="160" />
            <el-table-column prop="invoice_number" label="发票号码" width="150" />
            <el-table-column prop="invoice_type" label="类型" width="140" />
            <el-table-column prop="seller_name" label="销售方" min-width="160" />
            <el-table-column label="价税合计" width="130" align="right"><template #default="{ row }">{{ money(row.total_amount) }}</template></el-table-column>
            <el-table-column prop="invoice_date" label="开票日期" width="120" />
            <el-table-column label="查验" width="100">
              <template #default="{ row }">{{ invoiceStatusLabel(row.verify_status) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'biz:finance:invoice:manage'" link type="primary" @click="verifyInvoice(row)">查验</el-button>
                <el-button v-perm="'biz:finance:invoice:manage'" link @click="openInvoiceAttachments(row)">附件</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-drawer v-model="detailVisible" title="报销单详情" size="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="单号">{{ detail.claim_no }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.applicant_name }}</el-descriptions-item>
          <el-descriptions-item label="日期">{{ detail.claim_date }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ expenseStatusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ money(detail.total_amount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ money(detail.tax_amount) }}</el-descriptions-item>
          <el-descriptions-item label="预算占用">{{ money(detail.budget_occupied) }}</el-descriptions-item>
          <el-descriptions-item label="关联应付">{{ detail.payable_id ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin: 16px 0 8px">费用明细</h4>
        <el-table :data="detail.items" border size="small">
          <el-table-column prop="line_no" label="#" width="50" />
          <el-table-column prop="expense_date" label="日期" width="110" />
          <el-table-column prop="expense_category" label="类别" width="120" />
          <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
          <el-table-column label="税额" width="110" align="right"><template #default="{ row }">{{ money(row.tax_amount) }}</template></el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140" />
        </el-table>
      </template>
      <el-empty v-else description="未获取到详情" />
    </el-drawer>

    <el-dialog v-model="budgetVisible" title="新建预算" width="540px">
      <el-alert v-if="budgetError" type="error" :closable="false" show-icon :title="budgetError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="预算编号" required><el-input v-model="budgetForm.budget_no" maxlength="32" /></el-form-item>
        <el-form-item label="预算名称" required><el-input v-model="budgetForm.budget_name" maxlength="64" /></el-form-item>
        <el-form-item label="年度"><el-input-number v-model="budgetForm.period_year" :min="2000" :max="2100" style="width: 100%" /></el-form-item>
        <el-form-item label="维度">
          <el-select v-model="budgetForm.dimension" style="width: 100%">
            <el-option label="部门" value="DEPT" />
            <el-option label="项目" value="PROJECT" />
            <el-option label="公司" value="COMPANY" />
          </el-select>
        </el-form-item>
        <el-form-item label="维度 ID"><el-input v-model="budgetForm.dimension_id" placeholder="可选" /></el-form-item>
        <el-form-item label="科目编码"><el-input v-model="budgetForm.subject_code" maxlength="32" /></el-form-item>
        <el-form-item label="预算金额" required><el-input v-model="budgetForm.budget_amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="控制方式">
          <el-select v-model="budgetForm.control_mode" style="width: 100%">
            <el-option label="严格控制" value="STRICT" />
            <el-option label="超支预警" value="WARN" />
            <el-option label="不控制" value="NONE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="budgetVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBudget">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkVisible" title="预算控制校验" width="520px">
      <el-alert v-if="checkError" type="error" :closable="false" show-icon :title="checkError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="年度"><el-input-number v-model="checkForm.period_year" :min="2000" :max="2100" style="width: 100%" /></el-form-item>
        <el-form-item label="维度">
          <el-select v-model="checkForm.dimension" style="width: 100%">
            <el-option label="部门" value="DEPT" />
            <el-option label="项目" value="PROJECT" />
            <el-option label="公司" value="COMPANY" />
          </el-select>
        </el-form-item>
        <el-form-item label="维度 ID"><el-input v-model="checkForm.dimension_id" /></el-form-item>
        <el-form-item label="科目编码"><el-input v-model="checkForm.subject_code" /></el-form-item>
        <el-form-item label="校验金额" required><el-input v-model="checkForm.amount" placeholder="0.00" /></el-form-item>
      </el-form>
      <el-alert
        v-if="checkResult"
        :type="checkResult.allowed ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="checkResult.allowed ? '预算校验通过' : '预算不足/超控'"
        style="margin-top: 8px"
      >
        <div>控制方式：{{ checkResult.control_mode || '-' }}；可用：{{ money(checkResult.available) }}</div>
        <div v-if="checkResult.message">{{ checkResult.message }}</div>
      </el-alert>
      <template #footer>
        <el-button @click="checkVisible = false">关闭</el-button>
        <el-button type="primary" @click="runControlCheck">校验</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="invoiceAttachmentVisible" title="发票附件" width="680px">
      <div class="fl-toolbar" style="margin-bottom: 10px">
        <span class="fl-muted">
          发票 {{ invoiceAttachmentTarget?.invoice_code || '-' }} / {{ invoiceAttachmentTarget?.invoice_number || '-' }}
        </span>
        <el-upload :auto-upload="false" :show-file-list="false" :on-change="uploadInvoiceAttachment">
          <el-button size="small" plain :loading="invoiceAttachmentUploading">上传附件</el-button>
        </el-upload>
      </div>
      <el-table :data="invoiceAttachments" v-loading="invoiceAttachmentLoading" border size="small" empty-text="暂无附件">
        <el-table-column prop="file_name" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatFileSize(row.file_size) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link type="primary" @click="downloadInvoiceAttachment(row)">下载</el-button>
            <el-button link type="danger" @click="removeInvoiceAttachment(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="invoiceAttachmentVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="invoiceVisible" title="登记发票" width="540px">
      <el-alert v-if="invoiceError" type="error" :closable="false" show-icon :title="invoiceError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="发票代码" required><el-input v-model="invoiceForm.invoice_code" maxlength="32" /></el-form-item>
        <el-form-item label="发票号码" required><el-input v-model="invoiceForm.invoice_number" maxlength="32" /></el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="invoiceForm.invoice_type" style="width: 100%">
            <el-option label="增值税专用发票" value="VAT_SPECIAL" />
            <el-option label="增值税普通发票" value="VAT_NORMAL" />
            <el-option label="电子普票" value="ELECTRONIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="购买方"><el-input v-model="invoiceForm.buyer_name" maxlength="128" /></el-form-item>
        <el-form-item label="销售方"><el-input v-model="invoiceForm.seller_name" maxlength="128" /></el-form-item>
        <el-form-item label="不含税金额" required><el-input v-model="invoiceForm.amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="税率"><el-input v-model="invoiceForm.tax_rate" maxlength="8" /></el-form-item>
        <el-form-item label="开票日期">
          <el-date-picker v-model="invoiceForm.invoice_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invoiceVisible = false">取消</el-button>
        <el-button type="primary" @click="submitInvoice">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="adjustVisible" title="预算调整" width="520px">
      <el-alert v-if="adjustError" type="error" :closable="false" show-icon :title="adjustError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="预算">
          <span>{{ adjustRow ? `${adjustRow.budget_no} ${adjustRow.budget_name}` : '-' }}</span>
        </el-form-item>
        <el-form-item label="调整类型">
          <el-select v-model="adjustForm.adjust_type" style="width: 100%">
            <el-option label="追加（INCREASE）" value="INCREASE" />
            <el-option label="调减（DECREASE）" value="DECREASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整金额" required><el-input v-model="adjustForm.amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="adjustForm.reason" type="textarea" :rows="2" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustSaving" @click="submitAdjust">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rolloverVisible" title="预算结转" width="540px">
      <el-alert v-if="rolloverError" type="error" :closable="false" show-icon :title="rolloverError" style="margin-bottom: 12px" />
      <el-form label-width="100px" v-loading="rolloverLoading">
        <el-form-item label="转出预算">
          <span>{{ rolloverRow ? `${rolloverRow.budget_no} ${rolloverRow.budget_name}（可用 ${money(rolloverRow.available)}）` : '-' }}</span>
        </el-form-item>
        <el-form-item label="目标预算" required>
          <el-select v-model="rolloverForm.target_budget_id" filterable placeholder="选择同年度其它预算" style="width: 100%">
            <el-option
              v-for="budget in rolloverTargets"
              :key="budget.id"
              :label="`${budget.budget_no} ${budget.budget_name}（可用 ${money(budget.available)}）`"
              :value="budget.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="结转金额"><el-input v-model="rolloverForm.amount" placeholder="留空表示全部可用额" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="rolloverForm.reason" type="textarea" :rows="2" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rolloverVisible = false">取消</el-button>
        <el-button type="primary" :loading="rolloverSaving" @click="submitRollover">结转</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyVisible" title="预算调整历史" width="900px">
      <div class="fl-muted" style="margin-bottom: 8px">
        {{ historyRow ? `${historyRow.budget_no} ${historyRow.budget_name}` : '' }}
      </div>
      <el-table :data="adjustments" v-loading="historyLoading" border size="small" empty-text="暂无调整记录">
        <el-table-column prop="adjust_no" label="单号" width="170" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ adjustmentTypeLabels[row.adjust_type] ?? row.adjust_type }}</template>
        </el-table-column>
        <el-table-column label="调整额" width="130" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column label="调整前" width="130" align="right"><template #default="{ row }">{{ money(row.before_amount) }}</template></el-table-column>
        <el-table-column label="调整后" width="130" align="right"><template #default="{ row }">{{ money(row.after_amount) }}</template></el-table-column>
        <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="occurred_at" label="时间" width="170" />
      </el-table>
      <template #footer>
        <el-button @click="historyVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
