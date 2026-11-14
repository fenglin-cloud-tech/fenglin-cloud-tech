<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { crmApi, type ContractChangeView, type ContractListItemView, type ContractView, type QuotationListItemView, type QuotationView } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const tab = ref<'quotations' | 'contracts'>('quotations')

const quotations = ref<QuotationListItemView[]>([])
const quotationTotal = ref(0)
const quotationLoading = ref(false)
const quotationError = ref('')
const quotationQuery = reactive({ status: '', keyword: '', page: 1, size: 10 })

const contracts = ref<ContractListItemView[]>([])
const contractTotal = ref(0)
const contractLoading = ref(false)
const contractError = ref('')
const contractQuery = reactive({ status: '', keyword: '', page: 1, size: 10 })

const quotationDetailVisible = ref(false)
const quotationDetailLoading = ref(false)
const quotationDetail = ref<QuotationView | null>(null)
const submitting = ref(false)

const contractDetailVisible = ref(false)
const contractDetailLoading = ref(false)
const contractDetail = ref<ContractView | null>(null)
const contractChanges = ref<ContractChangeView[]>([])

const quotationStatusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '审批中' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CONVERTED', label: '已转合同' },
]
const contractStatusOptions = [
  { value: 'ACTIVE', label: '生效中' },
  { value: 'EXPIRED', label: '已到期' },
  { value: 'TERMINATED', label: '已终止' },
]

function quotationLabel(value?: string) {
  return quotationStatusOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}
function quotationTag(value?: string) {
  if (value === 'APPROVED' || value === 'CONVERTED') return 'success'
  if (value === 'REJECTED') return 'danger'
  if (value === 'PENDING') return 'warning'
  return 'info'
}
function contractLabel(value?: string) {
  return contractStatusOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}
function contractTag(value?: string) {
  if (value === 'ACTIVE') return 'success'
  if (value === 'TERMINATED') return 'danger'
  return 'info'
}

async function loadQuotations() {
  if (!auth.userId) {
    quotationError.value = '登录上下文缺少用户 ID，无法过滤“我的报价”'
    return
  }
  quotationLoading.value = true
  quotationError.value = ''
  try {
    const result = await crmApi.quotations.my({
      status: quotationQuery.status || undefined,
      keyword: quotationQuery.keyword || undefined,
      page: quotationQuery.page,
      size: quotationQuery.size,
    })
    quotations.value = result.records
    quotationTotal.value = result.total
  } catch (err) {
    quotationError.value = describeError(err)
  } finally {
    quotationLoading.value = false
  }
}

async function loadContracts() {
  if (!auth.userId) {
    contractError.value = '登录上下文缺少用户 ID，无法过滤“我的合同”'
    return
  }
  contractLoading.value = true
  contractError.value = ''
  try {
    const result = await crmApi.contracts.my({
      status: contractQuery.status || undefined,
      keyword: contractQuery.keyword || undefined,
      page: contractQuery.page,
      size: contractQuery.size,
    })
    contracts.value = result.records
    contractTotal.value = result.total
  } catch (err) {
    contractError.value = describeError(err)
  } finally {
    contractLoading.value = false
  }
}

async function openQuotation(row: QuotationListItemView) {
  quotationDetailVisible.value = true
  quotationDetailLoading.value = true
  quotationDetail.value = null
  try {
    quotationDetail.value = await crmApi.quotations.myDetail(row.quotation_id)
  } catch (err) {
    quotationError.value = describeError(err)
  } finally {
    quotationDetailLoading.value = false
  }
}

async function submitQuotation() {
  if (!quotationDetail.value) return
  submitting.value = true
  try {
    const result = await crmApi.quotations.submit(quotationDetail.value.quotation_id, {})
    ElMessage.success(result.approval_required ? `已提交审批：${result.approve_instance_id ?? ''}` : '报价已生效')
    await openQuotation({ ...quotationDetail.value, customer_name: undefined } as QuotationListItemView)
    await loadQuotations()
  } catch (err) {
    quotationError.value = describeError(err)
  } finally {
    submitting.value = false
  }
}

async function openContract(row: ContractListItemView) {
  contractDetailVisible.value = true
  contractDetailLoading.value = true
  contractDetail.value = null
  contractChanges.value = []
  try {
    contractDetail.value = await crmApi.contracts.myDetail(row.contract_id)
    contractChanges.value = await crmApi.contracts.myChanges(row.contract_id)
  } catch (err) {
    contractError.value = describeError(err)
  } finally {
    contractDetailLoading.value = false
  }
}

function switchTab(name: string | number) {
  if (name === 'contracts' && !contracts.value.length) loadContracts()
}

onMounted(() => {
  loadQuotations()
  loadContracts()
})
</script>

