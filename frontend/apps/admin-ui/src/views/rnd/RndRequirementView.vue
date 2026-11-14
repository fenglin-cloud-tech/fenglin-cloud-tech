<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH } from '@fenglin/ui-admin'
import { rndApi, type RndId, type RndProduct, type RndRequirement } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const requirements = ref<RndRequirement[]>([])
const selectedProductId = ref<RndId | ''>('')
const statusFilter = ref('')
const loading = ref(false)
const error = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({
  title: '',
  req_type: 'STORY',
  priority: 'P2',
  assignee_id: '',
  estimate_hours: '',
  acceptance_criteria: '',
  plan_start_at: '',
  plan_end_at: '',
})

const transitions: Record<string, string[]> = {
  TODO: ['REVIEWING', 'DOING'],
  REVIEWING: ['REVIEWED', 'REJECTED'],
  REJECTED: ['REVIEWING'],
  REVIEWED: ['DOING', 'CLOSED'],
  DOING: ['DONE', 'CLOSED'],
  DONE: ['CLOSED'],
}

const statusLabels: Record<string, string> = {
  TODO: '待处理',
  REVIEWING: '评审中',
  REVIEWED: '已评审',
  REJECTED: '已驳回',
  DOING: '进行中',
  DONE: '已完成',
  CLOSED: '已关闭',
}

const gapItems = [
  '需求列表只支持按产品/迭代/状态过滤，没有按负责人或关键字查询的接口。',
  '需求评审会调用工作流服务（rndRequirementReview），工作流服务不可用时提交评审返回真实错误。',
  '需求关联（relations）与改派迭代接口已实现，但本页未暴露关联关系可视化，仅保留流转与评审。',
]

const filteredRequirements = computed(() =>
  statusFilter.value ? requirements.value.filter((item) => item.status_code === statusFilter.value) : requirements.value,
)

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'DONE' || value === 'CLOSED' || value === 'REVIEWED') return 'success'
  if (value === 'REJECTED') return 'danger'
  if (value === 'DOING' || value === 'REVIEWING') return 'warning'
  return 'info'
}

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) {
      selectedProductId.value = products.value[0].id
    }
    await loadRequirements()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadRequirements() {
  if (!selectedProductId.value) {
    requirements.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    requirements.value = await rndApi.requirements.list({
      productId: selectedProductId.value,
      page: 1,
      size: 200,
    })
  } catch (err) {
    requirements.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  if (!selectedProductId.value || !form.title.trim()) {
    createError.value = '请选择产品并填写标题'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    await rndApi.requirements.create({
      product_id: selectedProductId.value,
      title: form.title.trim(),
      req_type: form.req_type,
      priority: form.priority,
      assignee_id: form.assignee_id ? form.assignee_id : undefined,
      estimate_hours: form.estimate_hours || undefined,
      acceptance_criteria: form.acceptance_criteria.trim() || undefined,
      plan_start_at: form.plan_start_at || undefined,
      plan_end_at: form.plan_end_at || undefined,
    })
    ElMessage.success('需求已创建')
    createVisible.value = false
    Object.assign(form, { title: '', assignee_id: '', estimate_hours: '', acceptance_criteria: '', plan_start_at: '', plan_end_at: '' })
    await loadRequirements()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function transition(row: RndRequirement, target: string) {
  try {
    await rndApi.requirements.transition(row.id, target)
    ElMessage.success(`需求已流转至 ${statusLabel(target)}`)
    await loadRequirements()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitReview(row: RndRequirement) {
  try {
    const result = await rndApi.requirements.review(row.id)
    ElMessage.success(`已提交评审（实例 ${result.approval_instance_id ?? '待工作流返回'}）`)
    await loadRequirements()
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
      title="需求管理"
      description="需求创建、状态流转与评审；数据来自 /api/rnd/v1/requirements。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 220px" @change="loadRequirements">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="code" />
        </el-select>
        <el-button v-perm="'biz:devops:requirement:create'" type="primary" @click="createVisible = true">新建需求</el-button>
        <el-button :loading="loading" @click="loadRequirements">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="filteredRequirements" v-loading="loading" border stripe max-height="560">
        <el-table-column prop="req_no" label="需求编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="req_type" label="类型" width="100" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status_code)">{{ row.status_name ?? statusLabel(row.status_code) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignee_id" label="负责人" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="estimate_hours" label="预估工时" width="110" />
        <el-table-column prop="plan_end_at" label="计划完成" width="130" />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-if="row.status_code === 'TODO'"
              v-perm="'biz:devops:requirement:review'"
              link
              type="primary"
              @click="submitReview(row)"
            >提交评审</el-button>
            <el-dropdown v-if="transitions[row.status_code]" trigger="click" @command="(target: string) => transition(row, target)">
              <el-button v-perm="'biz:devops:requirement:transition'" link type="primary">流转</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="target in transitions[row.status_code]" :key="target" :command="target">
                    {{ statusLabel(target) }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新建需求" width="580px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.req_type" style="width: 100%">
            <el-option label="需求" value="STORY" />
            <el-option label="任务" value="TASK" />
            <el-option label="缺陷" value="BUG" />
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
        <el-form-item label="负责人ID"><el-input v-model="form.assignee_id" placeholder="选填" /></el-form-item>
        <el-form-item label="预估工时"><el-input v-model="form.estimate_hours" placeholder="如 16" /></el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="form.plan_start_at" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划完成">
          <el-date-picker v-model="form.plan_end_at" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="验收标准"><el-input v-model="form.acceptance_criteria" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
