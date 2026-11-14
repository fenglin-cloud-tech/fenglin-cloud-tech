<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH } from '@fenglin/ui-admin'
import {
  financeApi,
  type FinanceId,
  type FinanceAgingReport,
  type FinancePayable,
  type FinancePaymentDoc,
  type FinancePaymentPlanLine,
  type FinanceReceivable,
  type FinanceReceiptDoc,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const receivables = ref<FinanceReceivable[]>([])
const payables = ref<FinancePayable[]>([])
const aging = ref<FinanceAgingReport | null>(null)
const paymentPlan = ref<FinancePaymentPlanLine[]>([])
const activeTab = ref('ar')
const agingType = ref<'AR' | 'AP'>('AR')
const loading = ref(false)
const error = ref('')

const receiptVisible = ref(false)
const receiptError = ref('')
const receiptForm = reactive({
  customer_name: '',
  amount: '',
  receipt_date: new Date().toISOString().slice(0, 10),
  pay_method: 'BANK_TRANSFER',
  receivable_ids: [] as FinanceId[],
})

const paymentVisible = ref(false)
const paymentError = ref('')
const paymentForm = reactive({
  supplier_name: '',
  amount: '',
  payment_date: new Date().toISOString().slice(0, 10),
  pay_method: 'BANK_TRANSFER',
  payable_ids: [] as FinanceId[],
})

const writeOffVisible = ref(false)
const writeOffError = ref('')
const writeOffContext = ref<{ bizType: 'AR' | 'AP'; id: FinanceId; no: string; outstanding: string } | null>(null)
const writeOffForm = reactive({ amount: '', write_off_date: new Date().toISOString().slice(0, 10) })

const statusLabels: Record<string, string> = {
  OPEN: '未核销',
  PARTIAL: '部分核销',
  SETTLED: '已核销',
  OVERDUE: '逾期',
  CLOSED: '已关闭',
}

const settlementKind = ref<'receipt' | 'payment'>('receipt')
const settlementStatus = ref('')
const settlementRange = ref<[string, string] | null>(null)
const receiptDocs = ref<FinanceReceiptDoc[]>([])
const paymentDocs = ref<FinancePaymentDoc[]>([])
const settlementLoading = ref(false)
const settlementError = ref('')

const settlementStatusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'CONFIRMED', label: '已确认' },
]

const gapItems = [
  '收付款单按单号/日期/状态查询，客户与供应商主数据不在云财服务内，只能显示关联 ID。',
  '坏账核销、催收任务联动接口缺失；账龄报表按后端返回的分桶原样展示。',
]

