<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { rndApi, type RndId, type RndProduct, type RndTask, type RndWorklog } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const products = ref<RndProduct[]>([])
const tasks = ref<RndTask[]>([])
const worklogs = ref<RndWorklog[]>([])
const selectedProductId = ref<RndId | ''>('')
const statusFilter = ref('')
const loading = ref(false)
const error = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({
  title: '',
  task_type: 'DEV',
  priority: 'P2',
  assignee_id: '',
  estimate_hours: '',
  due_date: '',
})

const logVisible = ref(false)
const logSaving = ref(false)
const logError = ref('')
const logForm = reactive({ task_id: '', work_date: new Date().toISOString().slice(0, 10), hours: '8', remark: '' })

const transitions: Record<string, string[]> = {
  todo: ['doing', 'done'],
  doing: ['done', 'closed'],
  done: ['closed'],
}

const statusLabels: Record<string, string> = {
  todo: '待处理',
  doing: '进行中',
  done: '已完成',
  closed: '已关闭',
}

const gapItems = [
  '任务列表必须携带产品 ID，没有跨产品的“全部任务”查询接口。',
  '工时列表仅支持按任务 ID 或条数查询，缺少按员工/日期区间汇总的接口。',
  '任务状态流转固定为 todo → doing → done → closed，后端不接受自定义状态码。',
]

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'done' || value === 'closed') return 'success'
  if (value === 'doing') return 'warning'
  return 'info'
}

async function loadProducts() {
  try {
    products.value = await rndApi.products.list({ page: 1, size: 100 })
    if (!selectedProductId.value && products.value.length) selectedProductId.value = products.value[0].id
    await loadTasks()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadTasks() {
  if (!selectedProductId.value) {
    tasks.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    tasks.value = await rndApi.tasks.list({
      productId: selectedProductId.value,
      status: statusFilter.value || undefined,
      page: 1,
      size: 200,
    })
  } catch (err) {
    tasks.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadWorklogs() {
  try {
    worklogs.value = await rndApi.worklogs.list({ limit: 100 })
  } catch (err) {
    worklogs.value = []
    error.value = describeError(err)
  }
}

async function submitTask() {
  if (!selectedProductId.value || !form.title.trim()) {
    createError.value = '请选择产品并填写任务标题'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    await rndApi.tasks.create({
      product_id: selectedProductId.value,
      title: form.title.trim(),
      task_type: form.task_type,
      priority: form.priority,
      assignee_id: form.assignee_id ? form.assignee_id : undefined,
      estimate_hours: form.estimate_hours || undefined,
      due_date: form.due_date || undefined,
    })
    ElMessage.success('任务已创建')
    createVisible.value = false
    Object.assign(form, { title: '', assignee_id: '', estimate_hours: '', due_date: '' })
    await loadTasks()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function transition(row: RndTask, target: string) {
  try {
    await rndApi.tasks.transition(row.id, target)
    ElMessage.success(`任务已流转至 ${statusLabel(target)}`)
    await loadTasks()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitWorklog() {
  if (!logForm.task_id) {
    logError.value = '请填写任务 ID'
    return
  }
  logSaving.value = true
  logError.value = ''
  try {
    await rndApi.worklogs.create({
      task_id: logForm.task_id,
      work_date: logForm.work_date,
      hours: logForm.hours,
      remark: logForm.remark.trim() || undefined,
    })
    ElMessage.success('工时已登记')
    logVisible.value = false
    logForm.task_id = ''
    logForm.remark = ''
    await loadWorklogs()
  } catch (err) {
    logError.value = describeError(err)
  } finally {
    logSaving.value = false
  }
}

function taskTitle(id?: RndId | null) {
  if (!id) return '-'
  return tasks.value.find((item) => item.id === id)?.title ?? String(id)
}

onMounted(async () => {
  await loadProducts()
  await loadWorklogs()
})
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="任务与工时"
      description="任务创建、状态流转与工时登记；数据来自 /api/rnd/v1/tasks 与 /worklogs。"
    >
      <template #actions>
        <el-select v-model="selectedProductId" placeholder="选择产品" style="width: 220px" @change="loadTasks">
          <el-option v-for="product in products" :key="product.id" :label="`${product.product_code} ${product.name}`" :value="product.id" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px" @change="loadTasks">
          <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="code" />
        </el-select>
        <el-button v-perm="'biz:devops:task:create'" type="primary" @click="createVisible = true">新建任务</el-button>
        <el-button v-perm="'biz:devops:worklog:submit'" plain @click="logVisible = true">登记工时</el-button>
        <el-button :loading="loading" @click="loadTasks">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <el-table :data="tasks" v-loading="loading" border stripe max-height="420">
        <el-table-column prop="task_no" label="任务编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="task_type" label="类型" width="100" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignee_id" label="负责人" width="100" />
        <el-table-column prop="estimate_hours" label="预估" width="90" />
        <el-table-column prop="actual_hours" label="实际" width="90" />
        <el-table-column prop="due_date" label="截止" width="120" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-dropdown v-if="transitions[row.status]" trigger="click" @command="(target: string) => transition(row, target)">
              <el-button v-perm="'biz:devops:task:transition'" link type="primary">流转</el-button>
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

    <el-card shadow="never">
      <template #header><span>最近工时记录</span></template>
      <el-table :data="worklogs" border stripe size="small" max-height="300">
        <el-table-column prop="id" label="记录ID" width="100" />
        <el-table-column label="任务" min-width="200">
          <template #default="{ row }">{{ taskTitle(row.task_id) }}</template>
        </el-table-column>
        <el-table-column prop="user_id" label="用户ID" width="110" />
        <el-table-column prop="work_date" label="日期" width="120" />
        <el-table-column prop="hours" label="工时" width="90" />
        <el-table-column prop="remark" label="说明" min-width="180" />
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新建任务" width="540px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="任务标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.task_type" style="width: 100%">
            <el-option label="开发" value="DEV" />
            <el-option label="测试" value="TEST" />
            <el-option label="设计" value="DESIGN" />
            <el-option label="其他" value="OTHER" />
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
        <el-form-item label="预估工时"><el-input v-model="form.estimate_hours" placeholder="如 8" /></el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.due_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitTask">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logVisible" title="登记工时" width="480px">
      <el-alert v-if="logError" type="error" :closable="false" show-icon :title="logError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="任务ID" required><el-input v-model="logForm.task_id" /></el-form-item>
        <el-form-item label="工作日期">
          <el-date-picker v-model="logForm.work_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工时"><el-input v-model="logForm.hours" placeholder="如 8" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="logForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="logVisible = false">取消</el-button>
        <el-button type="primary" :loading="logSaving" @click="submitWorklog">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
