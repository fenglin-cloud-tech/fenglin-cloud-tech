<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  collabApi,
  type CollabApprovalInstance,
  type CollabApprovalRow,
  type CollabApprovalTemplate,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const activeTab = ref('tasks')

const gapItems = [
  '我的待办/我的申请返回 Map，字段未强类型化，页面按返回键动态渲染。',
  '没有动态表单引擎，form_data 以 JSON 文本提交，结构由模板 form_schema 约定。',
  '已办列表已提供（/approvals/tasks/done，含审批意见明文）；撤回后的实例在“我的申请”中保留 CANCELLED 状态可查。',
  '抄送列表尚未提供：需要新增 collab_approval_cc 表与提交时写入抄送人，属表结构变更，未在本批实施。',
]

function formatCell(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function formatTime(value?: string): string {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function pickId(row: CollabApprovalRow, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = row[key]
    if (value !== null && value !== undefined && String(value) !== '') return String(value)
  }
  return undefined
}

function rowTaskId(row: CollabApprovalRow): string | undefined {
  // task_id 是 19 位雪花 ID，转 number 会被舍入导致办理失败，这里保持字符串原样透传
  return pickId(row, ['task_id', 'taskId', 'id'])
}

function rowInstanceId(row: CollabApprovalRow): string | undefined {
  return pickId(row, ['instance_id', 'instanceId'])
}

const taskRows = ref<CollabApprovalRow[]>([])
const taskLoading = ref(false)
const taskError = ref('')
const taskColumns = computed(() => (taskRows.value.length ? Object.keys(taskRows.value[0]) : []))

const instRows = ref<CollabApprovalRow[]>([])
const instLoading = ref(false)
const instError = ref('')
const instQuery = reactive({ page: 1, size: 10 })
const instTotal = ref(0)
const instColumns = computed(() => (instRows.value.length ? Object.keys(instRows.value[0]) : []))

const doneRows = ref<(CollabApprovalRow & { comment?: string })[]>([])
const doneLoading = ref(false)
const doneError = ref('')
const doneQuery = reactive({ page: 1, size: 10, action: '' })
const doneTotal = ref(0)
const doneColumns = computed(() => (doneRows.value.length ? Object.keys(doneRows.value[0]) : []))

const templateRows = ref<CollabApprovalTemplate[]>([])
const templateLoading = ref(false)
const templateError = ref('')

const actionVisible = ref(false)
const actionSaving = ref(false)
const actionError = ref('')
const actionMode = ref<'approve' | 'reject' | 'transfer' | 'addSign'>('approve')
const actionTaskId = ref<string | null>(null)
const actionForm = reactive({ comment: '', assignee_id: '', reason: '', position: '' })
const actionTitles: Record<string, string> = {
  approve: '通过审批',
  reject: '驳回审批',
  transfer: '转交审批',
  addSign: '加签',
}
const actionTitle = computed(() => actionTitles[actionMode.value])

const instanceVisible = ref(false)
const instanceLoading = ref(false)
const instance = ref<CollabApprovalInstance | null>(null)

const submitVisible = ref(false)
const submitSaving = ref(false)
const submitError = ref('')
const submitResult = ref('')
const submitForm = reactive({ template_id: '', approver_id: '', form_data: '{}' })
const idempotencyKey = ref('')

function genIdempotencyKey(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

async function loadTasks() {
  taskLoading.value = true
  taskError.value = ''
  try {
    taskRows.value = await collabApi.approvals.myTasks()
  } catch (err) {
    taskError.value = describeError(err)
  } finally {
    taskLoading.value = false
  }
}

async function loadInstances() {
  instLoading.value = true
  instError.value = ''
  try {
    const result = await collabApi.approvals.myInstances({ page: instQuery.page, size: instQuery.size })
    instRows.value = result
    // 接口只返回列表不返回总数：满页时补一页，用于判断是否还有下一页
    instTotal.value = (instQuery.page - 1) * instQuery.size + result.length + (result.length >= instQuery.size ? 1 : 0)
  } catch (err) {
    instError.value = describeError(err)
  } finally {
    instLoading.value = false
  }
}

async function loadDone() {
  doneLoading.value = true
  doneError.value = ''
  try {
    const result = await collabApi.approvals.doneTasks({
      action: doneQuery.action || undefined,
      page: doneQuery.page,
      size: doneQuery.size,
    })
    doneRows.value = result.records
    doneTotal.value = result.total
  } catch (err) {
    doneError.value = describeError(err)
  } finally {
    doneLoading.value = false
  }
}

async function loadTemplates() {
  templateLoading.value = true
  templateError.value = ''
  try {
    templateRows.value = await collabApi.approvals.templates()
  } catch (err) {
    templateError.value = describeError(err)
  } finally {
    templateLoading.value = false
  }
}

function refreshActive() {
  if (activeTab.value === 'tasks') {
    void loadTasks()
  } else if (activeTab.value === 'done') {
    void loadDone()
  } else {
    void loadInstances()
  }
}

function openAction(mode: 'approve' | 'reject' | 'transfer' | 'addSign', row: CollabApprovalRow) {
  const taskId = rowTaskId(row)
  if (taskId === undefined) {
    ElMessage.warning('缺少 task_id，无法处理该待办')
    return
  }
  actionMode.value = mode
  actionTaskId.value = taskId
  Object.assign(actionForm, { comment: '', assignee_id: '', reason: '', position: '' })
  actionError.value = ''
  actionVisible.value = true
}

async function submitAction() {
  const taskId = actionTaskId.value
  if (taskId === null) return
  let assigneeId = 0
  if (actionMode.value === 'transfer' || actionMode.value === 'addSign') {
    if (!actionForm.assignee_id.trim()) {
      actionError.value = '请填写处理人 ID'
      return
    }
    assigneeId = Number(actionForm.assignee_id.trim())
    if (!Number.isFinite(assigneeId)) {
      actionError.value = '处理人 ID 必须是数字'
      return
    }
  }
  actionSaving.value = true
  actionError.value = ''
  try {
    if (actionMode.value === 'approve') {
      await collabApi.approvals.approve(taskId, actionForm.comment.trim() || undefined)
      ElMessage.success('审批已通过')
    } else if (actionMode.value === 'reject') {
      await collabApi.approvals.reject(taskId, actionForm.comment.trim() || undefined)
      ElMessage.success('审批已驳回')
    } else if (actionMode.value === 'transfer') {
      await collabApi.approvals.transfer(taskId, {
        assignee_id: assigneeId,
        reason: actionForm.reason.trim() || undefined,
      })
      ElMessage.success('审批已转交')
    } else {
      await collabApi.approvals.addSign(taskId, {
        assignee_id: assigneeId,
        position: actionForm.position.trim() || undefined,
      })
      ElMessage.success('已加签')
    }
    actionVisible.value = false
    await loadTasks()
  } catch (err) {
    actionError.value = describeError(err)
  } finally {
    actionSaving.value = false
  }
}

async function openInstance(row: CollabApprovalRow) {
  const id = rowInstanceId(row)
  if (!id) {
    ElMessage.warning('缺少 instance_id，无法查看详情')
    return
  }
  instance.value = null
  instanceVisible.value = true
  instanceLoading.value = true
  try {
    instance.value = await collabApi.approvals.instance(id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  } finally {
    instanceLoading.value = false
  }
}

async function cancelInstance(row: CollabApprovalRow) {
  const id = rowInstanceId(row)
  if (!id) {
    ElMessage.warning('缺少 instance_id，无法撤销')
    return
  }
  try {
    await ElMessageBox.confirm(`确认撤销申请「${formatCell(row.instance_no ?? row.instanceNo ?? id)}」？`, '撤销申请', {
      type: 'warning',
      confirmButtonText: '确认撤销',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await collabApi.approvals.cancel(id)
    ElMessage.success('申请已撤销')
    if (instance.value?.instance_id === id) {
      instance.value = null
      instanceVisible.value = false
    }
    await loadInstances()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openSubmit() {
  Object.assign(submitForm, { template_id: '', approver_id: '', form_data: '{}' })
  idempotencyKey.value = genIdempotencyKey()
  submitResult.value = ''
  submitError.value = ''
  submitVisible.value = true
  if (!templateRows.value.length) void loadTemplates()
}

async function submitApproval() {
  if (!submitForm.template_id) {
    submitError.value = '请选择审批模板'
    return
  }
  let formData: Record<string, unknown> | undefined
  const formText = submitForm.form_data.trim()
  if (formText) {
    try {
      const parsed: unknown = JSON.parse(formText)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        submitError.value = 'form_data 必须是 JSON 对象'
        return
      }
      formData = parsed as Record<string, unknown>
    } catch {
      submitError.value = 'form_data 不是合法的 JSON'
      return
    }
  }
  let approverId: number | undefined
  if (submitForm.approver_id.trim()) {
    approverId = Number(submitForm.approver_id.trim())
    if (!Number.isFinite(approverId)) {
      submitError.value = '审批人 ID 必须是数字'
      return
    }
  }
  submitSaving.value = true
  submitError.value = ''
  try {
    const result = await collabApi.approvals.submit({
      template_id: submitForm.template_id,
      form_data: formData,
      approver_id: approverId,
      idempotency_key: idempotencyKey.value,
    })
    submitResult.value = result.instance_no
    ElMessage.success('审批已提交')
    await loadInstances()
  } catch (err) {
    submitError.value = describeError(err)
  } finally {
    submitSaving.value = false
  }
}

onMounted(() => {
  void loadTasks()
  void loadDone()
  void loadInstances()
  void loadTemplates()
})
</script>

<template>
  <div>
    <PageHeader title="审批" description="处理待办、查看我的申请；表单以 JSON 文本提交。">
      <template #actions>
        <el-button v-perm="'biz:collab:approval:submit'" type="primary" @click="openSubmit">发起审批</el-button>
        <el-button :loading="taskLoading || instLoading || templateLoading" @click="refreshActive">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-alert v-if="templateError" type="error" :closable="false" show-icon :title="templateError" style="margin-bottom: 12px" />

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="待我审批" name="tasks">
        <el-alert v-if="taskError" type="error" :closable="false" show-icon :title="taskError" style="margin-bottom: 12px" />

        <el-table :data="taskRows" v-loading="taskLoading" border stripe>
          <el-table-column
            v-for="col in taskColumns"
            :key="col"
            :label="col"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ formatCell(row[col]) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <template v-if="rowTaskId(row) !== undefined">
                <el-button v-perm="'biz:collab:approval:handle'" link type="success" @click="openAction('approve', row)">
                  通过
                </el-button>
                <el-button v-perm="'biz:collab:approval:handle'" link type="danger" @click="openAction('reject', row)">
                  驳回
                </el-button>
                <el-button v-perm="'biz:collab:approval:handle'" link type="primary" @click="openAction('transfer', row)">
                  转交
                </el-button>
                <el-button v-perm="'biz:collab:approval:handle'" link type="warning" @click="openAction('addSign', row)">
                  加签
                </el-button>
              </template>
              <el-text v-else type="info">缺少 task_id</el-text>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我处理过的（已办）" name="done">
        <el-alert v-if="doneError" type="error" :closable="false" show-icon :title="doneError" style="margin-bottom: 12px" />

        <div class="fl-toolbar">
          <el-select v-model="doneQuery.action" placeholder="全部动作" clearable style="width: 150px" @change="doneQuery.page = 1; loadDone()">
            <el-option label="通过" value="APPROVE" />
            <el-option label="驳回" value="REJECT" />
            <el-option label="转交" value="TRANSFER" />
          </el-select>
          <span class="fl-muted">共 {{ doneTotal }} 条已办</span>
        </div>

        <el-table :data="doneRows" v-loading="doneLoading" border stripe>
          <el-table-column
            v-for="col in doneColumns"
            :key="col"
            :label="col"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ formatCell(row[col]) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openInstance(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="doneQuery.page"
            v-model:page-size="doneQuery.size"
            :total="doneTotal"
            :page-sizes="[10, 20, 50]"
            layout="sizes, prev, pager, next"
            @change="loadDone"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="我的申请" name="instances">
        <el-alert v-if="instError" type="error" :closable="false" show-icon :title="instError" style="margin-bottom: 12px" />

        <el-table :data="instRows" v-loading="instLoading" border stripe>
          <el-table-column
            v-for="col in instColumns"
            :key="col"
            :label="col"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ formatCell(row[col]) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="rowInstanceId(row)">
                <el-button link type="primary" @click="openInstance(row)">详情</el-button>
                <el-button v-perm="'biz:collab:approval:submit'" link type="danger" @click="cancelInstance(row)">
                  撤销
                </el-button>
              </template>
              <el-text v-else type="info">缺少 instance_id</el-text>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="instQuery.page"
            v-model:page-size="instQuery.size"
            :total="instTotal"
            :page-sizes="[10, 20, 50]"
            layout="sizes, prev, pager, next"
            @change="loadInstances"
          />
          <span class="fl-muted">本页 {{ instRows.length }} 条（接口不返回总数）</span>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="actionVisible" :title="actionTitle" width="480px">
      <el-alert v-if="actionError" type="error" :closable="false" show-icon :title="actionError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item v-if="actionMode === 'approve' || actionMode === 'reject'" label="审批意见">
          <el-input v-model="actionForm.comment" type="textarea" :rows="3" maxlength="500" placeholder="选填" />
        </el-form-item>
        <template v-else>
          <el-form-item label="处理人 ID" required>
            <el-input v-model="actionForm.assignee_id" placeholder="数字 ID" />
          </el-form-item>
          <el-form-item v-if="actionMode === 'transfer'" label="转交原因">
            <el-input v-model="actionForm.reason" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
          </el-form-item>
          <el-form-item v-else label="加签位置">
            <el-input v-model="actionForm.position" maxlength="64" placeholder="选填，如 BEFORE / AFTER" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="actionVisible = false">取消</el-button>
        <el-button
          v-perm="'biz:collab:approval:handle'"
          type="primary"
          :loading="actionSaving"
          @click="submitAction"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="instanceVisible" title="审批实例详情" width="820px">
      <div v-loading="instanceLoading">
        <template v-if="instance">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="实例编号">{{ instance.instance_no }}</el-descriptions-item>
            <el-descriptions-item label="实例 ID">{{ instance.instance_id }}</el-descriptions-item>
            <el-descriptions-item label="模板 ID">{{ instance.template_id }}</el-descriptions-item>
            <el-descriptions-item label="发起人">{{ instance.initiator_id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ instance.status }}</el-descriptions-item>
            <el-descriptions-item label="当前节点">{{ instance.current_node || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(instance.created_at) }}</el-descriptions-item>
            <el-descriptions-item label="表单数据">{{ formatCell(instance.form_data) }}</el-descriptions-item>
          </el-descriptions>

          <h3 class="block-title">审批任务</h3>
          <el-table :data="instance.tasks" size="small" border>
            <el-table-column prop="task_id" label="任务 ID" width="120" />
            <el-table-column prop="node_key" label="节点标识" width="130" />
            <el-table-column prop="node_name" label="节点名称" min-width="130" />
            <el-table-column label="处理人" width="90">
              <template #default="{ row }">{{ row.assignee_id ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="处理动作" width="110">
              <template #default="{ row }">{{ row.action || '-' }}</template>
            </el-table-column>
            <el-table-column label="处理时间" width="170">
              <template #default="{ row }">{{ formatTime(row.handled_at) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="instanceVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="submitVisible" title="发起审批" width="560px">
      <el-alert v-if="submitError" type="error" :closable="false" show-icon :title="submitError" style="margin-bottom: 12px" />
      <el-alert
        v-if="submitResult"
        type="success"
        :closable="false"
        show-icon
        :title="`提交成功，实例编号：${submitResult}`"
        style="margin-bottom: 12px"
      />
      <el-form label-width="110px">
        <el-form-item label="审批模板" required>
          <el-select v-model="submitForm.template_id" filterable placeholder="选择模板" style="width: 100%">
            <el-option
              v-for="item in templateRows"
              :key="item.template_id"
              :label="`${item.name}（${item.code}）`"
              :value="item.template_id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="指定审批人">
          <el-input v-model="submitForm.approver_id" placeholder="选填，数字 ID" />
        </el-form-item>
        <el-form-item label="表单数据">
          <el-input v-model="submitForm.form_data" type="textarea" :rows="5" placeholder="JSON 对象，结构由模板 form_schema 约定" />
        </el-form-item>
        <el-form-item label="幂等键">
          <el-input v-model="idempotencyKey" readonly />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">关闭</el-button>
        <el-button
          v-perm="'biz:collab:approval:submit'"
          type="primary"
          :loading="submitSaving"
          @click="submitApproval"
        >
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.block-title {
  margin: 20px 0 10px;
  font-size: 14px;
}
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
</style>
