<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const assets = ref<ServiceRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ status: '', ownerId: '', assetType: '' })
const reminders = ref<ServiceRecord | null>(null)
const csat = ref<ServiceRecord | null>(null)
const loading = ref(false)
const error = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({ asset_no: '', name: '', asset_type: 'LAPTOP', owner_id: '', dept_id: '', ext_ref: '' })

const actionForm = reactive({ id: '', owner_id: '', days: 30 })

const gapItems = [
  '资产没有删除接口，只能通过状态（SCRAPPED）停用。',
  '借用/归还都要求 service:asset:manage，普通员工无法自助归还，需 IT 处理。',
  'CSAT 报表来自 /reports/csat，按周期汇总；没有按工单逐条反馈明细查询。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [assetResult, reminderResult, csatResult] = await Promise.all([
      serviceApi.assets.list({
        status: filters.status || undefined,
        ownerId: filters.ownerId ? filters.ownerId : undefined,
        assetType: filters.assetType || undefined,
        page: page.value,
        size: size.value,
      }),
      serviceApi.assets.reminders().catch(() => null),
      serviceApi.reports.csat().catch(() => null),
    ])
    assets.value = assetResult.records
    total.value = assetResult.total
    reminders.value = reminderResult
    csat.value = csatResult
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  if (!form.name.trim()) {
    createError.value = '请填写资产名称'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    await serviceApi.assets.create({
      asset_no: form.asset_no.trim() || undefined,
      name: form.name.trim(),
      asset_type: form.asset_type,
      owner_id: form.owner_id ? form.owner_id : undefined,
      dept_id: form.dept_id ? form.dept_id : undefined,
      ext_ref: form.ext_ref.trim() || undefined,
    })
    ElMessage.success('资产已创建')
    createVisible.value = false
    form.name = ''
    form.asset_no = ''
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function borrow() {
  if (!actionForm.id || !actionForm.owner_id) {
    ElMessage.warning('请填写资产 ID 与借用人工号')
    return
  }
  try {
    await serviceApi.assets.borrow(actionForm.id, {
      owner_id: actionForm.owner_id,
      days: Number(actionForm.days),
    })
    ElMessage.success('借用成功')
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function returnAsset() {
  if (!actionForm.id) {
    ElMessage.warning('请填写资产 ID')
    return
  }
  try {
    await serviceApi.assets.returnAsset(actionForm.id, {})
    ElMessage.success('归还成功')
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="资产与满意度"
      description="IT 资产台账、借用归还、到期提醒与 CSAT 汇总；数据来自 /api/service/v1/assets 与 /reports/csat。"
    >
      <template #actions>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px" @change="changePage(1)">
          <el-option label="在库" value="IN_STOCK" />
          <el-option label="使用中" value="IN_USE" />
          <el-option label="维修中" value="REPAIRING" />
          <el-option label="已报废" value="SCRAPPED" />
        </el-select>
        <el-input v-model="filters.ownerId" placeholder="使用人ID" clearable style="width: 130px" @keyup.enter="changePage(1)" />
        <el-button v-perm="'service:asset:manage'" type="primary" @click="createVisible = true">新增资产</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>借用 / 归还</span></template>
          <el-form inline>
            <el-form-item label="资产ID"><el-input v-model="actionForm.id" style="width: 120px" /></el-form-item>
            <el-form-item label="使用人"><el-input v-model="actionForm.owner_id" style="width: 120px" /></el-form-item>
            <el-form-item label="天数"><el-input-number v-model="actionForm.days" :min="1" style="width: 110px" /></el-form-item>
            <el-form-item>
              <el-button v-perm="'service:asset:manage'" type="primary" @click="borrow">借用</el-button>
              <el-button v-perm="'service:asset:manage'" plain @click="returnAsset">归还</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>满意度与提醒</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="平均分">{{ csat?.avg_score ?? csat?.score ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="样本数">{{ csat?.sample_count ?? csat?.total ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="借用到期提醒">{{ reminders?.due_soon ?? reminders?.count ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="已逾期">{{ reminders?.overdue ?? '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-table :data="assets" v-loading="loading" border stripe max-height="480">
        <el-table-column prop="asset_no" label="资产编号" width="150" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="asset_type" label="类型" width="120" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="owner_id" label="使用人" width="100" />
        <el-table-column prop="dept_id" label="部门" width="100" />
        <el-table-column prop="borrow_due_at" label="借用到期" width="170" />
        <el-table-column prop="ext_ref" label="外部引用" min-width="150" />
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="changePage"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="新增资产" width="520px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="资产编号"><el-input v-model="form.asset_no" placeholder="留空自动生成" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="120" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.asset_type" style="width: 100%">
            <el-option label="笔记本" value="LAPTOP" />
            <el-option label="台式机" value="DESKTOP" />
            <el-option label="显示器" value="MONITOR" />
            <el-option label="软件许可" value="LICENSE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用人ID"><el-input v-model="form.owner_id" /></el-form-item>
        <el-form-item label="部门ID"><el-input v-model="form.dept_id" /></el-form-item>
        <el-form-item label="外部引用"><el-input v-model="form.ext_ref" placeholder="选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
