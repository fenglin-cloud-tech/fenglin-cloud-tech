<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { crmApi, type CrmId, type QuotationListItemView, type QuotationView } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<QuotationListItemView[]>([])
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
  opportunity_id: undefined as string | number | undefined,
  tax_rate: 6,
  valid_until: '',
  remark: '',
})
const items = ref([{ product_code: '', product_name: '', unit: '套', quantity: 1, unit_price: 0, discount_rate: 0 }])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<QuotationView | null>(null)

const convertVisible = ref(false)
const converting = ref(false)
const convertError = ref('')
const convertForm = reactive({
  sla_level: 'P2',
  service_package: '',
  start_date: '',
  end_date: '',
  reason: '',
})

const statusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '审批中' },
  { value: 'APPROVED', label: '已审批' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CONVERTED', label: '已转合同' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.quotations.list(query)
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, {
    customer_id: undefined,
    opportunity_id: undefined,
    tax_rate: 6,
    valid_until: '',
    remark: '',
  })
  items.value = [{ product_code: '', product_name: '', unit: '套', quantity: 1, unit_price: 0, discount_rate: 0 }]
  createError.value = ''
  createVisible.value = true
}

function addItem() {
  items.value.push({ product_code: '', product_name: '', unit: '套', quantity: 1, unit_price: 0, discount_rate: 0 })
}

function removeItem(index: number) {
  if (items.value.length > 1) items.value.splice(index, 1)
}

