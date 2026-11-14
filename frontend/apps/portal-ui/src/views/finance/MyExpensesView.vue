<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { financeApi, type FinanceExpense } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const expenses = ref<FinanceExpense[]>([])
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const dialogVisible = ref(false)

const myExpenses = computed(() => expenses.value)

const gapItems = [
  '缺发票/附件上传接口：明细只能填写金额与备注，invoice_id / attachment_id 无法从员工端选择。',
]

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审批',
  PENDING: '待审批',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  PAID: '已支付',
}

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'APPROVED' || value === 'PAID') return 'success'
  if (value === 'REJECTED') return 'danger'
  if (value === 'SUBMITTED' || value === 'PENDING') return 'warning'
  return 'info'
}

function money(value?: string) {
  return value === undefined || value === null ? '-' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const today = new Date().toISOString().slice(0, 10)

const form = reactive({
  claim_date: today,
  claim_type: 'DAILY',
  currency_code: 'CNY',
  items: [
    {
      expense_date: today,
      expense_category: 'TRAVEL',
      amount: '',
      tax_amount: '0',
      remark: '',
    },
  ],
})

function addItem() {
  form.items.push({
    expense_date: today,
    expense_category: 'TRAVEL',
    amount: '',
    tax_amount: '0',
    remark: '',
  })
}

function removeItem(index: number) {
  if (form.items.length <= 1) return
  form.items.splice(index, 1)
}

function resetForm() {
  form.claim_date = today
  form.claim_type = 'DAILY'
  form.currency_code = 'CNY'
  form.items = [
    { expense_date: today, expense_category: 'TRAVEL', amount: '', tax_amount: '0', remark: '' },
  ]
}

const totalAmount = computed(() =>
  form.items.reduce((sum, item) => sum + (Number(item.amount) || 0), 0).toFixed(2),
)

async function loadExpenses() {
  loading.value = true
  error.value = ''
  try {
    expenses.value = await financeApi.expenses.my({ page: 1, size: 50 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function handleCreate(submitAfterCreate: boolean) {
  if (!auth.userId) {
    ElMessage.error('当前令牌未返回用户 ID，无法创建报销单')
    return
  }
  if (!form.items.length || form.items.some((item) => !item.amount)) {
    ElMessage.warning('请填写每条明细的金额')
    return
  }
  saving.value = true
  try {
    const created = await financeApi.expenses.create({
      applicant_id: auth.userId,
      applicant_name: auth.displayName,
      claim_type: form.claim_type,
      currency_code: form.currency_code,
      claim_date: form.claim_date,
      items: form.items.map((item) => ({
        expense_date: item.expense_date,
        expense_category: item.expense_category,
        amount: item.amount,
        tax_amount: item.tax_amount || '0',
        remark: item.remark || undefined,
      })),
    })
    if (submitAfterCreate) {
      await financeApi.expenses.submit(created.id)
    }
    ElMessage.success(submitAfterCreate ? '报销单已创建并提交' : '报销单草稿已保存')
    dialogVisible.value = false
    resetForm()
    await loadExpenses()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

async function handleSubmit(row: FinanceExpense) {
  try {
    await ElMessageBox.confirm(`确认提交报销单 ${row.claim_no}？提交后进入审批流程。`, '提交报销单', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await financeApi.expenses.submit(row.id)
    ElMessage.success('已提交')
    await loadExpenses()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(loadExpenses)
</script>

<template>
  <div>
    <PageHeader module="云财 Finance" title="我的报销" description="提交与跟踪本人报销单；数据直接来自报销接口。" />

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-button type="primary" @click="dialogVisible = true">新建报销单</el-button>
        <el-button :loading="loading" @click="loadExpenses">刷新</el-button>
        <span class="fl-muted">{{ auth.displayName }}</span>
      </div>

      <div v-loading="loading">
        <el-empty v-if="!myExpenses.length && !error" description="暂无本人报销单" :image-size="80" />
        <el-table v-else :data="myExpenses" border stripe>
          <el-table-column prop="claim_no" label="单号" width="180" />
          <el-table-column prop="claim_date" label="报销日期" width="120" />
          <el-table-column prop="claim_type" label="类型" width="110" />
          <el-table-column label="金额" width="130" align="right">
            <template #default="{ row }">{{ money(row.total_amount) }} {{ row.currency_code }}</template>
          </el-table-column>
          <el-table-column label="税额" width="110" align="right">
            <template #default="{ row }">{{ money(row.tax_amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="明细" width="80">
            <template #default="{ row }">{{ row.items?.length ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="handleSubmit(row)">
                提交
              </el-button>
              <span v-else class="fl-muted">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建报销单" width="760px" destroy-on-close>
      <el-form label-width="92px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="报销日期">
              <el-date-picker v-model="form.claim_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="类型">
              <el-select v-model="form.claim_type" style="width: 100%">
                <el-option label="日常报销" value="DAILY" />
                <el-option label="差旅报销" value="TRAVEL" />
                <el-option label="项目报销" value="PROJECT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种">
              <el-input v-model="form.currency_code" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">费用明细（合计 {{ totalAmount }}）</el-divider>
        <div v-for="(item, index) in form.items" :key="index" class="expense-line">
          <el-date-picker
            v-model="item.expense_date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="发生日期"
            style="width: 140px"
          />
          <el-select v-model="item.expense_category" style="width: 130px">
            <el-option label="交通" value="TRAVEL" />
            <el-option label="餐饮" value="MEAL" />
            <el-option label="住宿" value="HOTEL" />
            <el-option label="办公" value="OFFICE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
          <el-input v-model="item.amount" placeholder="金额" style="width: 120px" />
          <el-input v-model="item.tax_amount" placeholder="税额" style="width: 110px" />
          <el-input v-model="item.remark" placeholder="备注" style="flex: 1" />
          <el-button link type="danger" :disabled="form.items.length <= 1" @click="removeItem(index)">
            删除
          </el-button>
        </div>
        <el-button link type="primary" @click="addItem">+ 添加明细</el-button>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="saving" @click="handleCreate(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate(true)">保存并提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.expense-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
</style>