<template>
  <div>
    <el-card shadow="never">
      <el-tabs v-model="tab" @tab-change="switchTab">
        <el-tab-pane label="我的报价" name="quotations">
          <div class="fl-toolbar">
            <el-select v-model="quotationQuery.status" placeholder="报价状态" clearable style="width: 140px" @change="quotationQuery.page = 1; loadQuotations()">
              <el-option v-for="item in quotationStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input v-model="quotationQuery.keyword" placeholder="报价编号/客户" clearable style="width: 220px" @keyup.enter="quotationQuery.page = 1; loadQuotations()" />
            <el-button type="primary" plain @click="quotationQuery.page = 1; loadQuotations()">查询</el-button>
            <el-button :loading="quotationLoading" @click="loadQuotations">刷新</el-button>
            <span class="fl-muted">负责人：{{ auth.displayName }} · 共 {{ quotationTotal }} 条</span>
          </div>

          <el-alert v-if="quotationError" type="error" :closable="false" show-icon :title="quotationError" style="margin-bottom: 12px" />

          <el-table :data="quotations" v-loading="quotationLoading" border stripe>
            <el-table-column prop="quotation_no" label="报价编号" width="180" />
            <el-table-column prop="customer_name" label="客户" min-width="170" />
            <el-table-column prop="version_no" label="版本" width="80" />
            <el-table-column prop="total_amount" label="含税金额" width="140" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="quotationTag(row.status)" size="small">{{ quotationLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="创建时间" width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openQuotation(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="quotationQuery.page"
            v-model:page-size="quotationQuery.size"
            :total="quotationTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @change="loadQuotations"
          />
        </el-tab-pane>

        <el-tab-pane label="我的合同" name="contracts">
          <div class="fl-toolbar">
            <el-select v-model="contractQuery.status" placeholder="合同状态" clearable style="width: 140px" @change="contractQuery.page = 1; loadContracts()">
              <el-option v-for="item in contractStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input v-model="contractQuery.keyword" placeholder="合同编号/客户" clearable style="width: 220px" @keyup.enter="contractQuery.page = 1; loadContracts()" />
            <el-button type="primary" plain @click="contractQuery.page = 1; loadContracts()">查询</el-button>
            <el-button :loading="contractLoading" @click="loadContracts">刷新</el-button>
            <span class="fl-muted">负责人：{{ auth.displayName }} · 共 {{ contractTotal }} 条</span>
          </div>

          <el-alert v-if="contractError" type="error" :closable="false" show-icon :title="contractError" style="margin-bottom: 12px" />

          <el-table :data="contracts" v-loading="contractLoading" border stripe>
            <el-table-column prop="contract_no" label="合同编号" width="170" />
            <el-table-column prop="customer_name" label="客户" min-width="170" />
            <el-table-column prop="amount" label="合同金额" width="140" />
            <el-table-column prop="start_date" label="开始日期" width="120" />
            <el-table-column prop="end_date" label="结束日期" width="120" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="contractTag(row.status)" size="small">{{ contractLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openContract(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="contractQuery.page"
            v-model:page-size="contractQuery.size"
            :total="contractTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @change="loadContracts"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-drawer v-model="quotationDetailVisible" title="报价详情" size="560px">
      <div v-loading="quotationDetailLoading">
        <template v-if="quotationDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="报价编号">{{ quotationDetail.quotation_no }}</el-descriptions-item>
            <el-descriptions-item label="客户 ID">{{ quotationDetail.customer_id }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ quotationDetail.version_no }}</el-descriptions-item>
            <el-descriptions-item label="含税金额">{{ quotationDetail.total_amount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="税率">{{ quotationDetail.tax_rate ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="有效期至">{{ quotationDetail.valid_until || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ quotationLabel(quotationDetail.status) }}</el-descriptions-item>
            <el-descriptions-item label="审批实例">{{ quotationDetail.approve_instance_id || '-' }}</el-descriptions-item>
          </el-descriptions>

          <h4 style="margin: 16px 0 8px">报价明细</h4>
          <el-table :data="quotationDetail.items" size="small" border>
            <el-table-column prop="line_no" label="行号" width="70" />
            <el-table-column prop="product_name" label="产品" min-width="140" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column prop="unit_price" label="单价" width="120" />
            <el-table-column prop="amount" label="金额" width="120" />
          </el-table>

          <div style="margin-top: 16px">
            <el-button
              v-if="quotationDetail.status === 'DRAFT'"
              v-perm="'crm:quotation:submit'"
              type="primary"
              :loading="submitting"
              @click="submitQuotation"
            >提交审批</el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-drawer v-model="contractDetailVisible" title="合同详情" size="560px">
      <div v-loading="contractDetailLoading">
        <template v-if="contractDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="合同编号">{{ contractDetail.contract_no }}</el-descriptions-item>
            <el-descriptions-item label="客户 ID">{{ contractDetail.customer_id }}</el-descriptions-item>
            <el-descriptions-item label="合同金额">{{ contractDetail.amount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="SLA">{{ contractDetail.sla_level || '-' }}</el-descriptions-item>
            <el-descriptions-item label="服务包">{{ contractDetail.service_package || '-' }}</el-descriptions-item>
            <el-descriptions-item label="期限">{{ contractDetail.start_date || '-' }} ~ {{ contractDetail.end_date || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ contractLabel(contractDetail.status) }}</el-descriptions-item>
          </el-descriptions>

          <h4 style="margin: 16px 0 8px">回款计划</h4>
          <el-table :data="contractDetail.payment_plans" size="small" border>
            <el-table-column prop="period_no" label="期次" width="70" />
            <el-table-column prop="due_date" label="到期日" width="120" />
            <el-table-column prop="amount" label="金额" width="120" />
            <el-table-column prop="paid_amount" label="已回款" width="120" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>

          <h4 style="margin: 16px 0 8px">变更记录</h4>
          <el-empty v-if="!contractChanges.length" description="暂无变更记录" :image-size="60" />
          <el-table v-else :data="contractChanges" size="small" border>
            <el-table-column prop="change_no" label="变更单号" width="160" />
            <el-table-column prop="change_type" label="类型" width="100" />
            <el-table-column prop="after_amount" label="变更后金额" width="120" />
            <el-table-column prop="created_at" label="时间" min-width="160" />
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>