async function submitCreate() {
  if (!createForm.customer_id) {
    createError.value = '客户 ID 为必填项'
    return
  }
  if (items.value.some((item) => !item.product_code || item.quantity <= 0 || item.unit_price < 0)) {
    createError.value = '明细需填写产品编码、数量与单价'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await crmApi.quotations.create({
      customer_id: createForm.customer_id,
      opportunity_id: createForm.opportunity_id,
      tax_rate: createForm.tax_rate,
      valid_until: createForm.valid_until || undefined,
      remark: createForm.remark || undefined,
      items: items.value.map((item) => ({
        product_code: item.product_code,
        product_name: item.product_name || undefined,
        unit: item.unit || undefined,
        quantity: item.quantity,
        unit_price: item.unit_price,
        discount_rate: item.discount_rate || undefined,
      })),
    })
    ElMessage.success('报价已创建')
    createVisible.value = false
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

async function openDetail(row: QuotationListItemView) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await crmApi.quotations.detail(row.quotation_id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function submitApproval(row: { quotation_id: CrmId }) {
  try {
    const result = await crmApi.quotations.submit(row.quotation_id, {})
    ElMessage.success(
      result.approval_required
        ? `已提交审批（实例 ${result.approve_instance_id || '-'}）`
        : '折扣与金额未触发审批，后端自动通过',
    )
    await load()
    if (detail.value?.quotation_id === row.quotation_id) {
      detail.value = await crmApi.quotations.detail(row.quotation_id)
    }
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function approve(row: QuotationListItemView | null, result: 'APPROVED' | 'REJECTED') {
  const target = row ?? detail.value
  if (!target) return
  const id = target.quotation_id
  try {
    const { value } = await ElMessageBox.prompt(`请输入${result === 'APPROVED' ? '批准' : '驳回'}意见`, '报价审批回调', {
      inputPlaceholder: '意见将写入审批记录',
      inputValue: result === 'APPROVED' ? '同意' : '价格需调整',
    })
    await crmApi.quotations.approve(id, { result, comment: value })
    ElMessage.success('审批结果已回写')
    await load()
    window.setTimeout(async () => {
      if (detailVisible.value) detail.value = await crmApi.quotations.detail(id)
    }, 300)
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

function openConvert() {
  if (!detail.value) return
  const start = new Date()
  const end = new Date()
  end.setFullYear(end.getFullYear() + 1)
  Object.assign(convertForm, {
    sla_level: 'P2',
    service_package: '',
    start_date: start.toISOString().slice(0, 10),
    end_date: end.toISOString().slice(0, 10),
    reason: '',
  })
  convertError.value = ''
  convertVisible.value = true
}

async function submitConvert() {
  if (!detail.value) return
  if (!convertForm.start_date || !convertForm.end_date) {
    convertError.value = '合同起止日期为必填项'
    return
  }
  converting.value = true
  convertError.value = ''
  try {
    const contract = await crmApi.quotations.convertToContract(detail.value.quotation_id, {
      sla_level: convertForm.sla_level,
      service_package: convertForm.service_package || undefined,
      start_date: convertForm.start_date,
      end_date: convertForm.end_date,
      reason: convertForm.reason || undefined,
    })
    ElMessage.success(`已转合同：${contract.contract_no}`)
    convertVisible.value = false
    detailVisible.value = false
    await load()
  } catch (err) {
    convertError.value = describeError(err)
  } finally {
    converting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="云客 CRM · 报价" title="报价管理" description="报价单创建、提交审批、审批状态回写与转合同；列表接口为本次后端补齐的只读扩展。">
      <template #actions>
        <el-button v-perm="'crm:quotation:create'" type="primary" @click="openCreate">新建报价</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '原 01 契约未定义报价列表接口，本页使用本次补齐的 GET /api/crm/v1/quotations（只读）。',
        '综合折扣 > 20% 或含税总额 > 100 万时提交会进入工作流中心；否则后端自动通过。',
        '工作流审批任务在本页以“审批回调”接口演示回写；流程中心的任务办理 UI 尚未联动到报价。',
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="query.keyword" placeholder="报价单号/客户名称" clearable style="width: 220px" @keyup.enter="query.page = 1; load()" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input v-model="query.customer_id" placeholder="客户 ID" clearable style="width: 160px" />
        <el-input-number v-model="query.owner_id" :min="1" placeholder="负责人 ID" :controls="false" style="width: 140px" />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="query.keyword = ''; query.status = ''; query.customer_id = undefined; query.owner_id = undefined; query.page = 1; load()">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="quotation_no" label="报价单号" width="180" />
        <el-table-column prop="customer_name" label="客户" min-width="150" />
        <el-table-column prop="opportunity_id" label="商机 ID" width="90" />
        <el-table-column prop="version_no" label="版本" width="80" />
        <el-table-column prop="total_amount" label="含税总额" width="130" />
        <el-table-column prop="status" label="审批状态" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : row.status === 'CONVERTED' ? 'info' : 'warning'"
              size="small"
            >
              {{ statusOptions.find((o) => o.value === row.status)?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner_id" label="负责人" width="90" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-perm="'crm:quotation:submit'"
              link
              type="warning"
              @click="submitApproval(row)"
            >
              提交审批
            </el-button>
            <el-button v-if="row.status === 'PENDING'" v-perm="'crm:quotation:approve'" link type="success" @click="approve(row, 'APPROVED')">批准</el-button>
            <el-button v-if="row.status === 'PENDING'" v-perm="'crm:quotation:approve'" link type="danger" @click="approve(row, 'REJECTED')">驳回</el-button>
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

    <el-dialog v-model="createVisible" title="新建报价" width="820px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="客户 ID" required>
          <el-input v-model="createForm.customer_id" placeholder="雪花 ID，按文本透传" clearable />
        </el-form-item>
        <el-form-item label="商机 ID">
          <el-input v-model="createForm.opportunity_id" placeholder="雪花 ID，按文本透传" clearable />
        </el-form-item>
        <el-form-item label="税率(%)">
          <el-input-number v-model="createForm.tax_rate" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="有效期至">
          <el-date-picker v-model="createForm.valid_until" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <div class="items-head">
        <strong>报价明细</strong>
        <el-button size="small" @click="addItem">添加行</el-button>
      </div>
      <el-table :data="items" size="small" border>
        <el-table-column label="产品编码" min-width="140">
          <template #default="{ row }"><el-input v-model="row.product_code" size="small" /></template>
        </el-table-column>
        <el-table-column label="产品名称" min-width="140">
          <template #default="{ row }"><el-input v-model="row.product_name" size="small" /></template>
        </el-table-column>
        <el-table-column label="数量" width="100">
          <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0.01" :precision="2" size="small" :controls="false" style="width: 100%" /></template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }"><el-input-number v-model="row.unit_price" :min="0" :precision="2" size="small" :controls="false" style="width: 100%" /></template>
        </el-table-column>
        <el-table-column label="折扣(%)" width="100">
          <template #default="{ row }"><el-input-number v-model="row.discount_rate" :min="0" :max="100" size="small" :controls="false" style="width: 100%" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }"><el-button link type="danger" size="small" @click="removeItem($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="报价详情" size="720px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="报价单号">{{ detail.quotation_no }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
            <el-descriptions-item label="客户 ID">{{ detail.customer_id }}</el-descriptions-item>
            <el-descriptions-item label="商机 ID">{{ detail.opportunity_id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ detail.version_no }}</el-descriptions-item>
            <el-descriptions-item label="含税总额">{{ detail.total_amount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="综合折扣">{{ detail.discount_rate ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="审批实例">{{ detail.approve_instance_id || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="actions">
            <el-button v-if="detail.status === 'DRAFT'" v-perm="'crm:quotation:submit'" size="small" type="warning" @click="submitApproval(detail)">提交审批</el-button>
            <el-button v-if="detail.status === 'PENDING'" v-perm="'crm:quotation:approve'" size="small" type="success" @click="approve(null, 'APPROVED')">批准</el-button>
            <el-button v-if="detail.status === 'PENDING'" v-perm="'crm:quotation:approve'" size="small" type="danger" @click="approve(null, 'REJECTED')">驳回</el-button>
            <el-button v-if="detail.status === 'APPROVED'" v-perm="'crm:contract:manage'" size="small" type="primary" @click="openConvert">转合同</el-button>
          </div>

          <h3 class="sub">报价明细</h3>
          <el-table :data="detail.items" size="small" border>
            <el-table-column prop="line_no" label="行号" width="70" />
            <el-table-column prop="product_code" label="产品编码" min-width="130" />
            <el-table-column prop="product_name" label="产品名称" min-width="130" />
            <el-table-column prop="quantity" label="数量" width="90" />
            <el-table-column prop="unit_price" label="单价" width="110" />
            <el-table-column prop="discount_rate" label="折扣" width="90" />
            <el-table-column prop="amount" label="金额" width="120" />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="convertVisible" title="报价转合同" width="560px">
      <el-alert v-if="convertError" type="error" :closable="false" show-icon :title="convertError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="SLA 等级">
          <el-select v-model="convertForm.sla_level" style="width: 100%">
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务包">
          <el-input v-model="convertForm.service_package" maxlength="64" />
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="convertForm.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="convertForm.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="convertForm.reason" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="converting" @click="submitConvert">确认转合同</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.items-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 0;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}
.sub {
  margin: 8px 0;
  font-size: 14px;
}
</style>
