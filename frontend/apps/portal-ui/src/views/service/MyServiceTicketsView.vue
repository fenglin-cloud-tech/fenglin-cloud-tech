<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const tickets = ref<ServiceRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref('')

const drawer = ref(false)
const detail = ref<ServiceRecord | null>(null)
const comments = ref<ServiceRecord[]>([])
const sla = ref<ServiceRecord | null>(null)
const commentText = ref('')
const commentSaving = ref(false)

const createVisible = ref(false)
const createSaving = ref(false)
const createForm = reactive({ title: '', description: '', ticket_type: 'INCIDENT', priority: 'P3' })

const csatVisible = ref(false)
const csatSaving = ref(false)
const csatForm = reactive({ ticket_id: undefined as number | undefined, score: 5, comment: '' })

const gapItems = [
  '自助查询接口 /my/tickets 后端校验 biz:service:self:view（员工自助视角），与后台工单视图 service:ticket:view 分离。',
  '关闭/重开走员工自助接口 /tickets/{id}/self-close、/self-reopen，服务端校验工单提交人本人；处理人视角的完整流转仍在后台。',
  '满意度提交要求 service:csat:submit；无该权限时不显示评分入口。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await serviceApi.tickets.myTickets({ page: page.value, size: size.value })
    tickets.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openTicket(row: ServiceRecord) {
  try {
    detail.value = await serviceApi.tickets.get(row.id)
    comments.value = await serviceApi.tickets.comments(row.id)
    sla.value = await serviceApi.tickets.sla(row.id).catch(() => null)
    commentText.value = ''
    drawer.value = true
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function addComment() {
  if (!detail.value || !commentText.value.trim()) return
  commentSaving.value = true
  try {
    await serviceApi.tickets.addComment(detail.value.id, { content: commentText.value.trim() })
    commentText.value = ''
    comments.value = await serviceApi.tickets.comments(detail.value.id)
    ElMessage.success('评论已提交')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    commentSaving.value = false
  }
}

async function closeTicket(row: ServiceRecord) {
  try {
    await serviceApi.tickets.selfClose(row.id, '提交人确认关闭')
    ElMessage.success('工单已关闭')
    await load()
    if (detail.value && detail.value.id === row.id) {
      detail.value = await serviceApi.tickets.get(row.id)
    }
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function reopenTicket(row: ServiceRecord) {
  try {
    await serviceApi.tickets.selfReopen(row.id, '提交人重新打开')
    ElMessage.success('工单已重开')
    await load()
    if (detail.value && detail.value.id === row.id) {
      detail.value = await serviceApi.tickets.get(row.id)
    }
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function createTicket() {
  if (!createForm.title.trim()) {
    ElMessage.warning('标题必填')
    return
  }
  createSaving.value = true
  try {
    await serviceApi.tickets.create({
      title: createForm.title.trim(),
      description: createForm.description || undefined,
      ticket_type: createForm.ticket_type,
      priority: createForm.priority,
      source: 'PORTAL',
      requester_id: auth.userId ?? null,
    })
    ElMessage.success('工单已提交')
    createVisible.value = false
    createForm.title = ''
    createForm.description = ''
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    createSaving.value = false
  }
}

function openCsat(row: ServiceRecord) {
  csatForm.ticket_id = row.id
  csatForm.score = 5
  csatForm.comment = ''
  csatVisible.value = true
}

async function submitCsat() {
  if (!csatForm.ticket_id) return
  csatSaving.value = true
  try {
    await serviceApi.csat.submit({
      ticket_id: csatForm.ticket_id,
      score: Number(csatForm.score),
      comment: csatForm.comment || undefined,
      channel: 'PORTAL',
    })
    ElMessage.success('感谢评价')
    csatVisible.value = false
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    csatSaving.value = false
  }
}

function canCsat(row: ServiceRecord) {
  return auth.can('service:csat:submit') && ['RESOLVED', 'CLOSED'].includes(String(row.status))
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服服务台"
      title="我的工单"
      description="本人提交的工单、评论与满意度；数据来自 /api/service/v1/my/tickets。"
    >
      <template #actions>
        <el-button v-perm="'service:ticket:create'" type="primary" @click="createVisible = true">提交工单</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="tickets" v-loading="loading" border stripe size="small">
        <el-table-column prop="ticket_no" label="工单号" width="160" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="ticket_type" label="类型" width="110" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="assignee_id" label="处理人" width="100" />
        <el-table-column prop="created_at" label="提交时间" width="170" />
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTicket(row)">详情</el-button>
            <el-button v-if="row.status === 'RESOLVED'" link type="success" @click="closeTicket(row)">关闭</el-button>
            <el-button v-if="['RESOLVED', 'CLOSED'].includes(String(row.status))" link type="warning" @click="reopenTicket(row)">重开</el-button>
            <el-button v-if="canCsat(row)" link type="success" @click="openCsat(row)">评价</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 8px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(next: number) => { page = next; load() }"
      />
    </el-card>

    <el-drawer v-model="drawer" title="工单详情" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单号">{{ detail.ticket_no }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.ticket_type }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ detail.priority }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="sla">
          <el-divider content-position="left">SLA</el-divider>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="响应截止">{{ sla.sla_response_due_at || '-' }}</el-descriptions-item>
            <el-descriptions-item label="解决截止">{{ sla.sla_resolve_due_at || '-' }}</el-descriptions-item>
            <el-descriptions-item label="首次响应">{{ sla.first_response_at || '-' }}</el-descriptions-item>
            <el-descriptions-item label="解决时间">{{ sla.resolved_at || '-' }}</el-descriptions-item>
          </el-descriptions>
        </template>

        <el-divider content-position="left">评论</el-divider>
        <div v-for="item in comments" :key="item.id" class="comment-item">
          <div class="comment-item__head">
            <span>#{{ item.author_id ?? '-' }}</span>
            <span class="fl-muted">{{ item.created_at }}</span>
          </div>
          <div>{{ item.content }}</div>
        </div>
        <el-empty v-if="comments.length === 0" description="暂无评论" :image-size="60" />
        <el-input v-model="commentText" type="textarea" :rows="3" placeholder="补充说明（仅本人可见的公开回复）" />
        <div style="margin-top: 8px; text-align: right">
          <el-button type="primary" :loading="commentSaving" @click="addComment">提交评论</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="createVisible" title="提交工单" width="520px">
      <el-form label-width="90px">
        <el-form-item label="标题"><el-input v-model="createForm.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.ticket_type" style="width: 100%">
            <el-option label="故障 INCIDENT" value="INCIDENT" />
            <el-option label="服务请求 REQUEST" value="REQUEST" />
            <el-option label="咨询 QUESTION" value="QUESTION" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option v-for="p in ['P1', 'P2', 'P3', 'P4']" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="createForm.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="createTicket">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="csatVisible" title="服务评价" width="460px">
      <el-form label-width="80px">
        <el-form-item label="评分">
          <el-rate v-model="csatForm.score" :max="5" show-score />
        </el-form-item>
        <el-form-item label="建议"><el-input v-model="csatForm.comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="csatVisible = false">取消</el-button>
        <el-button type="primary" :loading="csatSaving" @click="submitCsat">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.comment-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--fl-color-border);
  font-size: 13px;
}
.comment-item__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
