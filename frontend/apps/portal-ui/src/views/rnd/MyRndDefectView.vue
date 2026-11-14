<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { rndApi, type RndDefect, type RndId, type RndProduct } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const defects = ref<RndDefect[]>([])
const selectedProductId = ref<RndId | ''>('')
const scope = ref<'reported' | 'assigned' | 'all'>('reported')
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
  reproduce_steps: '',
})

const gapItems = [
  '缺陷流转由研发/测试处理，员工端不提供关闭权限。',
  '选择产品仅用于提报缺陷，列表已由服务端按当前账号返回本人上报/被指派的数据。',
]

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

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'closed' || value === 'fixed') return 'success'
  if (value === 'rejected') return 'info'
  if (value === 'new' || value === 'reopened') return 'danger'
  return 'warning'
}

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadDefects() {
  loading.value = true
  error.value = ''
  try {
    defects.value = await rndApi.defects.my({ scope: scope.value, page: 1, size: 200 })
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
  const productId = selectedProductId.value
  try {
    await rndApi.defects.create({
      product_id: productId,
      title: form.title.trim(),
      severity: form.severity,
      priority: form.priority,
      env: form.env,
      found_stage: 'USER',
      reproduce_steps: form.reproduce_steps.trim() || undefined,
    })
    ElMessage.success('缺陷已提交')
    createVisible.value = false
    form.title = ''
    form.reproduce_steps = ''
    await loadDefects()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

onMounted(async () => {
  await loadProducts()
  await loadDefects()
})
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="缺陷提报"
      description="提报缺陷并跟踪我上报/指派给我的缺陷；数据来自 /api/rnd/v1/defects/my。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 200px">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-radio-group v-model="scope" @change="loadDefects">
          <el-radio-button label="reported">我上报的</el-radio-button>
          <el-radio-button label="assigned">指派给我</el-radio-button>
          <el-radio-button label="all">全部</el-radio-button>
        </el-radio-group>
        <el-button v-perm="'biz:devops:defect:self:create'" type="primary" @click="createVisible = true">提报缺陷</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="defects" v-loading="loading" border stripe max-height="560">
        <el-table-column prop="defect_no" label="缺陷编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="severity" label="严重度" width="100" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="env" label="环境" width="90" />
        <el-table-column prop="reporter_id" label="上报人" width="100" />
        <el-table-column prop="assignee_id" label="负责人" width="100" />
        <el-table-column prop="created_at" label="创建时间" width="180" />
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
        <el-form-item label="复现步骤"><el-input v-model="form.reproduce_steps" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
