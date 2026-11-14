<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  collabApi,
  type CollabInvite,
  type CollabMeeting,
  type CollabMeetingDetail,
  type CollabMinutes,
} from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()

const rows = ref<CollabInvite[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ page: 1, size: 10, response: '' })
const responding = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<CollabMeetingDetail | null>(null)
const detailMeetingId = ref('')
const checking = ref(false)

const minutesVisible = ref(false)
const minutesSaving = ref(false)
const minutesError = ref('')
const minutesForm = reactive({ topics: '', conclusions: '', content: '', publish: false })

interface ActionDraft {
  content: string
  assignee_id: string
  due_at: string
}
const actionsVisible = ref(false)
const actionsSaving = ref(false)
const actionsError = ref('')
const actionDrafts = ref<ActionDraft[]>([])

const gapItems = [
  '本页只显示“我受邀的会议”（排除本人组织），并带本人参会响应：待响应/已接受/已拒绝/待定。',
  '接受/拒绝/待定会写回本人在该会议日程中的参会响应，仅本人可改。',
  '会议纪要正文与历史版本在详情中展示；发布与编辑纪要仍需会议管理权限。',
]

const minutesHistory = ref<CollabMinutes[]>([])

const isOrganizer = computed(() => detail.value?.meeting.organizer_id === auth.userId)

function fmt(value?: string) {
  return value ? new Date(value).toLocaleString() : '-'
}

function toLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await collabApi.meetings.invites({
      page: query.page,
      size: query.size,
      response: query.response || undefined,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function respond(row: CollabInvite, response: 'ACCEPTED' | 'DECLINED' | 'TENTATIVE') {
  responding.value = String(row.meeting_id)
  try {
    await collabApi.meetings.respondInvite(row.meeting_id, response)
    ElMessage.success(response === 'ACCEPTED' ? '已接受邀请' : response === 'DECLINED' ? '已拒绝邀请' : '已标记待定')
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    responding.value = ''
  }
}

async function loadDetail() {
  if (!detailMeetingId.value) return
  detailLoading.value = true
  try {
    detail.value = await collabApi.meetings.detail(detailMeetingId.value)
    minutesHistory.value = await collabApi.meetings.minutesHistory(detailMeetingId.value).catch(() => [])
  } catch (err) {
    detail.value = null
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function openDetail(row: CollabInvite | CollabMeeting) {
  detailMeetingId.value = String(row.meeting_id)
  detail.value = null
  minutesHistory.value = []
  detailVisible.value = true
  await loadDetail()
}

async function checkin() {
  if (!detailMeetingId.value) return
  checking.value = true
  try {
    const result = await collabApi.meetings.checkin(detailMeetingId.value, { method: 'MANUAL' })
    ElMessage.success(result.checked_in ? '签到成功' : '已签到')
    await Promise.all([loadDetail(), load()])
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    checking.value = false
  }
}

async function cancelMeeting() {
  const meeting = detail.value?.meeting
  if (!meeting) return
  try {
    await ElMessageBox.confirm(`确认取消会议「${meeting.title}」？取消后不可恢复。`, '取消会议', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await collabApi.meetings.cancel(meeting.meeting_id)
    ElMessage.success('会议已取消')
    detailVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openMinutes() {
  Object.assign(minutesForm, { topics: '', conclusions: '', content: '', publish: false })
  minutesError.value = ''
  minutesVisible.value = true
}

async function submitMinutes() {
  if (!detailMeetingId.value) return
  minutesSaving.value = true
  minutesError.value = ''
  try {
    await collabApi.meetings.saveMinutes(detailMeetingId.value, {
      topics: toLines(minutesForm.topics),
      conclusions: toLines(minutesForm.conclusions),
      content: minutesForm.content.trim() || undefined,
      publish: minutesForm.publish,
    })
    ElMessage.success(minutesForm.publish ? '纪要已发布' : '纪要已保存')
    minutesVisible.value = false
    await loadDetail()
  } catch (err) {
    minutesError.value = describeError(err)
  } finally {
    minutesSaving.value = false
  }
}

function openActions() {
  actionDrafts.value = [{ content: '', assignee_id: '', due_at: '' }]
  actionsError.value = ''
  actionsVisible.value = true
}

function addActionRow() {
  actionDrafts.value.push({ content: '', assignee_id: '', due_at: '' })
}

function removeActionRow(index: number) {
  actionDrafts.value.splice(index, 1)
}

async function submitActions() {
  if (!detailMeetingId.value) return
  const actions: Array<{ content: string; assignee_id?: string; due_at?: string }> = []
  for (const draft of actionDrafts.value) {
    const content = draft.content.trim()
    if (!content) continue
    let assigneeId: string | undefined
    if (draft.assignee_id.trim()) {
      assigneeId = draft.assignee_id.trim()
      if (!/^\d+$/.test(assigneeId)) {
        actionsError.value = '负责人 ID 需为正整数'
        return
      }
    }
    actions.push({
      content,
      assignee_id: assigneeId,
      due_at: draft.due_at ? new Date(draft.due_at).toISOString() : undefined,
    })
  }
  if (!actions.length) {
    actionsError.value = '至少填写一条行动项内容'
    return
  }
  actionsSaving.value = true
  actionsError.value = ''
  try {
    await collabApi.meetings.addActions(detailMeetingId.value, {
      minutes_id: detail.value?.minutes?.minutes_id,
      actions,
    })
    ElMessage.success('行动项已派发')
    actionsVisible.value = false
    await loadDetail()
  } catch (err) {
    actionsError.value = describeError(err)
  } finally {
    actionsSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="会议邀请" description="查看会议列表与详情、签到；组织者可编辑纪要、派发行动项与取消会议。">
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select v-model="query.response" placeholder="全部响应" clearable style="width: 160px" @change="query.page = 1; load()">
          <el-option label="待响应" value="PENDING" />
          <el-option label="已接受" value="ACCEPTED" />
          <el-option label="已拒绝" value="DECLINED" />
          <el-option label="待定" value="TENTATIVE" />
        </el-select>
        <span class="fl-muted">共 {{ total }} 场受邀会议</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe row-key="meeting_id">
        <el-table-column prop="title" label="主题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="organizer_id" label="组织者" width="100">
          <template #default="{ row }">
            {{ row.organizer_id }}
            <el-tag v-if="row.organizer_id === auth.userId" size="small" type="success">我</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="会议室" width="100">
          <template #default="{ row }">{{ row.room_id ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ fmt(row.start_at) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }">{{ fmt(row.end_at) }}</template>
        </el-table-column>
        <el-table-column prop="meeting_mode" label="形式" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CANCELLED' ? 'info' : 'primary'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的响应" width="100">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.response === 'ACCEPTED' ? 'success' : row.response === 'DECLINED' ? 'danger' : 'info'"
            >{{ row.response || 'PENDING' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="success" :loading="responding === String(row.meeting_id)" @click="respond(row, 'ACCEPTED')">接受</el-button>
            <el-button link type="danger" :loading="responding === String(row.meeting_id)" @click="respond(row, 'DECLINED')">拒绝</el-button>
            <el-button link type="warning" :loading="responding === String(row.meeting_id)" @click="respond(row, 'TENTATIVE')">待定</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="load"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="会议详情" width="900px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="会议 ID">{{ detail.meeting.meeting_id }}</el-descriptions-item>
            <el-descriptions-item label="主题">{{ detail.meeting.title }}</el-descriptions-item>
            <el-descriptions-item label="组织者">
              {{ detail.meeting.organizer_id }}
              <el-tag v-if="isOrganizer" size="small" type="success">我组织的</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="会议室">{{ detail.meeting.room_id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ fmt(detail.meeting.start_at) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ fmt(detail.meeting.end_at) }}</el-descriptions-item>
            <el-descriptions-item label="会议形式">{{ detail.meeting.meeting_mode }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.meeting.status }}</el-descriptions-item>
            <el-descriptions-item label="入会码">{{ detail.meeting.join_code || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-head">
            <h3>签到</h3>
            <el-button type="success" size="small" :loading="checking" @click="checkin">签到</el-button>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="已签到人数">{{ detail.checkins?.checked_in ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="签到用户">
              {{ detail.checkins?.user_ids?.length ? detail.checkins?.user_ids?.join('、') : '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-head">
            <h3>会议纪要</h3>
            <el-button
              v-if="isOrganizer"
              v-perm="'biz:collab:meeting:manage'"
              type="primary"
              size="small"
              @click="openMinutes"
            >
              编辑纪要
            </el-button>
          </div>
          <el-descriptions v-if="detail.minutes" :column="3" border size="small">
            <el-descriptions-item label="纪要 ID">{{ detail.minutes.minutes_id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.minutes.status }}</el-descriptions-item>
            <el-descriptions-item label="发布时间">{{ fmt(detail.minutes.published_at) }}</el-descriptions-item>
            <el-descriptions-item label="摘要" :span="3">{{ detail.minutes.summary || '-' }}</el-descriptions-item>
            <el-descriptions-item label="正文" :span="3">
              <pre class="minutes-content">{{ detail.minutes.content || '（无正文）' }}</pre>
            </el-descriptions-item>
          </el-descriptions>
          <el-text v-else type="info">尚未创建纪要</el-text>

          <div v-if="minutesHistory.length" class="section-head"><h3>纪要历史（{{ minutesHistory.length }} 个版本）</h3></div>
          <el-table v-if="minutesHistory.length" :data="minutesHistory" border size="small">
            <el-table-column prop="minutes_id" label="版本 ID" width="120" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ fmt(row.created_at) }}</template>
            </el-table-column>
            <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
          </el-table>

          <div class="section-head">
            <h3>行动项</h3>
            <el-button
              v-if="isOrganizer"
              v-perm="'biz:collab:meeting:manage'"
              type="primary"
              size="small"
              @click="openActions"
            >
              添加行动项
            </el-button>
          </div>
          <el-table :data="detail.actions" border size="small" row-key="action_id">
            <el-table-column prop="action_id" label="行动项 ID" width="120" />
            <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
            <el-table-column label="负责人" width="100">
              <template #default="{ row }">{{ row.assignee_id ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="截止时间" width="170">
              <template #default="{ row }">{{ fmt(row.due_at) }}</template>
            </el-table-column>
            <el-table-column label="待办 ID" width="120">
              <template #default="{ row }">{{ row.todo_id ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
          <el-empty v-if="!detail.actions.length" description="暂无行动项" :image-size="60" />
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="isOrganizer && detail && detail.meeting.status !== 'CANCELLED'"
          v-perm="'biz:collab:meeting:manage'"
          type="danger"
          @click="cancelMeeting"
        >
          取消会议
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="minutesVisible" title="编辑会议纪要" width="560px">
      <el-alert v-if="minutesError" type="error" :closable="false" show-icon :title="minutesError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="议题">
          <el-input v-model="minutesForm.topics" type="textarea" :rows="3" placeholder="每行一个议题" />
        </el-form-item>
        <el-form-item label="结论">
          <el-input v-model="minutesForm.conclusions" type="textarea" :rows="3" placeholder="每行一个结论" />
        </el-form-item>
        <el-form-item label="纪要正文">
          <el-input v-model="minutesForm.content" type="textarea" :rows="5" maxlength="5000" />
        </el-form-item>
        <el-form-item label="立即发布">
          <el-switch v-model="minutesForm.publish" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="minutesVisible = false">取消</el-button>
        <el-button
          v-perm="'biz:collab:meeting:manage'"
          type="primary"
          :loading="minutesSaving"
          @click="submitMinutes"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="actionsVisible" title="添加行动项" width="700px">
      <el-alert v-if="actionsError" type="error" :closable="false" show-icon :title="actionsError" style="margin-bottom: 12px" />
      <div v-for="(draft, index) in actionDrafts" :key="index" class="action-row">
        <el-input v-model="draft.content" placeholder="行动项内容" style="flex: 2" />
        <el-input v-model="draft.assignee_id" placeholder="负责人 ID" style="flex: 1" />
        <el-date-picker
          v-model="draft.due_at"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="截止时间"
          style="flex: 1.4"
        />
        <el-button link type="danger" @click="removeActionRow(index)">删除</el-button>
      </div>
      <el-button link type="primary" style="margin-top: 8px" @click="addActionRow">+ 增加一行</el-button>
      <template #footer>
        <el-button @click="actionsVisible = false">取消</el-button>
        <el-button
          v-perm="'biz:collab:meeting:manage'"
          type="primary"
          :loading="actionsSaving"
          @click="submitActions"
        >
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 10px;
}
.section-head h3 {
  margin: 0;
  font-size: 14px;
}
.action-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.minutes-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}
</style>
