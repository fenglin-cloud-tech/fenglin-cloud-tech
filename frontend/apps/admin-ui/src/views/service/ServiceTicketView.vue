<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<ServiceRecord[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)
const filters = reactive({ status: '', priority: '', keyword: '' })

const detailVisible = ref(false)
const detail = ref<ServiceRecord | null>(null)
const comments = ref<ServiceRecord[]>([])
const flowLogs = ref<ServiceRecord[]>([])
const sla = ref<ServiceRecord | null>(null)
const commentText = ref('')
const internalComment = ref(false)
const assignId = ref('')
const transitionStatus = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({
  title: '',
  description: '',
  ticket_type: 'INCIDENT',
  priority: 'P3',
  source: 'PORTAL',
  impact: 'MEDIUM',
  urgency: 'MEDIUM',
  contact: '',
})

const gapItems = [
  '工单列表返回 PageResult（records/total），前端按真实分页渲染。',
  '“我的工单/服务台工单”接口分别校验 biz:service:self:view 与 biz:service:desk:view，权限不足时后端返回真实 403。',
  '工单详情中的联系人字段后端加密存储（contact_enc），接口只回显脱敏值。',
]

const statusLabels: Record<string, string> = {
  NEW: '新建',
  ASSIGNED: '已分派',
  IN_PROGRESS: '处理中',
  PENDING_USER: '待用户',
  PENDING_VENDOR: '待供应商',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
}

