<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const active = ref('problem')
const saving = ref(false)
const error = ref('')

const problemQuery = reactive({ status: '', keyword: '', page: 1, size: 10 })
const problemRows = ref<ServiceRecord[]>([])
const problemTotal = ref(0)
const problemLoading = ref(false)
const changeQuery = reactive({ status: '', keyword: '', page: 1, size: 10 })
const changeRows = ref<ServiceRecord[]>([])
const changeTotal = ref(0)
const changeLoading = ref(false)
const detailVisible = ref(false)
const detailTitle = ref('')
const detail = ref<ServiceRecord | null>(null)

const problemForm = reactive({
  title: '',
  description: '',
  root_cause: '',
  workaround: '',
  known_error: false,
  owner_id: '',
  ticket_ids: '',
})

const aggregateForm = reactive({
  id: '',
  ticket_ids: '',
  status: 'KNOWN_ERROR',
  root_cause: '',
  workaround: '',
  known_error: true,
})

const changeForm = reactive({
  title: '',
  change_type: 'NORMAL',
  plan: '',
  rollback_plan: '',
  risk_level: 'MEDIUM',
  planned_start: '',
  planned_end: '',
  approval_flow_key: '',
  owner_id: '',
})

const approveForm = reactive({ id: '', approved: true, comment: '' })

const gapItems = [
  '问题与变更列表已支持按状态、关键字（编号/标题）过滤并分页，点击行可查看详情。',
  '问题聚合需要先知道工单 ID，工单 ID 可在“工单工作台”查看。',
  '变更审批依赖工作流（svcChangeApproval），工作流服务不可用时返回真实错误。',
]

async function loadProblems() {
  problemLoading.value = true
  try {
    const page = await serviceApi.problems.list({
      status: problemQuery.status || undefined,
      keyword: problemQuery.keyword || undefined,
      page: problemQuery.page,
      size: problemQuery.size,
    })
    problemRows.value = page.records ?? []
    problemTotal.value = page.total ?? 0
  } catch (err) {
    error.value = describeError(err)
  } finally {
    problemLoading.value = false
  }
}

async function loadChanges() {
  changeLoading.value = true
  try {
    const page = await serviceApi.changes.list({
      status: changeQuery.status || undefined,
      keyword: changeQuery.keyword || undefined,
      page: changeQuery.page,
      size: changeQuery.size,
    })
    changeRows.value = page.records ?? []
    changeTotal.value = page.total ?? 0
  } catch (err) {
    error.value = describeError(err)
  } finally {
    changeLoading.value = false
  }
}