async function loadSettlements() {
  settlementLoading.value = true
  settlementError.value = ''
  try {
    const params = {
      status: settlementStatus.value || undefined,
      from: settlementRange.value?.[0] || undefined,
      to: settlementRange.value?.[1] || undefined,
      limit: 100,
    }
    if (settlementKind.value === 'receipt') {
      receiptDocs.value = await financeApi.arap.receipts(params)
    } else {
      paymentDocs.value = await financeApi.arap.payments(params)
    }
  } catch (err) {
    settlementError.value = describeError(err)
  } finally {
    settlementLoading.value = false
  }
}

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [arList, apList, plan] = await Promise.all([
      financeApi.arap.receivables({ size: 100 }),
      financeApi.arap.payables({ size: 100 }),
      financeApi.arap.paymentPlan(100),
    ])
    receivables.value = arList
    payables.value = apList
    paymentPlan.value = plan
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadAging() {
  try {
    aging.value = await financeApi.arap.aging(agingType.value)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openReceipt(row?: FinanceReceivable) {
  Object.assign(receiptForm, {
    customer_name: row?.customer_name ?? '',
    amount: row?.outstanding ?? '',
    receipt_date: new Date().toISOString().slice(0, 10),
    pay_method: 'BANK_TRANSFER',
    receivable_ids: row ? [row.id] : [],
  })
  receiptError.value = ''
  receiptVisible.value = true
}

function openPayment(row?: FinancePayable) {
  Object.assign(paymentForm, {
    supplier_name: row?.supplier_name ?? '',
    amount: row?.outstanding ?? '',
    payment_date: new Date().toISOString().slice(0, 10),
    pay_method: 'BANK_TRANSFER',
    payable_ids: row ? [row.id] : [],
  })
  paymentError.value = ''
  paymentVisible.value = true
}

async function submitReceipt() {
  if (!receiptForm.amount || !receiptForm.receipt_date) {
    receiptError.value = '收款金额与日期不能为空'
    return
  }
  try {
    const doc = await financeApi.arap.receive({
      customer_name: receiptForm.customer_name.trim() || undefined,
      amount: receiptForm.amount,
      receipt_date: receiptForm.receipt_date,
      pay_method: receiptForm.pay_method,
      receivable_ids: receiptForm.receivable_ids,
    })
    ElMessage.success(`收款单 ${doc.doc_no} 已创建，状态 ${doc.status}，核销金额 ${money(doc.allocated_amount)}`)
    receiptVisible.value = false
    await loadAll()
  } catch (err) {
    receiptError.value = describeError(err)
  }
}

async function submitPayment() {
  if (!paymentForm.amount || !paymentForm.payment_date) {
    paymentError.value = '付款金额与日期不能为空'
    return
  }
  try {
    const doc = await financeApi.arap.pay({
      supplier_name: paymentForm.supplier_name.trim() || undefined,
      amount: paymentForm.amount,
      payment_date: paymentForm.payment_date,
      pay_method: paymentForm.pay_method,
      payable_ids: paymentForm.payable_ids,
    })
    ElMessage.success(`付款单 ${doc.doc_no} 已创建，状态 ${doc.status}，核销金额 ${money(doc.allocated_amount)}`)
    paymentVisible.value = false
    await loadAll()
  } catch (err) {
    paymentError.value = describeError(err)
  }
}

function openWriteOff(row: FinanceReceivable | FinancePayable, bizType: 'AR' | 'AP') {
  writeOffContext.value = {
    bizType,
    id: row.id,
    no: bizType === 'AR' ? (row as FinanceReceivable).ar_no : (row as FinancePayable).ap_no,
    outstanding: row.outstanding,
  }
  writeOffForm.amount = row.outstanding
  writeOffForm.write_off_date = new Date().toISOString().slice(0, 10)
  writeOffError.value = ''
  writeOffVisible.value = true
}

async function submitWriteOff() {
  if (!writeOffContext.value || !writeOffForm.amount) {
    writeOffError.value = '核销金额不能为空'
    return
  }
  try {
    const result = await financeApi.arap.writeOff(writeOffContext.value.bizType, writeOffContext.value.id, {
      amount: writeOffForm.amount,
      write_off_date: writeOffForm.write_off_date,
    })
    ElMessage.success(`核销单 ${result.write_off_no} 已生成，核销金额 ${money(result.amount)}`)
    writeOffVisible.value = false
    await loadAll()
  } catch (err) {
    writeOffError.value = describeError(err)
  }
}

onMounted(async () => {
  await loadAll()
  await loadAging()
})
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="应收应付与资金结算"
      description="应收应付台账、账龄分析、收款/付款与核销、应付付款计划；金额与状态均来自真实接口。"
    >
      <template #actions>
        <el-button v-perm="'biz:finance:receipt:create'" type="primary" @click="openReceipt()">登记收款</el-button>
        <el-button v-perm="'biz:finance:payment:create'" type="primary" plain @click="openPayment()">登记付款</el-button>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="应收账款" name="ar">
          <el-table :data="receivables" v-loading="loading" border stripe>
            <el-table-column prop="ar_no" label="应收单号" width="150" show-overflow-tooltip />
            <el-table-column prop="customer_name" label="客户" min-width="140" show-overflow-tooltip />
            <el-table-column prop="bill_date" label="单据日期" width="110" />
            <el-table-column prop="due_date" label="到期日" width="110" />
            <el-table-column prop="currency_code" label="币种" width="70" />
            <el-table-column label="应收金额" width="120" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column label="已核销" width="120" align="right"><template #default="{ row }">{{ money(row.settled_amount) }}</template></el-table-column>
            <el-table-column label="未核销" width="120" align="right"><template #default="{ row }">{{ money(row.outstanding) }}</template></el-table-column>
            <el-table-column prop="aging_bucket" label="账龄" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag size="small">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'biz:finance:receipt:create'" link type="primary" @click="openReceipt(row)">收款</el-button>
                <el-button v-perm="'biz:finance:arap:writeoff'" link type="warning" @click="openWriteOff(row, 'AR')">核销</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="应付账款" name="ap">
          <el-table :data="payables" v-loading="loading" border stripe>
            <el-table-column prop="ap_no" label="应付单号" width="150" show-overflow-tooltip />
            <el-table-column prop="supplier_name" label="供应商" min-width="140" show-overflow-tooltip />
            <el-table-column prop="bill_date" label="单据日期" width="110" />
            <el-table-column prop="due_date" label="到期日" width="110" />
            <el-table-column prop="currency_code" label="币种" width="70" />
            <el-table-column label="应付金额" width="120" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column label="已核销" width="120" align="right"><template #default="{ row }">{{ money(row.settled_amount) }}</template></el-table-column>
            <el-table-column label="未核销" width="120" align="right"><template #default="{ row }">{{ money(row.outstanding) }}</template></el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }"><el-tag size="small">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'biz:finance:payment:create'" link type="primary" @click="openPayment(row)">付款</el-button>
                <el-button v-perm="'biz:finance:arap:writeoff'" link type="warning" @click="openWriteOff(row, 'AP')">核销</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="收付款单" name="settlement">
          <div class="fl-toolbar">
            <el-radio-group v-model="settlementKind" @change="loadSettlements">
              <el-radio-button value="receipt">收款单</el-radio-button>
              <el-radio-button value="payment">付款单</el-radio-button>
            </el-radio-group>
            <el-select v-model="settlementStatus" placeholder="全部状态" clearable style="width: 150px">
              <el-option v-for="opt in settlementStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-date-picker
              v-model="settlementRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 240px"
            />
            <el-button type="primary" :loading="settlementLoading" @click="loadSettlements">查询</el-button>
          </div>
          <el-alert
            v-if="settlementError"
            type="error"
            :closable="false"
            show-icon
            :title="settlementError"
            style="margin-bottom: 8px"
          />
          <el-table v-if="settlementKind === 'receipt'" v-loading="settlementLoading" :data="receiptDocs" border stripe>
            <el-table-column prop="receipt_no" label="收款单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="customer_id" label="客户ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
            <el-table-column prop="receipt_date" label="收款日期" width="120" />
            <el-table-column prop="currency_code" label="币种" width="80" />
            <el-table-column label="金额" width="130" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="pay_method" label="收款方式" width="130" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag size="small">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="voucher_id" label="凭证ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
          </el-table>
          <el-table v-else v-loading="settlementLoading" :data="paymentDocs" border stripe>
            <el-table-column prop="payment_no" label="付款单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="supplier_id" label="供应商ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
            <el-table-column prop="payment_date" label="付款日期" width="120" />
            <el-table-column prop="currency_code" label="币种" width="80" />
            <el-table-column label="金额" width="130" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="pay_method" label="付款方式" width="130" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag size="small">{{ statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="voucher_id" label="凭证ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="账龄分析" name="aging">
          <div class="fl-toolbar">
            <el-radio-group v-model="agingType" @change="loadAging">
              <el-radio-button value="AR">应收账龄</el-radio-button>
              <el-radio-button value="AP">应付账龄</el-radio-button>
            </el-radio-group>
            <el-button :loading="loading" @click="loadAging">刷新</el-button>
            <span v-if="aging" class="fl-muted">合计 {{ money(aging.total) }}</span>
          </div>
          <el-table :data="aging?.lines ?? []" border stripe max-height="520">
            <el-table-column prop="party_name" label="往来单位" min-width="200" />
            <el-table-column prop="bucket" label="账龄区间" width="140" />
            <el-table-column label="金额" width="160" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="付款计划" name="plan">
          <el-table :data="paymentPlan" border stripe max-height="520">
            <el-table-column prop="ap_no" label="应付单号" width="170" />
            <el-table-column prop="supplier_name" label="供应商" min-width="160" />
            <el-table-column prop="due_date" label="到期日" width="120" />
            <el-table-column prop="planned_pay_date" label="计划付款日" width="130" />
            <el-table-column prop="currency_code" label="币种" width="80" />
            <el-table-column label="应付" width="130" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
            <el-table-column label="未付" width="130" align="right"><template #default="{ row }">{{ money(row.outstanding) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="receiptVisible" title="登记收款" width="520px">
      <el-alert v-if="receiptError" type="error" :closable="false" show-icon :title="receiptError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="客户名称"><el-input v-model="receiptForm.customer_name" maxlength="128" /></el-form-item>
        <el-form-item label="收款金额" required><el-input v-model="receiptForm.amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="收款日期" required>
          <el-date-picker v-model="receiptForm.receipt_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款方式">
          <el-select v-model="receiptForm.pay_method" style="width: 100%">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="承兑汇票" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联应收">
          <el-select v-model="receiptForm.receivable_ids" multiple filterable style="width: 100%" placeholder="可选，核销到指定应收单">
            <el-option v-for="ar in receivables" :key="ar.id" :label="`${ar.ar_no} / 未核销 ${money(ar.outstanding)}`" :value="ar.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiptVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReceipt">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="paymentVisible" title="登记付款" width="520px">
      <el-alert v-if="paymentError" type="error" :closable="false" show-icon :title="paymentError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="供应商"><el-input v-model="paymentForm.supplier_name" maxlength="128" /></el-form-item>
        <el-form-item label="付款金额" required><el-input v-model="paymentForm.amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="付款日期" required>
          <el-date-picker v-model="paymentForm.payment_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="paymentForm.pay_method" style="width: 100%">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="承兑汇票" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联应付">
          <el-select v-model="paymentForm.payable_ids" multiple filterable style="width: 100%" placeholder="可选，核销到指定应付单">
            <el-option v-for="ap in payables" :key="ap.id" :label="`${ap.ap_no} / 未核销 ${money(ap.outstanding)}`" :value="ap.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPayment">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="writeOffVisible" title="核销" width="460px">
      <el-alert v-if="writeOffError" type="error" :closable="false" show-icon :title="writeOffError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="单据">{{ writeOffContext?.no }}</el-form-item>
        <el-form-item label="未核销">{{ money(writeOffContext?.outstanding) }}</el-form-item>
        <el-form-item label="核销金额" required><el-input v-model="writeOffForm.amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="核销日期">
          <el-date-picker v-model="writeOffForm.write_off_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="writeOffVisible = false">取消</el-button>
        <el-button type="warning" @click="submitWriteOff">确认核销</el-button>
      </template>
    </el-dialog>
  </div>
</template>