const priorityType: Record<string, string> = { P1: 'danger', P2: 'warning', P3: 'info', P4: 'info' }

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await serviceApi.tickets.list({
      status: filters.status || undefined,
      priority: filters.priority || undefined,
      keyword: filters.keyword || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: ServiceRecord) {
  detailVisible.value = true
  detail.value = row
  comments.value = []
  flowLogs.value = []
  sla.value = null
  const id = Number(row.id)
  try {
    detail.value = await serviceApi.tickets.get(id)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
  const [commentResult, logResult, slaResult] = await Promise.all([
    serviceApi.tickets.comments(id).catch(() => [] as ServiceRecord[]),
    serviceApi.tickets.flowLogs(id).catch(() => [] as ServiceRecord[]),
    serviceApi.tickets.sla(id).catch(() => null),
  ])
  comments.value = commentResult
  flowLogs.value = logResult
  sla.value = slaResult
}

async function submitCreate() {
  if (!form.title.trim()) {
    createError.value = '请填写工单标题'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    const created = await serviceApi.tickets.create({
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      ticket_type: form.ticket_type,
      priority: form.priority,
      source: form.source,
      impact: form.impact,
      urgency: form.urgency,
      contact: form.contact.trim() || undefined,
    })
    ElMessage.success(`工单已创建：${created.ticket_no ?? created.id}`)
    createVisible.value = false
    form.title = ''
    form.description = ''
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function assign() {
  if (!detail.value || !assignId.value) {
    ElMessage.warning('请填写处理人 ID')
    return
  }
  try {
    detail.value = await serviceApi.tickets.assign(Number(detail.value.id), { assignee_id: Number(assignId.value) })
    ElMessage.success('已分派')
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function transition() {
  if (!detail.value || !transitionStatus.value) {
    ElMessage.warning('请选择目标状态')
    return
  }
  try {
    detail.value = await serviceApi.tickets.transition(Number(detail.value.id), transitionStatus.value)
    ElMessage.success('状态已流转')
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function addComment() {
  if (!detail.value || !commentText.value.trim()) return
  try {
    await serviceApi.tickets.addComment(Number(detail.value.id), {
      content: commentText.value.trim(),
      internal: internalComment.value,
      comment_type: 'COMMENT',
    })
    commentText.value = ''
    comments.value = await serviceApi.tickets.comments(Number(detail.value.id))
    ElMessage.success('评论已添加')
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
      title="工单工作台"
      description="工单查询、创建、分派、流转、评论与 SLA；数据来自 /api/service/v1/tickets。"
    >
      <template #actions>
        <el-input v-model="filters.keyword" placeholder="标题关键字" clearable style="width: 180px" @keyup.enter="changePage(1)" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 130px" @change="changePage(1)">
          <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="code" />
        </el-select>
        <el-select v-model="filters.priority" placeholder="全部优先级" clearable style="width: 120px" @change="changePage(1)">
          <el-option v-for="p in ['P1', 'P2', 'P3', 'P4']" :key="p" :label="p" :value="p" />
        </el-select>
        <el-button v-perm="'service:ticket:create'" type="primary" @click="createVisible = true">新建工单</el-button>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe max-height="560" @row-click="openDetail">
        <el-table-column prop="ticket_no" label="工单号" width="150" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="ticket_type" label="类型" width="110" />
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="priorityType[row.priority] ?? 'info'">{{ row.priority ?? '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="assignee_id" label="处理人" width="100" />
        <el-table-column prop="requester_id" label="提单人" width="100" />
        <el-table-column prop="sla_resolve_due_at" label="解决时限" width="170" />
        <el-table-column prop="created_at" label="创建时间" width="170" />
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

    <el-drawer v-model="detailVisible" title="工单详情" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单号">{{ detail.ticket_no }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ detail.priority ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="处理人">{{ detail.assignee_id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="提单人">{{ detail.requester_id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.created_at ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ detail.description ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">SLA</el-divider>
        <el-descriptions v-if="sla" :column="2" border size="small">
          <el-descriptions-item label="响应时限">{{ sla.response_due_at ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="解决时限">{{ sla.resolve_due_at ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="暂停累计(秒)">{{ sla.paused_seconds ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="是否超时">{{ sla.breached ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="无 SLA 数据" :image-size="60" />

        <el-divider content-position="left">操作</el-divider>
        <el-space wrap>
          <el-input v-model="assignId" placeholder="处理人ID" style="width: 130px" />
          <el-button v-perm="'service:ticket:assign'" type="primary" plain @click="assign">分派</el-button>
          <el-select v-model="transitionStatus" placeholder="目标状态" style="width: 140px">
            <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="code" />
          </el-select>
          <el-button v-perm="'service:ticket:handle'" type="primary" plain @click="transition">流转</el-button>
        </el-space>

        <el-divider content-position="left">评论</el-divider>
        <el-input v-model="commentText" type="textarea" :rows="3" placeholder="输入评论" />
        <div class="fl-toolbar" style="margin-top: 8px">
          <el-checkbox v-model="internalComment">内部评论</el-checkbox>
          <el-button v-perm="'service:ticket:handle'" type="primary" @click="addComment">发表评论</el-button>
        </div>
        <el-timeline style="margin-top: 12px">
          <el-timeline-item v-for="item in comments" :key="item.id" :timestamp="item.created_at">
            <strong>#{{ item.author_id }}</strong>
            <el-tag v-if="item.is_internal" size="small" type="warning" style="margin-left: 6px">内部</el-tag>
            <div>{{ item.content }}</div>
          </el-timeline-item>
        </el-timeline>

        <el-divider content-position="left">流转日志</el-divider>
        <el-table :data="flowLogs" size="small" border max-height="220">
          <el-table-column prop="from_status" label="原状态" width="110" />
          <el-table-column prop="to_status" label="新状态" width="110" />
          <el-table-column prop="operator_id" label="操作人" width="90" />
          <el-table-column prop="created_at" label="时间" width="170" />
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="createVisible" title="新建工单" width="560px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.ticket_type" style="width: 100%">
            <el-option label="事件" value="INCIDENT" />
            <el-option label="服务请求" value="REQUEST" />
            <el-option label="问题" value="PROBLEM" />
            <el-option label="变更" value="CHANGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option v-for="p in ['P1', 'P2', 'P3', 'P4']" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="影响范围">
          <el-select v-model="form.impact" style="width: 100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急度">
          <el-select v-model="form.urgency" style="width: 100%">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" placeholder="选填" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
