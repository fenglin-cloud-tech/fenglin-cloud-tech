<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ApprovalFormFields, GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  collabApi,
  type CollabApprovalInstance,
  type CollabApprovalRow,
  type CollabApprovalTemplate,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const activeTab = ref('tasks')

const gapItems = [
  '我的待办/我的申请后端返回 Map，字段未强类型化，页面按键动态渲染。',
  '没有已办/抄送列表接口。',
]

// 后端返回 Map 且键为驼峰/下划线混用，这里统一映射为中文列名
const COLUMN_LABELS: Record<string, string> = {
  taskId: '任务 ID', task_id: '任务 ID',
  instanceId: '实例 ID', instance_id: '实例 ID',
  instanceNo: '实例编号', instance_no: '实例编号',
  nodeKey: '节点标识', node_key: '节点标识',
  nodeName: '节点名称', node_name: '节点名称',
  assigneeId: '处理人', assignee_id: '处理人',
  instanceStatus: '实例状态', instance_status: '实例状态',
  status: '状态',
  createdAt: '创建时间', created_at: '创建时间',
  initiatorId: '发起人', initiator_id: '发起人',
  templateId: '模板 ID', template_id: '模板 ID',
}

function columnLabel(col: string): string {
  return COLUMN_LABELS[col] ?? col
}

function formatCell(value: unknown, key?: string): string {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  // 时间类字段后端返回毫秒时间戳，直接展示数字不可读
  if (key && /(?:^|_)(?:created|updated|handled)(?:_at|At)$/i.test(key) && /^\d{13}$/.test(String(value))) {
    return new Date(Number(value)).toLocaleString()
  }
  return String(value)
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
const instanceId = ref('')

const templateVisible = ref(false)
const templateSaving = ref(false)
const templateFormError = ref('')
const templateForm = reactive({
  code: '',
  name: '',
  category: '',
  process_key: '',
  enabled: true,
  form_schema: '{}',
  dept_scope: '',
})

const templateEditingId = ref('')
const templateDialogTitle = computed(() => (templateEditingId.value ? '编辑审批模板' : '新建审批模板'))

const submitVisible = ref(false)
const submitSaving = ref(false)
const submitError = ref('')
const submitResult = ref('')
const submitForm = reactive({ template_id: '', approver_id: '', form_data: '{}' })
const idempotencyKey = ref('')
const submitValues = ref<Record<string, unknown>>({})
const submitFieldsRef = ref<{ validate: () => string | null } | null>(null)
const submitTemplates = computed(() => templateRows.value.filter((item) => item.enabled))
const selectedTemplate = computed(() =>
  templateRows.value.find((item) => String(item.template_id) === submitForm.template_id),
)
const schemaFields = computed<unknown[]>(() => {
  const fields = selectedTemplate.value?.form_schema?.fields
  return Array.isArray(fields) ? fields : []
})

// 切换模板时清空已填写的动态表单，避免上一模板的键值混入
watch(
  () => submitForm.template_id,
  () => {
    submitValues.value = {}
  },
)

function genIdempotencyKey(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
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
    // 后端返回列表且不返回总数：按当前页是否满页推断是否还有下一页
    instTotal.value = (instQuery.page - 1) * instQuery.size + result.length + (result.length >= instQuery.size ? 1 : 0)
  } catch (err) {
    instError.value = describeError(err)
  } finally {
    instLoading.value = false
  }
}

async function loadTemplates() {
  templateLoading.value = true
  templateError.value = ''
  try {
    // 管理端列表含停用模板；无 manage 权限时回退到发起侧可见的启用模板
    templateRows.value = await collabApi.admin.listApprovalTemplates()
  } catch {
    try {
      templateRows.value = await collabApi.approvals.templates()
    } catch (err) {
      templateRows.value = []
      templateError.value = describeError(err)
    }
  } finally {
    templateLoading.value = false
  }
}

function refreshActive() {
  if (activeTab.value === 'tasks') {
    void loadTasks()
  } else if (activeTab.value === 'instances') {
    void loadInstances()
  } else {
    void loadTemplates()
  }
}