async function openProblem(row: ServiceRecord) {
  try {
    detail.value = await serviceApi.problems.get(row.id)
    detailTitle.value = `问题 ${detail.value.problem_no ?? detail.value.id}`
    detailVisible.value = true
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function openChange(row: ServiceRecord) {
  try {
    detail.value = await serviceApi.changes.get(row.id)
    detailTitle.value = `变更 ${detail.value.change_no ?? detail.value.id}`
    detailVisible.value = true
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(() => {
  loadProblems()
  loadChanges()
})

function ids(text: string) {
  return text
    .split(',')
    .map((item) => item.trim())
    .filter((item) => /^\d+$/.test(item) && item !== '0')
}

async function createProblem() {
  if (!problemForm.title.trim()) {
    error.value = '请填写问题标题'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const created = await serviceApi.problems.create({
      title: problemForm.title.trim(),
      description: problemForm.description.trim() || undefined,
      root_cause: problemForm.root_cause.trim() || undefined,
      workaround: problemForm.workaround.trim() || undefined,
      known_error: problemForm.known_error,
      owner_id: problemForm.owner_id ? problemForm.owner_id : undefined,
      ticket_ids: ids(problemForm.ticket_ids),
    })
    ElMessage.success(`问题已创建：${created.problem_no ?? created.id}`)
    problemForm.title = ''
    problemForm.description = ''
    problemForm.ticket_ids = ''
    await loadProblems()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function aggregate() {
  if (!aggregateForm.id) {
    error.value = '请填写问题 ID'
    return
  }
  saving.value = true
  error.value = ''
  try {
    await serviceApi.problems.aggregate(aggregateForm.id, {
      ticket_ids: ids(aggregateForm.ticket_ids),
      status: aggregateForm.status,
      root_cause: aggregateForm.root_cause.trim() || undefined,
      workaround: aggregateForm.workaround.trim() || undefined,
      known_error: aggregateForm.known_error,
    })
    ElMessage.success('问题已聚合')
    await loadProblems()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function createChange() {
  if (!changeForm.title.trim()) {
    error.value = '请填写变更标题'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const created = await serviceApi.changes.create({
      title: changeForm.title.trim(),
      change_type: changeForm.change_type,
      plan: changeForm.plan.trim() || undefined,
      rollback_plan: changeForm.rollback_plan.trim() || undefined,
      risk_level: changeForm.risk_level,
      planned_start: changeForm.planned_start || undefined,
      planned_end: changeForm.planned_end || undefined,
      approval_flow_key: changeForm.approval_flow_key.trim() || undefined,
      owner_id: changeForm.owner_id ? changeForm.owner_id : undefined,
    })
    ElMessage.success(`变更已创建：${created.change_no ?? created.id}`)
    changeForm.title = ''
    await loadChanges()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function approveChange() {
  if (!approveForm.id) {
    error.value = '请填写变更 ID'
    return
  }
  saving.value = true
  error.value = ''
  try {
    await serviceApi.changes.approve(approveForm.id, {
      approved: approveForm.approved,
      comment: approveForm.comment.trim() || undefined,
    })
    ElMessage.success('审批结果已提交')
    await loadChanges()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="问题与变更"
      description="问题创建/聚合与变更审批；数据来自 /api/service/v1/problems 与 /changes。"
    />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="active">
        <el-tab-pane label="问题管理" name="problem">
          <el-row :gutter="8" style="margin-bottom: 8px">
            <el-col :span="5"><el-input v-model="problemQuery.keyword" placeholder="编号/标题关键字" clearable @keyup.enter="loadProblems" /></el-col>
            <el-col :span="4">
              <el-select v-model="problemQuery.status" placeholder="全部状态" clearable style="width: 100%">
                <el-option label="待分析" value="NEW" />
                <el-option label="分析中" value="INVESTIGATING" />
                <el-option label="已知错误" value="KNOWN_ERROR" />
                <el-option label="已解决" value="RESOLVED" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
            </el-col>
            <el-col :span="3"><el-button type="primary" plain @click="problemQuery.page = 1; loadProblems()">查询</el-button></el-col>
          </el-row>
          <el-table v-loading="problemLoading" :data="problemRows" border stripe size="small" @row-click="openProblem">
            <el-table-column prop="problem_no" label="编号" width="150" />
            <el-table-column prop="title" label="标题" min-width="220" />
            <el-table-column prop="status" label="状态" width="130" />
            <el-table-column prop="known_error" label="已知错误" width="90" />
            <el-table-column prop="owner_id" label="负责人" width="110" />
            <el-table-column prop="created_at" label="创建时间" width="170" />
          </el-table>
          <el-pagination
            v-model:current-page="problemQuery.page"
            :page-size="problemQuery.size"
            :total="problemTotal"
            layout="total, prev, pager, next"
            style="margin: 8px 0 16px; justify-content: flex-end"
            @current-change="loadProblems"
          />
          <el-row :gutter="16">
            <el-col :span="12">
              <h4>创建问题</h4>
              <el-form label-width="100px">
                <el-form-item label="标题" required><el-input v-model="problemForm.title" maxlength="200" /></el-form-item>
                <el-form-item label="描述"><el-input v-model="problemForm.description" type="textarea" :rows="3" /></el-form-item>
                <el-form-item label="根因"><el-input v-model="problemForm.root_cause" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="临时方案"><el-input v-model="problemForm.workaround" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="已知错误"><el-switch v-model="problemForm.known_error" /></el-form-item>
                <el-form-item label="负责人ID"><el-input v-model="problemForm.owner_id" /></el-form-item>
                <el-form-item label="关联工单"><el-input v-model="problemForm.ticket_ids" placeholder="逗号分隔工单ID" /></el-form-item>
                <el-form-item>
                  <el-button v-perm="'service:problem:manage'" type="primary" :loading="saving" @click="createProblem">创建问题</el-button>
                </el-form-item>
              </el-form>
            </el-col>
            <el-col :span="12">
              <h4>聚合问题</h4>
              <el-form label-width="100px">
                <el-form-item label="问题ID" required><el-input v-model="aggregateForm.id" /></el-form-item>
                <el-form-item label="工单ID"><el-input v-model="aggregateForm.ticket_ids" placeholder="逗号分隔" /></el-form-item>
                <el-form-item label="状态"><el-input v-model="aggregateForm.status" /></el-form-item>
                <el-form-item label="根因"><el-input v-model="aggregateForm.root_cause" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="临时方案"><el-input v-model="aggregateForm.workaround" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="已知错误"><el-switch v-model="aggregateForm.known_error" /></el-form-item>
                <el-form-item>
                  <el-button v-perm="'service:problem:manage'" type="primary" plain :loading="saving" @click="aggregate">执行聚合</el-button>
                </el-form-item>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="变更管理" name="change">
          <el-row :gutter="8" style="margin-bottom: 8px">
            <el-col :span="5"><el-input v-model="changeQuery.keyword" placeholder="编号/标题关键字" clearable @keyup.enter="loadChanges" /></el-col>
            <el-col :span="4">
              <el-select v-model="changeQuery.status" placeholder="全部状态" clearable style="width: 100%">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="待审批" value="PENDING_APPROVAL" />
                <el-option label="已批准" value="APPROVED" />
                <el-option label="已驳回" value="REJECTED" />
                <el-option label="实施中" value="IMPLEMENTING" />
                <el-option label="已完成" value="COMPLETED" />
              </el-select>
            </el-col>
            <el-col :span="3"><el-button type="primary" plain @click="changeQuery.page = 1; loadChanges()">查询</el-button></el-col>
          </el-row>
          <el-table v-loading="changeLoading" :data="changeRows" border stripe size="small" @row-click="openChange">
            <el-table-column prop="change_no" label="编号" width="150" />
            <el-table-column prop="title" label="标题" min-width="220" />
            <el-table-column prop="change_type" label="类型" width="110" />
            <el-table-column prop="risk_level" label="风险" width="90" />
            <el-table-column prop="status" label="状态" width="130" />
            <el-table-column prop="planned_start" label="计划开始" width="170" />
          </el-table>
          <el-pagination
            v-model:current-page="changeQuery.page"
            :page-size="changeQuery.size"
            :total="changeTotal"
            layout="total, prev, pager, next"
            style="margin: 8px 0 16px; justify-content: flex-end"
            @current-change="loadChanges"
          />
          <el-row :gutter="16">
            <el-col :span="12">
              <h4>创建变更</h4>
              <el-form label-width="110px">
                <el-form-item label="标题" required><el-input v-model="changeForm.title" maxlength="200" /></el-form-item>
                <el-form-item label="变更类型">
                  <el-select v-model="changeForm.change_type" style="width: 100%">
                    <el-option label="标准变更" value="STANDARD" />
                    <el-option label="普通变更" value="NORMAL" />
                    <el-option label="紧急变更" value="EMERGENCY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="风险等级">
                  <el-select v-model="changeForm.risk_level" style="width: 100%">
                    <el-option label="高" value="HIGH" />
                    <el-option label="中" value="MEDIUM" />
                    <el-option label="低" value="LOW" />
                  </el-select>
                </el-form-item>
                <el-form-item label="实施计划"><el-input v-model="changeForm.plan" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="回滚方案"><el-input v-model="changeForm.rollback_plan" type="textarea" :rows="2" /></el-form-item>
                <el-form-item label="计划开始">
                  <el-date-picker v-model="changeForm.planned_start" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
                </el-form-item>
                <el-form-item label="计划结束">
                  <el-date-picker v-model="changeForm.planned_end" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
                </el-form-item>
                <el-form-item label="审批流Key"><el-input v-model="changeForm.approval_flow_key" placeholder="选填" /></el-form-item>
                <el-form-item label="负责人ID"><el-input v-model="changeForm.owner_id" /></el-form-item>
                <el-form-item>
                  <el-button v-perm="'service:change:manage'" type="primary" :loading="saving" @click="createChange">创建变更</el-button>
                </el-form-item>
              </el-form>
            </el-col>
            <el-col :span="12">
              <h4>审批变更</h4>
              <el-form label-width="110px">
                <el-form-item label="变更ID" required><el-input v-model="approveForm.id" /></el-form-item>
                <el-form-item label="审批结果"><el-switch v-model="approveForm.approved" active-text="通过" inactive-text="驳回" /></el-form-item>
                <el-form-item label="审批意见"><el-input v-model="approveForm.comment" type="textarea" :rows="3" /></el-form-item>
                <el-form-item>
                  <el-button v-perm="'service:change:manage'" type="primary" :loading="saving" @click="approveChange">提交审批</el-button>
                </el-form-item>
              </el-form>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="detailVisible" :title="detailTitle" width="640px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item v-for="(value, key) in detail" :key="key" :label="String(key)">
          {{ value === null || value === undefined || value === '' ? '-' : String(value) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>
