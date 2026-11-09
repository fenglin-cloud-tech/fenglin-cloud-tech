<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { collabApi, type CollabMeeting, type CollabMeetingDetail } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()

const rows = ref<CollabMeeting[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ page: 1, size: 10 })
const onlyMine = ref(false)

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
  '后端没有“我受邀的会议”查询接口，会议列表是租户全量；无法准确展示邀请对象，页面仅按组织者标注。',
  '没有接受/拒绝邀请接口，参会响应通过日程 attendees 表达。',
  '签到接口可用，签到结果以会议详情 checkins 为准。',
]

const filteredRows = computed(() => {
  if (!onlyMine.value) return rows.value
  return rows.value.filter((row) => row.organizer_id === auth.userId)
})

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
    const result = await collabApi.meetings.list({ page: query.page, size: query.size })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadDetail() {
  if (!detailMeetingId.value) return
  detailLoading.value = true
  try {
    detail.value = await collabApi.meetings.detail(detailMeetingId.value)
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
  const actions: Array<{ content: string; assignee_id?: number; due_at?: string }> = []
  for (const draft of actionDrafts.value) {
    const content = draft.content.trim()
    if (!content) continue
    let assigneeId: number | undefined
    if (draft.assignee_id.trim()) {
      assigneeId = Number(draft.assignee_id.trim())
      if (!Number.isInteger(assigneeId) || assigneeId <= 0) {
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
        <span class="fl-muted">仅看我组织</span>
        <el-switch v-model="onlyMine" />
        <span class="fl-muted">共 {{ total }} 场会议，当前展示 {{ filteredRows.length }} 场</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="filteredRows" v-loading="loading" border stripe row-key="meeting_id">
        <el-table-column prop="title" label="主题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="organizer_id" label="组织者" width="100">
          <template #default="{ row }">
            {{ row.organizer_id }}
            <el-tag v-if="row.organizer_id === auth.userId" size="small" type="success">我</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="会议室" width="100">
          <template #default="{ row }">{{ row.room_id ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ fmt(row.start_at) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">{{ fmt(row.end_at) }}</template>
        </el-table-column>
        <el-table-column prop="meeting_mode" label="形式" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CANCELLED' ? 'info' : 'primary'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="入会码" width="110">
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
          </el-descriptions>
          <el-text v-else type="info">尚未创建纪要</el-text>

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
</style>
