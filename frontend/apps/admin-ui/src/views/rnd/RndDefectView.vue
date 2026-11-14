<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH } from '@fenglin/ui-admin'
import { rndApi, type RndDefect, type RndId, type RndProduct } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const defects = ref<RndDefect[]>([])
const selectedProductId = ref<RndId | ''>('')
const statusFilter = ref('')
const severityFilter = ref('')
const loading = ref(false)
const error = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({
  title: '',
  severity: 'MAJOR',
  priority: 'P2',
  env: 'TEST',
  found_stage: 'TESTING',
  assignee_id: '',
  reproduce_steps: '',
})

const transitions: Record<string, string[]> = {
  new: ['confirmed', 'rejected'],
  confirmed: ['fixing', 'rejected'],
  fixing: ['fixed'],
  fixed: ['verifying'],
  verifying: ['closed', 'reopened'],
  reopened: ['fixing', 'confirmed'],
  closed: ['reopened'],
}

const statusLabels: Record<string, string> = {
  new: '新建',
  confirmed: '已确认',
  rejected: '已拒绝',
  fixing: '修复中',
  fixed: '已修复',
  verifying: '验证中',
  closed: '已关闭',
  reopened: '重新打开',
}

const gapItems = [
  '缺陷列表仅支持按产品/状态/严重度过滤，缺少按负责人、迭代或关键字查询的接口。',
  '关闭缺陷需要 biz:devops:defect:verify，普通研发账号只能流转到 verifying。',
  '重新打开超过 3 次会被后端拒绝（reopen_count 上限），本页原样展示后端错误。',
]

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'closed' || value === 'fixed') return 'success'
  if (value === 'rejected') return 'info'
  if (value === 'new' || value === 'reopened') return 'danger'
  return 'warning'
}

function severityType(value?: string) {
  if (value === 'BLOCKER' || value === 'CRITICAL') return 'danger'
  if (value === 'MAJOR') return 'warning'
  return 'info'
}

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
    await loadDefects()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadDefects() {
  if (!selectedProductId.value) {
    defects.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    defects.value = await rndApi.defects.list({
      productId: selectedProductId.value,
      status: statusFilter.value || undefined,
      severity: severityFilter.value || undefined,
      page: 1,
      size: 200,
    })
  } catch (err) {
    defects.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  if (!selectedProductId.value || !form.title.trim()) {
    createError.value = '请选择产品并填写缺陷标题'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    await rndApi.defects.create({
      product_id: selectedProductId.value,
      title: form.title.trim(),
      severity: form.severity,
      priority: form.priority,
      env: form.env,
      found_stage: form.found_stage,
      assignee_id: form.assignee_id ? form.assignee_id : undefined,
      reproduce_steps: form.reproduce_steps.trim() || undefined,
    })
    ElMessage.success('缺陷已创建')
    createVisible.value = false
    Object.assign(form, { title: '', assignee_id: '', reproduce_steps: '' })
    await loadDefects()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function transition(row: RndDefect, target: string) {
  try {
    await rndApi.defects.transition(row.id, target)
    ElMessage.success(`缺陷已流转至 ${statusLabel(target)}`)
    await loadDefects()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(loadProducts)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="缺陷管理"
      description="缺陷提报、严重度与流转闭环；数据来自 /api/rnd/v1/defects。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 220px" @change="loadDefects">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="loadDefects">
          <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="severityFilter" placeholder="全部严重度" clearable style="width: 130px" @change="loadDefects">
          <el-option label="阻断" value="BLOCKER" />
          <el-option label="严重" value="CRITICAL" />
          <el-option label="主要" value="MAJOR" />
          <el-option label="次要" value="MINOR" />
        </el-select>
        <el-button v-perm="'biz:devops:defect:create'" type="primary" @click="createVisible = true">提报缺陷</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="defects" v-loading="loading" border stripe max-height="560">
        <el-table-column prop="defect_no" label="缺陷编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column label="严重度" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="severityType(row.severity)">{{ row.severity ?? '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="env" label="环境" width="90" />
        <el-table-column prop="assignee_id" label="负责人" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="reopen_count" label="重开" width="80" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-dropdown v-if="transitions[row.status]" trigger="click" @command="(target: string) => transition(row, target)">
              <el-button v-perm="'biz:devops:defect:transition'" link type="primary">流转</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="target in transitions[row.status]" :key="target" :command="target">
                    {{ statusLabel(target) }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="提报缺陷" width="560px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="缺陷标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="严重度">
          <el-select v-model="form.severity" style="width: 100%">
            <el-option label="阻断" value="BLOCKER" />
            <el-option label="严重" value="CRITICAL" />
            <el-option label="主要" value="MAJOR" />
            <el-option label="次要" value="MINOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option label="P0 最高" value="P0" />
            <el-option label="P1 高" value="P1" />
            <el-option label="P2 中" value="P2" />
            <el-option label="P3 低" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="发现环境"><el-input v-model="form.env" maxlength="32" /></el-form-item>
        <el-form-item label="发现阶段"><el-input v-model="form.found_stage" maxlength="32" /></el-form-item>
        <el-form-item label="负责人ID"><el-input v-model="form.assignee_id" placeholder="选填" /></el-form-item>
        <el-form-item label="复现步骤"><el-input v-model="form.reproduce_steps" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
