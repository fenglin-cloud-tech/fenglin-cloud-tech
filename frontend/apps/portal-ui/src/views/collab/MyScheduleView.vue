<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { collabApi, type CollabBusySlot, type CollabCalendar, type CollabEvent } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()

const calendars = ref<CollabCalendar[]>([])
const calendarsLoading = ref(false)
const calendarId = ref('')
const range = ref<string[]>(defaultRange())

const events = ref<CollabEvent[]>([])
const loading = ref(false)
const error = ref('')

const busySlots = ref<CollabBusySlot[]>([])
const busyLoading = ref(false)
const busyError = ref('')

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  calendar_id: '',
  title: '',
  start_at: '',
  end_at: '',
  all_day: false,
  location: '',
  attendee_ids: '',
})

const gapItems = [
  '列表由服务端按「本人日历 + 本人参会」收敛，不接受 calendarId 指定他人日历；日历下拉仅用于新建日程时的默认日历。',
  '重复日程 rrule 仅透传保存，前端不展开。',
  '没有日程接受/拒绝/提醒设置接口。',
]

function pad(value: number) {
  return String(value).padStart(2, '0')
}

function toLocalInput(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

function defaultRange(): string[] {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const end = new Date(start.getTime() + 7 * 24 * 60 * 60 * 1000)
  return [toLocalInput(start), toLocalInput(end)]
}

function fmt(value?: string) {
  return value ? new Date(value).toLocaleString() : '-'
}

function parseIdList(text: string): string[] {
  return text
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter((value) => /^\d+$/.test(value))
}

async function loadCalendars() {
  calendarsLoading.value = true
  try {
    calendars.value = await collabApi.calendar.calendars()
    if (!calendarId.value && calendars.value.length) {
      calendarId.value = String(calendars.value[0].calendar_id)
    }
  } catch (err) {
    error.value = describeError(err)
  } finally {
    calendarsLoading.value = false
  }
}

async function loadEvents() {
  const [start, end] = range.value || []
  if (!start || !end) {
    error.value = '请选择时间范围'
    return
  }
  loading.value = true
  error.value = ''
  try {
    events.value = await collabApi.calendar.myEvents({
      startTime: new Date(start).toISOString(),
      endTime: new Date(end).toISOString(),
    })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadBusy() {
  busySlots.value = []
  busyError.value = ''
  const userId = auth.userId
  if (!userId) {
    busyError.value = '当前登录上下文缺少用户 ID，无法查询忙闲'
    return
  }
  const [start, end] = range.value || []
  if (!start || !end) {
    busyError.value = '请选择时间范围'
    return
  }
  busyLoading.value = true
  try {
    const result = await collabApi.calendar.freeBusy({
      user_ids: [userId],
      start_time: new Date(start).toISOString(),
      end_time: new Date(end).toISOString(),
    })
    busySlots.value = result.busy[String(userId)] ?? Object.values(result.busy)[0] ?? []
  } catch (err) {
    busyError.value = describeError(err)
  } finally {
    busyLoading.value = false
  }
}

function refresh() {
  void Promise.all([loadEvents(), loadBusy()])
}

function openCreate() {
  Object.assign(createForm, {
    calendar_id: calendarId.value || calendars.value[0]?.calendar_id || '',
    title: '',
    start_at: '',
    end_at: '',
    all_day: false,
    location: '',
    attendee_ids: '',
  })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.calendar_id) {
    createError.value = '请选择日历'
    return
  }
  if (!createForm.title.trim()) {
    createError.value = '日程标题为必填项'
    return
  }
  if (!createForm.start_at || !createForm.end_at) {
    createError.value = '开始时间与结束时间为必填项'
    return
  }
  const start = new Date(createForm.start_at)
  const end = new Date(createForm.end_at)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    createError.value = '开始/结束时间格式不正确'
    return
  }
  if (end.getTime() <= start.getTime()) {
    createError.value = '结束时间必须晚于开始时间'
    return
  }
  const attendeeIds = parseIdList(createForm.attendee_ids)
  creating.value = true
  createError.value = ''
  try {
    await collabApi.calendar.createEvent({
      calendar_id: createForm.calendar_id,
      title: createForm.title.trim(),
      start_at: start.toISOString(),
      end_at: end.toISOString(),
      all_day: createForm.all_day,
      location: createForm.location.trim() || undefined,
      attendee_ids: attendeeIds.length ? attendeeIds : undefined,
    })
    ElMessage.success('日程已创建')
    createVisible.value = false
    refresh()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

async function cancelEvent(row: CollabEvent) {
  try {
    await ElMessageBox.confirm(`确认取消日程「${row.title}」？取消后不可恢复。`, '取消日程', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  try {
    await collabApi.calendar.cancelEvent(row.event_id)
    ElMessage.success('日程已取消')
    refresh()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(async () => {
  await loadCalendars()
  await Promise.all([loadEvents(), loadBusy()])
})
</script>

<template>
  <div>
    <PageHeader title="我的日程" description="查看本人日历与本人参会的日程（服务端按当前账号收敛），并可创建、取消与查询个人忙闲。">
      <template #actions>
        <el-button v-perm="'biz:collab:calendar:manage'" type="primary" @click="openCreate">新建日程</el-button>
        <el-button :loading="loading" @click="refresh">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select
          v-model="calendarId"
          placeholder="默认日历"
          :loading="calendarsLoading"
          style="width: 220px"
        >
          <el-option
            v-for="cal in calendars"
            :key="cal.calendar_id"
            :label="`${cal.name}（${cal.cal_type}）`"
            :value="cal.calendar_id"
          />
        </el-select>
        <el-date-picker
          v-model="range"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          style="width: 380px"
        />
        <el-button type="primary" plain :loading="loading" @click="refresh">查询</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="events" v-loading="loading" border stripe row-key="event_id">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ fmt(row.start_at) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">{{ fmt(row.end_at) }}</template>
        </el-table-column>
        <el-table-column label="全天" width="80">
          <template #default="{ row }">{{ row.all_day ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CANCELLED' ? 'info' : 'primary'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参会人" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.attendees && row.attendees.length">
              <el-tag v-for="att in row.attendees" :key="att.attendee_id" size="small" style="margin: 2px 4px 2px 0">
                {{ att.attendee_id }}:{{ att.response }}
              </el-tag>
            </template>
            <span v-else class="fl-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="关联会议" width="180">
          <template #default="{ row }">
            <span class="fl-mono">{{ row.meeting_id || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-perm="'biz:collab:calendar:manage'"
              link
              type="danger"
              :disabled="row.status === 'CANCELLED'"
              @click="cancelEvent(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="section-head">
          <strong>我的忙闲</strong>
          <el-button size="small" :loading="busyLoading" @click="loadBusy">刷新忙闲</el-button>
        </div>
      </template>
      <el-alert v-if="busyError" type="warning" :closable="false" show-icon :title="busyError" style="margin-bottom: 12px" />
      <el-table v-if="auth.userId" :data="busySlots" v-loading="busyLoading" border size="small">
        <el-table-column label="开始时间" width="200">
          <template #default="{ row }">{{ fmt(row.start) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="200">
          <template #default="{ row }">{{ fmt(row.end) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" />
      </el-table>
      <el-empty v-if="auth.userId && !busyLoading && !busySlots.length" description="所选范围内无忙碌时段" :image-size="60" />
    </el-card>

    <el-dialog v-model="createVisible" title="新建日程" width="560px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="日历" required>
          <el-select v-model="createForm.calendar_id" placeholder="选择日历" style="width: 100%">
            <el-option
              v-for="cal in calendars"
              :key="cal.calendar_id"
              :label="`${cal.name}（${cal.cal_type}）`"
              :value="cal.calendar_id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
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
        <el-form-item label="全天">
          <el-switch v-model="createForm.all_day" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="createForm.location" maxlength="128" placeholder="选填" />
        </el-form-item>
        <el-form-item label="参会人 ID">
          <el-input v-model="createForm.attendee_ids" placeholder="用户 ID，逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button
          v-perm="'biz:collab:calendar:manage'"
          type="primary"
          :loading="creating"
          @click="submitCreate"
        >
          创建
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
}
</style>
