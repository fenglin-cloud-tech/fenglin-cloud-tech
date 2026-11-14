<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH } from '@fenglin/ui-admin'
import { collabApi, type CollabMeeting, type CollabMeetingDetail } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<CollabMeeting[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ page: 1, size: 10, status: '', range: [] as string[] })

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  title: '',
  start_at: '',
  end_at: '',
  meeting_mode: 'ONLINE',
  room_id: '',
  external_allowed: false,
  agenda: '',
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<CollabMeetingDetail | null>(null)
const detailMeetingId = ref<string | number>('')
const minutesHistory = ref<{ minutes_id: string | number; status: string; created_at?: string; summary?: string }[]>([])

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

const modeOptions = [
  { value: 'ONLINE', label: '线上会议' },
  { value: 'OFFLINE', label: '线下会议' },
  { value: 'HYBRID', label: '线上线下结合' },
]

const gapItems = [
  '会议详情已返回纪要摘要与正文；纪要历史按版本另列（GET /meetings/{id}/minutes）。',
  '会议邀请的接受/拒绝/待定在员工端“会议邀请”页处理，写回本人参会响应。',
  '会议列表已支持状态与时间范围过滤；“我受邀的会议”走 /meetings/invites。',
  '会议取消仍不单独留状态流水，取消动作只记审计；如需完整状态轨迹需新增历史表。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await collabApi.meetings.list({
      page: query.page,
      size: query.size,
      status: query.status || undefined,
      from: query.range && query.range[0] ? new Date(query.range[0]).toISOString() : undefined,
      to: query.range && query.range[1] ? new Date(query.range[1]).toISOString() : undefined,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, {
    title: '',
    start_at: '',
    end_at: '',
    meeting_mode: 'ONLINE',
    room_id: '',
    external_allowed: false,
    agenda: '',
  })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.title.trim()) {
    createError.value = '会议主题为必填项'
    return
  }
  if (!createForm.start_at || !createForm.end_at) {
    createError.value = '开始时间与结束时间为必填项'
    return
  }
  const start = new Date(createForm.start_at)
  const end = new Date(createForm.end_at)
  if (end.getTime() <= start.getTime()) {
    createError.value = '结束时间必须晚于开始时间'
    return
  }
  const roomId = createForm.room_id.trim() || undefined
  creating.value = true
  createError.value = ''
  try {
    await collabApi.meetings.create({
      title: createForm.title.trim(),
      start_at: start.toISOString(),
      end_at: end.toISOString(),
      meeting_mode: createForm.meeting_mode,
      room_id: roomId,
      external_allowed: createForm.external_allowed,
      agenda: createForm.agenda.trim() || undefined,
    })
    ElMessage.success('会议已预约')
    createVisible.value = false
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
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

async function openDetail(row: CollabMeeting) {
  detailMeetingId.value = row.meeting_id
  detail.value = null
  detailVisible.value = true
  await loadDetail()
}

function openMinutes() {
  Object.assign(minutesForm, { topics: '', conclusions: '', content: '', publish: false })
  minutesError.value = ''
  minutesVisible.value = true
}

function toLines(text: string): string[] {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
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
        actionsError.value = '负责人 ID 必须是数字'
        return
      }
    }
    const dueAt = draft.due_at ? new Date(draft.due_at).toISOString() : undefined
    actions.push({ content, assignee_id: assigneeId, due_at: dueAt })
  }
  if (actions.length === 0) {
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

async function checkin() {
  if (!detailMeetingId.value) return
  try {
    const result = await collabApi.meetings.checkin(detailMeetingId.value, { method: 'MANUAL' })
    ElMessage.success(result.checked_in ? '签到成功' : '已签到')
    await Promise.all([loadDetail(), load()])
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function cancelMeeting(meetingId: string | number, title: string) {
  try {
    await ElMessageBox.confirm(`确认取消会议「${title}」？取消后不可恢复。`, '取消会议', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await collabApi.meetings.cancel(meetingId)
    ElMessage.success('会议已取消')
    detailVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="云协 Collab" title="会议管理" description="预约会议、查看签到与纪要、派发行动项；取消后不可恢复。">
      <template #actions>
        <el-button v-perm="'biz:collab:meeting:manage'" type="primary" @click="openCreate">预约会议</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="query.page = 1; load()">
          <el-option label="已预约" value="SCHEDULED" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="已结束" value="ENDED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
        <el-date-picker
          v-model="query.range"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          range-separator="至"
          start-placeholder="开始时间起"
          end-placeholder="开始时间止"
          @change="query.page = 1; load()"
        />
        <el-button :loading="loading" @click="load">查询</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="meeting_id" label="会议 ID" width="160" show-overflow-tooltip />
        <el-table-column prop="title" label="主题" min-width="170" show-overflow-tooltip />
        <el-table-column prop="organizer_id" label="组织者" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="room_id" label="会议室" width="80" show-overflow-tooltip>
          <template #default="{ row }">{{ row.room_id ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ row.start_at ? new Date(row.start_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }">{{ row.end_at ? new Date(row.end_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column prop="meeting_mode" label="形式" width="90" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CANCELLED' ? 'info' : 'primary'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="join_code" label="入会码" width="110">
          <template #default="{ row }">{{ row.join_code || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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
    </div>

    <el-dialog v-model="createVisible" title="预约会议" width="580px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="会议主题" required>
          <el-input v-model="createForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="createForm.start_at"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="createForm.end_at"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="会议形式">
          <el-select v-model="createForm.meeting_mode" style="width: 100%">
            <el-option v-for="opt in modeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="会议室 ID">
          <el-input v-model="createForm.room_id" placeholder="选填，线下/混合会议填写" />
        </el-form-item>
        <el-form-item label="允许外部">
          <el-switch v-model="createForm.external_allowed" />
        </el-form-item>
        <el-form-item label="会议议程">
          <el-input v-model="createForm.agenda" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button v-perm="'biz:collab:meeting:manage'" type="primary" :loading="creating" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="会议详情" width="900px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="会议 ID">{{ detail.meeting.meeting_id }}</el-descriptions-item>
            <el-descriptions-item label="主题">{{ detail.meeting.title }}</el-descriptions-item>
            <el-descriptions-item label="组织者">{{ detail.meeting.organizer_id }}</el-descriptions-item>
            <el-descriptions-item label="会议室">{{ detail.meeting.room_id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">
              {{ detail.meeting.start_at ? new Date(detail.meeting.start_at).toLocaleString() : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="结束时间">
              {{ detail.meeting.end_at ? new Date(detail.meeting.end_at).toLocaleString() : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="会议形式">{{ detail.meeting.meeting_mode }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.meeting.status }}</el-descriptions-item>
            <el-descriptions-item label="入会码">{{ detail.meeting.join_code || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-head">
            <h3>签到统计</h3>
            <el-button v-perm="'biz:collab:meeting:join'" size="small" type="success" @click="checkin">签到</el-button>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="已签到人数">{{ detail.checkins?.checked_in ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="签到用户">
              {{ detail.checkins?.user_ids?.length ? detail.checkins?.user_ids?.join('、') : '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-head">
            <h3>会议纪要</h3>
            <el-button v-perm="'biz:collab:meeting:manage'" size="small" type="primary" @click="openMinutes">编辑纪要</el-button>
          </div>
          <el-descriptions v-if="detail.minutes" :column="3" border size="small">
            <el-descriptions-item label="纪要 ID">{{ detail.minutes.minutes_id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.minutes.status }}</el-descriptions-item>
            <el-descriptions-item label="发布时间">
              {{ detail.minutes.published_at ? new Date(detail.minutes.published_at).toLocaleString() : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="摘要" :span="3">{{ detail.minutes.summary || '-' }}</el-descriptions-item>
            <el-descriptions-item label="正文" :span="3">
              <pre style="margin: 0; white-space: pre-wrap; font-family: inherit">{{ detail.minutes.content || '（无正文）' }}</pre>
            </el-descriptions-item>
          </el-descriptions>
          <el-text v-else type="info">尚未创建纪要</el-text>

          <template v-if="minutesHistory.length">
            <div class="section-head"><h3>纪要历史（{{ minutesHistory.length }} 个版本）</h3></div>
            <el-table :data="minutesHistory" size="small" border>
              <el-table-column prop="minutes_id" label="版本 ID" width="120" show-overflow-tooltip />
              <el-table-column prop="status" label="状态" width="100" />
              <el-table-column label="创建时间" width="170">
                <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
              </el-table-column>
              <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
            </el-table>
          </template>

          <div class="section-head">
            <h3>行动项</h3>
            <el-button v-perm="'biz:collab:meeting:manage'" size="small" type="primary" @click="openActions">添加行动项</el-button>
          </div>
          <el-table :data="detail.actions" size="small" border>
            <el-table-column prop="action_id" label="行动项 ID" width="120" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="180" show-overflow-tooltip />
            <el-table-column prop="assignee_id" label="负责人" :width="ID_COLUMN_WIDTH" show-overflow-tooltip>
              <template #default="{ row }">{{ row.assignee_id ?? '-' }}</template>
            </el-table-column>
            <el-table-column label="截止时间" width="170">
              <template #default="{ row }">{{ row.due_at ? new Date(row.due_at).toLocaleString() : '-' }}</template>
            </el-table-column>
            <el-table-column prop="todo_id" label="待办 ID" width="120">
              <template #default="{ row }">{{ row.todo_id ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detail && detail.meeting.status !== 'CANCELLED'"
          v-perm="'biz:collab:meeting:manage'"
          type="danger"
          @click="cancelMeeting(detail.meeting.meeting_id, detail.meeting.title)"
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
        <el-button v-perm="'biz:collab:meeting:manage'" type="primary" :loading="minutesSaving" @click="submitMinutes">保存</el-button>
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
        <el-button v-perm="'biz:collab:meeting:manage'" type="primary" :loading="actionsSaving" @click="submitActions">提交</el-button>
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
</style>
