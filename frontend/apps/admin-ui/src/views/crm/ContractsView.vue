<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice, ID_COLUMN_WIDTH, formatMoney } from '@fenglin/ui-admin'
import { crmApi, type ContractChangeView, type ContractListItemView, type ContractView, type CrmId } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<ContractListItemView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({
  keyword: '',
  status: '',
  customer_id: undefined as string | undefined,
  owner_id: undefined as number | undefined,
  page: 1,
  size: 10,
})

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  customer_id: undefined as string | number | undefined,
  sla_level: 'P2',
  service_package: '',
  amount: undefined as number | undefined,
  start_date: '',
  end_date: '',
})
const planRows = ref([{ due_date: '', amount: 0 }])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ContractView | null>(null)
const changes = ref<ContractChangeView[]>([])

const amendVisible = ref(false)
const amending = ref(false)
const amendError = ref('')
const amendForm = reactive({ amount: undefined as number | undefined, sla_level: 'P2', service_package: '', end_date: '', reason: '' })

const renewVisible = ref(false)
const renewing = ref(false)
const renewError = ref('')
const renewForm = reactive({ start_date: '', end_date: '', amount: undefined as number | undefined, sla_level: 'P2', service_package: '', reason: '' })

const statusOptions = [
  { value: 'ACTIVE', label: '生效中' },
  { value: 'EXPIRED', label: '已到期' },
  { value: 'TERMINATED', label: '已终止' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.contracts.list(query)
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  const start = new Date()
  const end = new Date()
  end.setFullYear(end.getFullYear() + 1)
  Object.assign(createForm, {
    customer_id: undefined,
    sla_level: 'P2',
    service_package: '',
    amount: undefined,
    start_date: start.toISOString().slice(0, 10),
    end_date: end.toISOString().slice(0, 10),
  })
  planRows.value = [{ due_date: end.toISOString().slice(0, 10), amount: 0 }]
  createError.value = ''
  createVisible.value = true
}

function syncPlanAmount() {
  if (planRows.value.length === 1 && createForm.amount) {
    planRows.value[0].amount = createForm.amount
  }
}

function addPlan() {
  planRows.value.push({ due_date: createForm.end_date, amount: 0 })
}

function removePlan(index: number) {
  if (planRows.value.length > 1) planRows.value.splice(index, 1)
}

async function submitCreate() {
  if (!createForm.customer_id || !createForm.start_date || !createForm.end_date) {
    createError.value = '客户 ID、合同起止日期为必填项'
    return
  }
  if (!createForm.amount || createForm.amount <= 0) {
    createError.value = '合同金额必须大于 0'
    return
  }
  const planSum = planRows.value.reduce((sum, item) => sum + (item.amount || 0), 0)
  if (Math.abs(planSum - createForm.amount) > 0.001) {
    createError.value = `回款计划合计 ${planSum} 与合同金额 ${createForm.amount} 不一致`
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await crmApi.contracts.create({
      customer_id: createForm.customer_id,
      sla_level: createForm.sla_level,
      service_package: createForm.service_package || undefined,
      amount: createForm.amount,
      start_date: createForm.start_date,
      end_date: createForm.end_date,
      payment_plans: planRows.value.map((item, index) => ({
        period_no: index + 1,
        due_date: item.due_date || createForm.end_date,
        amount: item.amount,
      })),
    })
    ElMessage.success('合同已创建')
    createVisible.value = false
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

async function openDetail(row: ContractListItemView) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  changes.value = []
  try {
    detail.value = await crmApi.contracts.detail(row.contract_id)
    changes.value = await crmApi.contracts.changes(row.contract_id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetailById(id: CrmId) {
  detail.value = await crmApi.contracts.detail(id)
  changes.value = await crmApi.contracts.changes(id)
}

function openAmend() {
  if (!detail.value) return
  Object.assign(amendForm, {
    amount: detail.value.amount,
    sla_level: detail.value.sla_level || 'P2',
    service_package: detail.value.service_package || '',
    end_date: detail.value.end_date || '',
    reason: '',
  })
  amendError.value = ''
  amendVisible.value = true
}

async function submitAmend() {
  if (!detail.value) return
  amending.value = true
  amendError.value = ''
  try {
    await crmApi.contracts.amend(detail.value.contract_id, {
      amount: amendForm.amount,
      sla_level: amendForm.sla_level,
      service_package: amendForm.service_package || undefined,
      end_date: amendForm.end_date || undefined,
      reason: amendForm.reason || undefined,
    })
    ElMessage.success('合同变更已生效')
    amendVisible.value = false
    await refreshDetailById(detail.value.contract_id)
    await load()
  } catch (err) {
    amendError.value = describeError(err)
  } finally {
    amending.value = false
  }
}

function openRenew() {
  if (!detail.value) return
  const start = detail.value.end_date ? new Date(detail.value.end_date) : new Date()
  const end = new Date(start)
  end.setFullYear(end.getFullYear() + 1)
  Object.assign(renewForm, {
    start_date: start.toISOString().slice(0, 10),
    end_date: end.toISOString().slice(0, 10),
    amount: detail.value.amount,
    sla_level: detail.value.sla_level || 'P2',
    service_package: detail.value.service_package || '',
    reason: '',
  })
  renewError.value = ''
  renewVisible.value = true
}

async function submitRenew() {
  if (!detail.value) return
  renewing.value = true
  renewError.value = ''
  try {
    await crmApi.contracts.renew(detail.value.contract_id, {
      start_date: renewForm.start_date,
      end_date: renewForm.end_date,
      amount: renewForm.amount,
      sla_level: renewForm.sla_level,
      service_package: renewForm.service_package || undefined,
      reason: renewForm.reason || undefined,
    })
    ElMessage.success('续签合同已生成')
    renewVisible.value = false
    await load()
    detailVisible.value = false
  } catch (err) {
    renewError.value = describeError(err)
  } finally {
    renewing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="云客 CRM · 合同" title="合同管理" description="合同创建、变更、续签与回款计划查看；列表接口为本次后端补齐的只读扩展。">
      <template #actions>
        <el-button v-perm="'crm:contract:manage'" type="primary" @click="openCreate">新建合同</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '原 01 契约未定义合同列表接口，本页使用本次补齐的 GET /api/crm/v1/contracts（只读）。',
        '合同创建时回款计划合计必须等于合同金额；单期计划会自动带出合同金额。',
        '变更仅对 ACTIVE 合同开放；续签会生成新合同并保留父子关系。',
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="query.keyword" placeholder="合同编号/客户名称" clearable style="width: 220px" @keyup.enter="query.page = 1; load()" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input v-model="query.customer_id" placeholder="客户 ID" clearable style="width: 160px" />
        <el-input-number v-model="query.owner_id" :min="1" placeholder="负责人 ID" :controls="false" style="width: 140px" />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="query.keyword = ''; query.status = ''; query.customer_id = undefined; query.owner_id = undefined; query.page = 1; load()">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="contract_no" label="合同编号" width="180" show-overflow-tooltip />
        <el-table-column prop="customer_name" label="客户" min-width="150" />
        <el-table-column prop="sla_level" label="SLA" width="80" />
        <el-table-column prop="service_package" label="服务包" width="120" />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="start_date" label="开始" width="110" />
        <el-table-column prop="end_date" label="结束" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'TERMINATED' ? 'danger' : 'info'" size="small">
              {{ statusOptions.find((o) => o.value === row.status)?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner_id" label="负责人" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-perm="'crm:contract:manage'" link type="warning" @click="openDetail(row).then(() => openAmend())">变更</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="load"
      />
    </div>

    <el-dialog v-model="createVisible" title="新建合同" width="720px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="客户 ID" required>
          <el-input v-model="createForm.customer_id" placeholder="雪花 ID，按文本透传" clearable />
        </el-form-item>
        <el-form-item label="SLA 等级">
          <el-select v-model="createForm.sla_level" style="width: 100%">
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务包">
          <el-input v-model="createForm.service_package" maxlength="64" />
        </el-form-item>
        <el-form-item label="合同金额" required>
          <el-input-number v-model="createForm.amount" :min="0.01" :precision="2" style="width: 100%" @change="syncPlanAmount" />
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="createForm.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="createForm.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div class="plan-head">
        <strong>回款计划（合计需等于合同金额）</strong>
        <el-button size="small" @click="addPlan">添加期次</el-button>
      </div>
      <el-table :data="planRows" size="small" border>
        <el-table-column label="期次" width="70">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="到期日" min-width="180">
          <template #default="{ row }">
            <el-date-picker v-model="row.due_date" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="180">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" :min="0.01" :precision="2" size="small" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }"><el-button link type="danger" size="small" @click="removePlan($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="合同详情" size="760px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="合同编号">{{ detail.contract_no }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
            <el-descriptions-item label="客户 ID">{{ detail.customer_id }}</el-descriptions-item>
            <el-descriptions-item label="金额">{{ detail.amount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="SLA">{{ detail.sla_level || '-' }}</el-descriptions-item>
            <el-descriptions-item label="服务包">{{ detail.service_package || '-' }}</el-descriptions-item>
            <el-descriptions-item label="开始">{{ detail.start_date || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结束">{{ detail.end_date || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="actions">
            <el-button v-if="detail.status === 'ACTIVE'" v-perm="'crm:contract:manage'" size="small" type="warning" @click="openAmend">合同变更</el-button>
            <el-button v-perm="'crm:contract:manage'" size="small" type="primary" @click="openRenew">续签</el-button>
          </div>

          <h3 class="sub">回款计划</h3>
          <el-table :data="detail.payment_plans" size="small" border>
            <el-table-column prop="period_no" label="期次" width="70" />
            <el-table-column prop="due_date" label="到期日" width="120" />
            <el-table-column prop="amount" label="金额" width="120" />
            <el-table-column prop="paid_amount" label="已回款" width="120" />
            <el-table-column prop="remaining_amount" label="未回款" width="120" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="overdue_days" label="逾期天数" width="100" />
            <el-table-column prop="collection_flag" label="催收标记" width="110" />
          </el-table>

          <h3 class="sub">变更记录</h3>
          <el-table :data="changes" size="small" border>
            <el-table-column prop="change_no" label="变更单号" width="170" />
            <el-table-column prop="change_type" label="类型" width="100" />
            <el-table-column prop="before_amount" label="变更前" width="110" />
            <el-table-column prop="after_amount" label="变更后" width="110" />
            <el-table-column prop="reason" label="原因" min-width="140" />
            <el-table-column prop="new_contract_id" label="续签合同 ID" width="120" />
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="amendVisible" title="合同变更" width="560px">
      <el-alert v-if="amendError" type="error" :closable="false" show-icon :title="amendError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="变更后金额">
          <el-input-number v-model="amendForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="SLA 等级">
          <el-select v-model="amendForm.sla_level" style="width: 100%">
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务包">
          <el-input v-model="amendForm.service_package" maxlength="64" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="amendForm.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="amendForm.reason" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="amendVisible = false">取消</el-button>
        <el-button type="primary" :loading="amending" @click="submitAmend">确认变更</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renewVisible" title="合同续签" width="560px">
      <el-alert v-if="renewError" type="error" :closable="false" show-icon :title="renewError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="新合同开始" required>
          <el-date-picker v-model="renewForm.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="新合同结束" required>
          <el-date-picker v-model="renewForm.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="续签金额">
          <el-input-number v-model="renewForm.amount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="SLA 等级">
          <el-select v-model="renewForm.sla_level" style="width: 100%">
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="续签原因">
          <el-input v-model="renewForm.reason" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewVisible = false">取消</el-button>
        <el-button type="primary" :loading="renewing" @click="submitRenew">确认续签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.plan-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0;
}
.actions {
  display: flex;
  gap: 8px;
  margin: 16px 0;
}
.sub {
  margin: 12px 0 8px;
  font-size: 14px;
}
</style>