function openAction(mode: 'approve' | 'reject' | 'transfer' | 'addSign', row: CollabApprovalRow) {
  const taskId = rowTaskId(row)
  if (taskId === undefined) {
    ElMessage.warning('缺少 task_id，无法处理')
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
  let assigneeId: string | undefined
  if (actionMode.value === 'transfer' || actionMode.value === 'addSign') {
    if (!actionForm.assignee_id.trim()) {
      actionError.value = '请填写处理人 ID'
      return
    }
    assigneeId = actionForm.assignee_id.trim()
    if (!/^\d+$/.test(assigneeId)) {
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
        assignee_id: assigneeId ?? '',
        reason: actionForm.reason.trim() || undefined,
      })
      ElMessage.success('审批已转交')
    } else {
      await collabApi.approvals.addSign(taskId, {
        assignee_id: assigneeId ?? '',
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
  instanceId.value = id
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
    await ElMessageBox.confirm(`确认撤销审批实例「${formatCell(row.instance_no ?? id)}」？`, '撤销申请', {
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
    instanceVisible.value = false
    await loadInstances()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openTemplate(row?: CollabApprovalTemplate) {
  templateEditingId.value = row ? String(row.template_id) : ''
  Object.assign(templateForm, {
    code: row?.code ?? '',
    name: row?.name ?? '',
    category: row?.category ?? '',
    process_key: row?.process_key ?? '',
    enabled: row ? row.enabled : true,
    form_schema: row?.form_schema ? JSON.stringify(row.form_schema, null, 2) : '{}',
    dept_scope: '',
  })
  templateFormError.value = ''
  templateVisible.value = true
}

async function toggleTemplateEnabled(row: CollabApprovalTemplate) {
  try {
    await collabApi.admin.setApprovalTemplateEnabled(row.template_id, !row.enabled)
    ElMessage.success(row.enabled ? '模板已停用' : '模板已启用')
    await loadTemplates()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function removeTemplate(row: CollabApprovalTemplate) {
  try {
    await ElMessageBox.confirm(`确认删除审批模板「${row.name}」？删除后不能再用于发起审批。`, '删除模板', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await collabApi.admin.deleteApprovalTemplate(row.template_id)
    ElMessage.success('模板已删除')
    await loadTemplates()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitTemplate() {
  if (!templateForm.name.trim()) {
    templateFormError.value = '模板名称为必填项'
    return
  }
  if (!templateEditingId.value && !templateForm.code.trim()) {
    templateFormError.value = '模板编码为必填项'
    return
  }
  let formSchema: Record<string, unknown> | undefined
  const schemaText = templateForm.form_schema.trim()
  if (schemaText) {
    try {
      const parsed: unknown = JSON.parse(schemaText)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        templateFormError.value = 'form_schema 必须是 JSON 对象'
        return
      }
      formSchema = parsed as Record<string, unknown>
    } catch {
      templateFormError.value = 'form_schema 不是合法的 JSON'
      return
    }
  }
  let deptScope: number[] | undefined
  const scopeText = templateForm.dept_scope.trim()
  if (scopeText) {
    deptScope = scopeText
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .map(Number)
    if (deptScope.some((item) => !Number.isFinite(item))) {
      templateFormError.value = 'dept_scope 必须是逗号分隔的数字'
      return
    }
  }
  templateSaving.value = true
  templateFormError.value = ''
  try {
    const payload = {
      name: templateForm.name.trim(),
      category: templateForm.category.trim() || undefined,
      form_schema: formSchema,
      process_key: templateForm.process_key.trim() || undefined,
      enabled: templateForm.enabled,
      dept_scope: deptScope,
    }
    if (templateEditingId.value) {
      await collabApi.admin.updateApprovalTemplate(templateEditingId.value, payload)
      ElMessage.success('模板已更新')
    } else {
      await collabApi.admin.createApprovalTemplate({ ...payload, code: templateForm.code.trim() })
      ElMessage.success('模板已创建')
    }
    templateVisible.value = false
    await loadTemplates()
  } catch (err) {
    templateFormError.value = describeError(err)
  } finally {
    templateSaving.value = false
  }
}

function openSubmit() {
  Object.assign(submitForm, { template_id: '', approver_id: '', form_data: '{}' })
  submitValues.value = {}
  submitFieldsRef.value = null
  idempotencyKey.value = genIdempotencyKey()
  submitResult.value = ''
  submitError.value = ''
  submitVisible.value = true
}

async function submitApproval() {
  if (!submitForm.template_id) {
    submitError.value = '请选择审批模板'
    return
  }
  let formData: Record<string, unknown> | undefined
  if (schemaFields.value.length > 0) {
    const invalid = submitFieldsRef.value?.validate()
    if (invalid) {
      submitError.value = invalid
      return
    }
    formData = { ...submitValues.value }
  } else {
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
  }
  let approverId: string | undefined
  if (submitForm.approver_id.trim()) {
    approverId = submitForm.approver_id.trim()
    if (!/^\d+$/.test(approverId)) {
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
  void loadInstances()
  void loadTemplates()
})
</script>

<template>
  <div>
    <PageHeader module="云协 Collab" title="审批中心" description="待办处理、我的申请与审批模板；发起审批按模板 form_schema 渲染动态表单。">
      <template #actions>
        <el-button v-perm="'biz:collab:approval:submit'" type="primary" @click="openSubmit">发起审批</el-button>
        <el-button :loading="taskLoading || instLoading || templateLoading" @click="refreshActive">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="我的待办" name="tasks">
        <el-alert v-if="taskError" type="error" :closable="false" show-icon :title="taskError" style="margin-bottom: 12px" />

        <el-table :data="taskRows" v-loading="taskLoading" border stripe>
          <el-table-column
            v-for="col in taskColumns"
            :key="col"
            :label="columnLabel(col)"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ formatCell(row[col], col) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <template v-if="rowTaskId(row) !== undefined">
                <el-button v-perm="'biz:collab:approval:handle'" link type="success" @click="openAction('approve', row)">通过</el-button>
                <el-button v-perm="'biz:collab:approval:handle'" link type="danger" @click="openAction('reject', row)">驳回</el-button>
                <el-button v-perm="'biz:collab:approval:handle'" link type="primary" @click="openAction('transfer', row)">转交</el-button>
                <el-button v-perm="'biz:collab:approval:handle'" link type="warning" @click="openAction('addSign', row)">加签</el-button>
              </template>
              <el-text v-else type="info">缺少 task_id，无法处理</el-text>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我的申请" name="instances">
        <el-alert v-if="instError" type="error" :closable="false" show-icon :title="instError" style="margin-bottom: 12px" />

        <el-table :data="instRows" v-loading="instLoading" border stripe>
          <el-table-column
            v-for="col in instColumns"
            :key="col"
            :label="columnLabel(col)"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">{{ formatCell(row[col], col) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <template v-if="rowInstanceId(row)">
                <el-button link type="primary" @click="openInstance(row)">详情</el-button>
                <el-button v-perm="'biz:collab:approval:submit'" link type="danger" @click="cancelInstance(row)">撤销</el-button>
              </template>
              <el-text v-else type="info">缺少 instance_id</el-text>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="instQuery.page"
          v-model:page-size="instQuery.size"
          :total="instTotal"
          layout="prev, pager, next"
          style="margin-top: 16px; justify-content: flex-end"
          @change="loadInstances"
        />
      </el-tab-pane>

      <el-tab-pane label="模板" name="templates">
        <div class="fl-toolbar">
          <el-button
            v-perm="'tenant:collab:approval:template:manage'"
            type="primary"
            @click="openTemplate"
          >
            新建模板
          </el-button>
        </div>

        <el-alert v-if="templateError" type="error" :closable="false" show-icon :title="templateError" style="margin-bottom: 12px" />

        <el-table :data="templateRows" v-loading="templateLoading" border stripe>
          <el-table-column prop="template_id" label="模板 ID" width="190" show-overflow-tooltip />
          <el-table-column prop="code" label="编码" width="150" />
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="category" label="分类" width="120" />
          <el-table-column prop="process_key" label="流程标识" width="150">
            <template #default="{ row }">{{ row.process_key || '-' }}</template>
          </el-table-column>
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button v-perm="'tenant:collab:approval:template:manage'" link type="primary" @click="openTemplate(row)">编辑</el-button>
              <el-button
                v-perm="'tenant:collab:approval:template:manage'"
                link
                :type="row.enabled ? 'warning' : 'success'"
                @click="toggleTemplateEnabled(row)"
              >
                {{ row.enabled ? '停用' : '启用' }}
              </el-button>
              <el-button v-perm="'tenant:collab:approval:template:manage'" link type="danger" @click="removeTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
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
            <el-descriptions-item label="创建时间">
              {{ instance.created_at ? new Date(instance.created_at).toLocaleString() : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="表单数据">{{ formatCell(instance.form_data) }}</el-descriptions-item>
          </el-descriptions>

          <h3 class="block-title">审批任务</h3>
          <el-table :data="instance.tasks" size="small" border>
            <el-table-column prop="task_id" label="任务 ID" width="120" />
            <el-table-column prop="node_key" label="节点标识" width="130" />
            <el-table-column prop="node_name" label="节点名称" min-width="130" />
            <el-table-column prop="assignee_id" label="处理人" width="90">
              <template #default="{ row }">{{ row.assignee_id ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="action" label="处理动作" width="110">
              <template #default="{ row }">{{ row.action || '-' }}</template>
            </el-table-column>
            <el-table-column label="处理时间" width="170">
              <template #default="{ row }">{{ row.handled_at ? new Date(row.handled_at).toLocaleString() : '-' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="instanceVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateVisible" :title="templateDialogTitle" width="560px">
      <el-alert v-if="templateFormError" type="error" :closable="false" show-icon :title="templateFormError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="模板编码" :required="!templateEditingId">
          <el-input v-model="templateForm.code" maxlength="64" :disabled="!!templateEditingId" />
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="templateForm.category" maxlength="64" />
        </el-form-item>
        <el-form-item label="流程标识">
          <el-input v-model="templateForm.process_key" maxlength="128" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="templateForm.enabled" />
        </el-form-item>
        <el-form-item label="表单 Schema">
          <el-input
            v-model="templateForm.form_schema"
            type="textarea"
            :rows="6"
            placeholder='{"fields":[{"key":"amount","label":"金额","type":"number","required":true},{"key":"reason","label":"事由","type":"textarea","required":true}]}'
          />
          <div class="schema-hint">字段类型支持 text / textarea / number / date / select（options 为选项数组）</div>
        </el-form-item>
        <el-form-item label="部门范围">
          <el-input v-model="templateForm.dept_scope" placeholder="逗号分隔的数字，如 1,2,3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateVisible = false">取消</el-button>
        <el-button
          v-perm="'tenant:collab:approval:template:manage'"
          type="primary"
          :loading="templateSaving"
          @click="submitTemplate"
        >
          提交
        </el-button>
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
              v-for="item in submitTemplates"
              :key="item.template_id"
              :label="`${item.name}（${item.code}）`"
              :value="item.template_id"
            />
          </el-select>
          <div v-if="!submitTemplates.length" class="schema-hint">当前没有启用的审批模板，请先在「模板」页启用</div>
        </el-form-item>
        <el-form-item label="指定审批人">
          <el-input v-model="submitForm.approver_id" placeholder="选填，数字 ID" />
        </el-form-item>
        <ApprovalFormFields
          v-if="schemaFields.length > 0"
          ref="submitFieldsRef"
          v-model="submitValues"
          :schema="selectedTemplate?.form_schema"
        />
        <el-form-item v-else label="表单数据">
          <el-input v-model="submitForm.form_data" type="textarea" :rows="5" placeholder="该模板未定义 form_schema.fields，按 JSON 对象提交" />
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

.schema-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
