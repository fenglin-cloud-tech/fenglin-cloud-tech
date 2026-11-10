<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { financeApi, type FinanceProject, type FinanceTimesheet } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const projects = ref<FinanceProject[]>([])
const timesheets = ref<FinanceTimesheet[]>([])
const loading = ref(false)
const error = ref('')
const saving = ref(false)
const dialogVisible = ref(false)

const myTimesheets = computed(() => timesheets.value)

const totalHours = computed(() =>
  myTimesheets.value.reduce((sum, item) => sum + (Number(item.hours) || 0), 0).toFixed(2),
)

const gapItems = [
  '员工端只能填报与查看，审批 /projects/timesheets/approve 需要 biz:finance:timesheet:approve，不在本页提供。',
  '项目下拉依赖 biz:finance:project:view；若员工账号未授予该权限，页面只显示接口真实 403 提示。',
]

const statusLabels: Record<string, string> = {
  DRAFT: '待提交',
  SUBMITTED: '待审批',
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'APPROVED') return 'success'
  if (value === 'REJECTED') return 'danger'
  if (value === 'SUBMITTED' || value === 'PENDING') return 'warning'
  return 'info'
}

const today = new Date().toISOString().slice(0, 10)
const form = reactive({
  project_id: '' as number | '',
  work_date: today,
  hours: '8',
  task_desc: '',
})

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    projects.value = await financeApi.projects.list({ page: 1, size: 100 })
    timesheets.value = await financeApi.projects.myTimesheets({ page: 1, size: 100 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!auth.userId) {
    ElMessage.error('当前令牌未返回用户 ID，无法填报工时')
    return
  }
  if (!form.project_id) {
    ElMessage.warning('请选择项目')
    return
  }
  if (!form.hours || Number(form.hours) <= 0) {
    ElMessage.warning('请填写有效工时')
    return
  }
  saving.value = true
  try {
    await financeApi.projects.createTimesheet(Number(form.project_id), {
      employee_id: auth.userId,
      employee_name: auth.displayName,
      work_date: form.work_date,
      hours: form.hours,
      task_desc: form.task_desc || undefined,
    })
    ElMessage.success('工时已填报')
    dialogVisible.value = false
    form.task_desc = ''
    await loadAll()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

function projectName(id: number) {
  return projects.value.find((item) => item.id === id)?.project_name ?? String(id)
}

onMounted(loadAll)
</script>

<template>
  <div>
    <PageHeader module="云财 Finance" title="我的工时" description="按项目填报工时并查看本人记录；数据直接来自项目工时接口。" />

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-button type="primary" @click="dialogVisible = true">填报工时</el-button>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
        <el-tag type="info" effect="plain">本人合计 {{ totalHours }} 小时</el-tag>
        <span class="fl-muted">{{ auth.displayName }}</span>
      </div>

      <div v-loading="loading">
        <el-empty v-if="!myTimesheets.length && !error" description="暂无本人工时记录" :image-size="80" />
        <el-table v-else :data="myTimesheets" border stripe>
          <el-table-column prop="timesheet_no" label="工时单号" width="180" />
          <el-table-column label="项目" min-width="160">
            <template #default="{ row }">{{ projectName(row.project_id) }}</template>
          </el-table-column>
          <el-table-column prop="work_date" label="工作日期" width="120" />
          <el-table-column prop="hours" label="工时" width="90" />
          <el-table-column prop="cost_rate" label="成本单价" width="110" />
          <el-table-column prop="cost_amount" label="成本金额" width="120" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reject_reason" label="驳回原因" min-width="140" />
        </el-table>
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="填报工时" width="520px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="项目">
          <el-select v-model="form.project_id" filterable placeholder="选择项目" style="width: 100%">
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="`${project.project_no} ${project.project_name}`"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工作日期">
          <el-date-picker v-model="form.work_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工时">
          <el-input v-model="form.hours" placeholder="例如 8" />
        </el-form-item>
        <el-form-item label="工作说明">
          <el-input v-model="form.task_desc" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
