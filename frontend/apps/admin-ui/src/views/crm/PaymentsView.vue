<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice, formatMoney } from '@fenglin/ui-admin'
import { crmApi, type ContractListItemView, type ContractView, type CrmId, type PaymentRecordView } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const keyword = ref('')
const contracts = ref<ContractListItemView[]>([])
const contractLoading = ref(false)
const listError = ref('')
const selected = ref<ContractListItemView | null>(null)
const detail = ref<ContractView | null>(null)
const records = ref<PaymentRecordView[]>([])
const detailLoading = ref(false)
const detailError = ref('')

const paymentVisible = ref(false)
const paying = ref(false)
const payError = ref('')
const payForm = reactive({
  plan_id: undefined as CrmId | undefined,
  amount: 0,
  received_date: new Date().toISOString().slice(0, 10),
  method: 'BANK',
  voucher_no: '',
  remark: '',
})

async function searchContracts() {
  contractLoading.value = true
  listError.value = ''
  try {
    const result = await crmApi.contracts.list({ keyword: keyword.value || undefined, page: 1, size: 20 })
    contracts.value = result.records
  } catch (err) {
    listError.value = describeError(err)
  } finally {
    contractLoading.value = false
  }
}

async function selectContract(row: ContractListItemView) {
  selected.value = row
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  records.value = []
  try {
    detail.value = await crmApi.contracts.detail(row.contract_id)
    records.value = await crmApi.payments.records(row.contract_id)
  } catch (err) {
    detailError.value = describeError(err)
  } finally {
    detailLoading.value = false
  }
}

function openPayment() {
  payForm.plan_id = detail.value?.payment_plans.find((plan) => plan.status !== 'PAID')?.plan_id
  payForm.amount = detail.value?.payment_plans.find((plan) => plan.status !== 'PAID')?.remaining_amount ?? 0
  payForm.received_date = new Date().toISOString().slice(0, 10)
  payForm.method = 'BANK'
  payForm.voucher_no = ''
  payForm.remark = ''
  payError.value = ''
  paymentVisible.value = true
}

async function submitPayment() {
  if (!detail.value) return
  if (!payForm.amount || payForm.amount <= 0) {
    payError.value = '回款金额必须大于 0'
    return
  }
  paying.value = true
  payError.value = ''
  try {
    await crmApi.payments.register(detail.value.contract_id, {
      plan_id: payForm.plan_id,
      amount: payForm.amount,
      received_date: payForm.received_date || undefined,
      method: payForm.method,
      voucher_no: payForm.voucher_no || undefined,
      remark: payForm.remark || undefined,
    })
    ElMessage.success('回款已登记')
    paymentVisible.value = false
    if (selected.value) await selectContract(selected.value)
  } catch (err) {
    payError.value = describeError(err)
  } finally {
    paying.value = false
  }
}

onMounted(searchContracts)
</script>

<template>
  <div>
    <PageHeader module="云客 CRM · 回款" title="回款计划与登记" description="按合同查看回款计划与回款记录并登记到账；回款接口为 01 文档未定义的最小可用扩展。">
      <template #actions>
        <el-button :loading="contractLoading" @click="searchContracts">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '01 文档未定义回款接口与回款记录表，本页对接的是后端扩展接口（已标注）。',
        '登记金额超过计划未回款额时由后端校验拒绝；部分回款会自动更新计划状态。',
        '如需从报价直接生成合同，请使用“报价管理 → 转合同”。',
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="keyword" placeholder="合同编号/客户名称" clearable style="width: 260px" @keyup.enter="searchContracts" />
        <el-button type="primary" plain @click="searchContracts">查询合同</el-button>
      </div>
      <el-alert v-if="listError" type="error" :closable="false" show-icon :title="listError" style="margin-bottom: 12px" />
      <el-table :data="contracts" v-loading="contractLoading" border stripe highlight-current-row @row-click="selectContract">
        <el-table-column prop="contract_no" label="合同编号" width="190" />
        <el-table-column prop="customer_name" label="客户" min-width="160" />
        <el-table-column prop="amount" label="合同金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="selectContract(row)">查看回款</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="selected" v-loading="detailLoading" class="fl-card" style="margin-top: 16px">
      <div class="detail-head">
        <div>
          <strong>{{ selected.contract_no }}</strong>
          <span class="fl-muted"> · {{ selected.customer_name }} · 合同金额 {{ selected.amount }}</span>
        </div>
        <el-button v-perm="'crm:contract:manage'" type="primary" @click="openPayment">登记回款</el-button>
      </div>
      <el-alert v-if="detailError" type="error" :closable="false" show-icon :title="detailError" style="margin-bottom: 12px" />

      <template v-if="detail">
        <h3 class="sub">回款计划</h3>
        <el-table :data="detail.payment_plans" size="small" border>
          <el-table-column prop="period_no" label="期次" width="70" />
          <el-table-column prop="due_date" label="到期日" width="120" />
          <el-table-column prop="amount" label="计划金额" width="120" />
          <el-table-column prop="paid_amount" label="已回款" width="120" />
          <el-table-column prop="remaining_amount" label="未回款" width="120" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="overdue_days" label="逾期天数" width="100" />
          <el-table-column prop="collection_flag" label="催收标记" width="110" />
        </el-table>

        <h3 class="sub">回款记录</h3>
        <el-table :data="records" size="small" border>
          <el-table-column prop="payment_id" label="记录 ID" width="100" />
          <el-table-column prop="plan_id" label="计划 ID" width="100" />
          <el-table-column prop="amount" label="金额" width="120" />
          <el-table-column prop="received_date" label="到账日" width="120" />
          <el-table-column prop="method" label="方式" width="100" />
          <el-table-column prop="voucher_no" label="凭证号" width="140" />
          <el-table-column prop="remark" label="备注" min-width="140" />
          <el-table-column prop="status" label="状态" width="100" />
        </el-table>
      </template>
    </div>

    <el-dialog v-model="paymentVisible" title="登记回款" width="520px">
      <el-alert v-if="payError" type="error" :closable="false" show-icon :title="payError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="回款计划">
          <el-select v-model="payForm.plan_id" clearable style="width: 100%" placeholder="不选则后端按最早未结清计划冲抵">
            <el-option
              v-for="plan in detail?.payment_plans || []"
              :key="plan.plan_id"
              :label="`第 ${plan.period_no} 期 / 未回 ${plan.remaining_amount ?? plan.amount}`"
              :value="plan.plan_id"
              :disabled="plan.status === 'PAID'"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="回款金额" required>
          <el-input-number v-model="payForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到账日期">
          <el-date-picker v-model="payForm.received_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="回款方式">
          <el-select v-model="payForm.method" style="width: 100%">
            <el-option label="银行转账" value="BANK" />
            <el-option label="电汇" value="TRANSFER" />
            <el-option label="在线支付" value="ONLINE" />
            <el-option label="现金" value="CASH" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="凭证号">
          <el-input v-model="payForm.voucher_no" maxlength="64" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="payForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentVisible = false">取消</el-button>
        <el-button type="primary" :loading="paying" @click="submitPayment">确认登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.sub {
  margin: 12px 0 8px;
  font-size: 14px;
}
</style>
