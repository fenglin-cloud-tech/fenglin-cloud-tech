<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import {
  collabApi,
  type CollabCalendar,
  type CollabEvent,
  type CollabFreeBusy,
  type CollabRoom,
  type CollabSlotView,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const activeTab = ref<'events' | 'freebusy' | 'rooms'>('events')

const calendars = ref<CollabCalendar[]>([])
const calendarsLoading = ref(false)
const calendarsError = ref('')

const eventRange = ref<string[]>(defaultRange())
const eventCalendarId = ref('')
const events = ref<CollabEvent[]>([])
const eventsLoading = ref(false)
const eventsError = ref('')

const eventVisible = ref(false)
const eventSaving = ref(false)
const eventError = ref('')
const eventForm = reactive({
  calendar_id: '',
  title: '',
  start: '',
  end: '',
  all_day: false,
  location: '',
  attendee_ids: '',
  room_id: '',
})

const userIdsText = ref('')
const fbRange = ref<string[]>(defaultRange())
const fbLoading = ref(false)
const fbError = ref('')
const freeBusy = ref<CollabFreeBusy | null>(null)
const suggestedSlots = ref<CollabSlotView[]>([])
const slotLoading = ref(false)
const slotError = ref('')
const durationMinutes = ref(30)
const searchDays = ref(7)

const rooms = ref<CollabRoom[]>([])
const roomsLoading = ref(false)
const roomsError = ref('')
const availableRange = ref<string[]>(defaultRange())
const availableRooms = ref<CollabRoom[]>([])
const roomsQueried = ref(false)
const availLoading = ref(false)
const availError = ref('')

const reserveVisible = ref(false)
const reserveSaving = ref(false)
const reserveError = ref('')
const reserveRoom = ref<CollabRoom | null>(null)
const reserveForm = reactive({ start_at: '', end_at: '', meeting_id: '' })

const publicVisible = ref(false)
const publicSaving = ref(false)
const publicError = ref('')
const publicForm = reactive({ name: '', cal_type: 'PUBLIC', color: '#1F5EFF', visible_scope: '' })

const busyRows = computed(() => {
  const result: Array<{ key: string; user_id: string; start: string; end: string; status: string }> = []
  const busy = freeBusy.value?.busy
  if (!busy) return result
  Object.entries(busy).forEach(([userId, slots]) => {
    slots.forEach((slot, index) => {
      result.push({
        key: `${userId}-${slot.start}-${index}`,
        user_id: userId,
        start: slot.start,
        end: slot.end,
        status: slot.status,
      })
    })
  })
  return result
})

const slotRows = computed(() =>
  suggestedSlots.value.map((slot, index) => ({ key: `${slot.start}-${slot.end}-${index}`, ...slot })),
)

const roomRows = computed(() => (roomsQueried.value ? availableRooms.value : rooms.value))

function defaultRange(): string[] {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  const end = new Date(start.getTime() + 7 * 24 * 60 * 60 * 1000)
  return [toLocalInput(start), toLocalInput(end)]
}

function parseIdList(text: string): string[] {
  return text
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter((value) => /^\d+$/.test(value))
}

function toLocalInput(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

async function loadCalendars() {
  calendarsLoading.value = true
  calendarsError.value = ''
  try {
    calendars.value = await collabApi.calendar.calendars()
  } catch (err) {
    calendarsError.value = describeError(err)
  } finally {
    calendarsLoading.value = false
  }
}

async function loadEvents() {
  const [start, end] = eventRange.value || []
  if (!start || !end) {
    eventsError.value = '请选择时间范围'
    return
  }
  eventsLoading.value = true
  eventsError.value = ''
  try {
    events.value = await collabApi.calendar.events({
      startTime: new Date(start).toISOString(),
      endTime: new Date(end).toISOString(),
      calendarId: eventCalendarId.value || undefined,
    })
  } catch (err) {
    eventsError.value = describeError(err)
  } finally {
    eventsLoading.value = false
  }
}

function openEventDialog() {
  Object.assign(eventForm, {
    calendar_id: calendars.value[0]?.calendar_id || '',
    title: '',
    start: '',
    end: '',
    all_day: false,
    location: '',
    attendee_ids: '',
    room_id: '',
  })
  eventError.value = ''
  eventVisible.value = true
}

async function submitEvent() {
  if (!eventForm.calendar_id || !eventForm.title || !eventForm.start || !eventForm.end) {
    eventError.value = '日历、标题、开始与结束时间为必填项'
    return
  }
  const start = new Date(eventForm.start)
  const end = new Date(eventForm.end)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    eventError.value = '开始/结束时间格式不正确'
    return
  }
  const roomId = eventForm.room_id.trim() || undefined
  eventSaving.value = true
  eventError.value = ''
  try {
    await collabApi.calendar.createEvent({
      calendar_id: eventForm.calendar_id,
      title: eventForm.title,
      start_at: start.toISOString(),
      end_at: end.toISOString(),
      all_day: eventForm.all_day,
      location: eventForm.location || undefined,
      attendee_ids: parseIdList(eventForm.attendee_ids),
      room_id: roomId,
    })
    ElMessage.success('日程已创建')
    eventVisible.value = false
    await loadEvents()
  } catch (err) {
    eventError.value = describeError(err)
  } finally {
    eventSaving.value = false
  }
}

async function cancelEvent(row: CollabEvent) {
  try {
    await ElMessageBox.confirm(`确认取消日程「${row.title}」？`, '取消日程', { type: 'warning' })
    await collabApi.calendar.cancelEvent(row.event_id)
    ElMessage.success('日程已取消')
    await loadEvents()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

async function queryFreeBusy() {
  const userIds = parseIdList(userIdsText.value)
  if (!userIds.length) {
    fbError.value = '请输入至少一个用户 ID'
    return
  }
  const [start, end] = fbRange.value || []
  if (!start || !end) {
    fbError.value = '请选择时间范围'
    return
  }
  fbLoading.value = true
  fbError.value = ''
  try {
    freeBusy.value = await collabApi.calendar.freeBusy({
      user_ids: userIds,
      start_time: new Date(start).toISOString(),
      end_time: new Date(end).toISOString(),
    })
  } catch (err) {
    fbError.value = describeError(err)
  } finally {
    fbLoading.value = false
  }
}

async function loadSuggestSlots() {
  const userIds = parseIdList(userIdsText.value)
  if (userIdsText.value.trim() && !userIds.length) {
    slotError.value = '用户 ID 需为逗号分隔的正整数'
    return
  }
  slotLoading.value = true
  slotError.value = ''
  try {
    const result = await collabApi.calendar.suggestSlots({
      user_ids: userIds.length ? userIds : undefined,
      duration_minutes: durationMinutes.value,
      search_days: searchDays.value,
    })
    suggestedSlots.value = result.slots
  } catch (err) {
    slotError.value = describeError(err)
  } finally {
    slotLoading.value = false
  }
}

async function loadRooms() {
  roomsLoading.value = true
  roomsError.value = ''
  try {
    rooms.value = await collabApi.meetings.rooms({ bookable: true })
  } catch (err) {
    roomsError.value = describeError(err)
  } finally {
    roomsLoading.value = false
  }
}

async function queryAvailableRooms() {
  const [start, end] = availableRange.value || []
  if (!start || !end) {
    availError.value = '请选择时间范围'
    return
  }
  availLoading.value = true
  availError.value = ''
  try {
    availableRooms.value = await collabApi.meetings.availableRooms({
      startTime: new Date(start).toISOString(),
      endTime: new Date(end).toISOString(),
    })
    roomsQueried.value = true
  } catch (err) {
    availError.value = describeError(err)
  } finally {
    availLoading.value = false
  }
}

function openReserve(row: CollabRoom) {
  reserveRoom.value = row
  const range = availableRange.value || []
  reserveForm.start_at = range[0] || ''
  reserveForm.end_at = range[1] || ''
  reserveForm.meeting_id = ''
  reserveError.value = ''
  reserveVisible.value = true
}

async function submitReserve() {
  if (!reserveRoom.value) return
  if (!reserveForm.start_at || !reserveForm.end_at) {
    reserveError.value = '开始与结束时间为必填项'
    return
  }
  const start = new Date(reserveForm.start_at)
  const end = new Date(reserveForm.end_at)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    reserveError.value = '开始/结束时间格式不正确'
    return
  }
  reserveSaving.value = true
  reserveError.value = ''
  try {
    const result = await collabApi.meetings.reserve(reserveRoom.value.room_id, {
      start_at: start.toISOString(),
      end_at: end.toISOString(),
      meeting_id: reserveForm.meeting_id || undefined,
    })
    ElMessage.success(`会议室预定成功（${result.reservation_id}）`)
    reserveVisible.value = false
    await queryAvailableRooms()
  } catch (err) {
    reserveError.value = describeError(err)
  } finally {
    reserveSaving.value = false
  }
}

function openPublicCalendar() {
  Object.assign(publicForm, { name: '', cal_type: 'PUBLIC', color: '#1F5EFF', visible_scope: '' })
  publicError.value = ''
  publicVisible.value = true
}

async function submitPublicCalendar() {
  if (!publicForm.name) {
    publicError.value = '日历名称为必填项'
    return
  }
  publicSaving.value = true
  publicError.value = ''
  try {
    await collabApi.admin.createPublicCalendar({
      name: publicForm.name,
      cal_type: publicForm.cal_type,
      color: publicForm.color || undefined,
      visible_scope: publicForm.visible_scope || undefined,
    })
    ElMessage.success('公共日历已创建')
    publicVisible.value = false
    await loadCalendars()
  } catch (err) {
    publicError.value = describeError(err)
  } finally {
    publicSaving.value = false
  }
}

function refreshAll() {
  void Promise.allSettled([loadCalendars(), loadEvents(), loadRooms()])
}

onMounted(() => {
  refreshAll()
})
</script>

<template>
  <div>
    <PageHeader
      module="云协 Collab"
      title="日历与会议室"
      description="日程查询与创建、成员忙闲推荐、会议室可用性与预定；公共日历由管理端创建。"
    >
      <template #actions>
        <el-button v-perm="'tenant:collab:calendar:public:manage'" type="primary" @click="openPublicCalendar">创建公共日历</el-button>
        <el-button @click="refreshAll">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '日历接口没有订阅/共享设置接口，rrule 仅透传保存、前端不展开重复日程。',
        '会议室预定与会议创建是两个接口，后端不会自动串联（ReserveRequest 可传 meeting_id）。',
        '会议室列表只支持 bookable 过滤，没有分页、容量或设备检索参数。',
      ]"
    />

    <el-alert v-if="calendarsError" type="error" :closable="false" show-icon :title="calendarsError" style="margin-bottom: 12px" />

    <div class="fl-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="日程" name="events">
          <div class="fl-toolbar">
            <el-select v-model="eventCalendarId" placeholder="全部日历" clearable style="width: 220px" :loading="calendarsLoading">
              <el-option
                v-for="cal in calendars"
                :key="cal.calendar_id"
                :label="`${cal.name}（${cal.cal_type}）`"
                :value="cal.calendar_id"
              />
            </el-select>
            <el-date-picker
              v-model="eventRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 360px"
            />
            <el-button type="primary" plain @click="loadEvents">查询</el-button>
            <el-button v-perm="'biz:collab:calendar:manage'" type="primary" @click="openEventDialog">新建日程</el-button>
          </div>

          <el-alert v-if="eventsError" type="error" :closable="false" show-icon :title="eventsError" style="margin-bottom: 12px" />

          <el-table :data="events" v-loading="eventsLoading" border stripe row-key="event_id">
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="calendar_id" label="日历 ID" min-width="170" show-overflow-tooltip />
            <el-table-column label="开始时间" width="180">
              <template #default="{ row }">{{ new Date(row.start_at).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="结束时间" width="180">
              <template #default="{ row }">{{ new Date(row.end_at).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="全天" width="80">
              <template #default="{ row }">
                <el-tag :type="row.all_day ? 'success' : 'info'" size="small">{{ row.all_day ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="参与人" min-width="180">
              <template #default="{ row }">
                <el-tag v-for="att in row.attendees || []" :key="att.attendee_id" size="small" effect="plain" style="margin: 2px">
                  {{ att.attendee_id }}:{{ att.response }}
                </el-tag>
                <span v-if="!row.attendees?.length" class="fl-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'biz:collab:calendar:manage'" link type="danger" @click="cancelEvent(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="忙闲与推荐" name="freebusy">
          <div class="fl-toolbar">
            <el-input v-model="userIdsText" placeholder="用户 ID，逗号分隔" clearable style="width: 240px" />
            <el-date-picker
              v-model="fbRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 360px"
            />
            <el-button type="primary" plain :loading="fbLoading" @click="queryFreeBusy">查询忙闲</el-button>
          </div>

          <el-alert v-if="fbError" type="error" :closable="false" show-icon :title="fbError" style="margin-bottom: 12px" />

          <el-table :data="busyRows" v-loading="fbLoading" border size="small" row-key="key" style="margin-bottom: 20px">
            <el-table-column prop="user_id" label="用户 ID" width="140" />
            <el-table-column label="开始" min-width="180">
              <template #default="{ row }">{{ new Date(row.start).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="结束" min-width="180">
              <template #default="{ row }">{{ new Date(row.end).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120" />
          </el-table>

          <div class="fl-toolbar">
            <el-input-number v-model="durationMinutes" :min="15" :step="15" />
            <span class="fl-muted">会议时长（分钟）</span>
            <el-input-number v-model="searchDays" :min="1" :max="30" />
            <span class="fl-muted">搜索天数</span>
            <el-button type="primary" :loading="slotLoading" @click="loadSuggestSlots">推荐时段</el-button>
          </div>

          <el-alert v-if="slotError" type="error" :closable="false" show-icon :title="slotError" style="margin-bottom: 12px" />

          <el-table :data="slotRows" v-loading="slotLoading" border size="small" row-key="key">
            <el-table-column label="开始" min-width="180">
              <template #default="{ row }">{{ new Date(row.start).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="结束" min-width="180">
              <template #default="{ row }">{{ new Date(row.end).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column prop="available_count" label="可用人数" width="110" />
            <el-table-column prop="score" label="评分" width="100" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="会议室" name="rooms">
          <div class="fl-toolbar">
            <el-date-picker
              v-model="availableRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 360px"
            />
            <el-button type="primary" plain :loading="availLoading" @click="queryAvailableRooms">查询可用会议室</el-button>
            <el-button :loading="roomsLoading" @click="loadRooms">刷新会议室</el-button>
          </div>

          <el-alert v-if="roomsError" type="error" :closable="false" show-icon :title="roomsError" style="margin-bottom: 12px" />
          <el-alert v-if="availError" type="error" :closable="false" show-icon :title="availError" style="margin-bottom: 12px" />

          <el-table
            :data="roomRows"
            v-loading="roomsLoading || availLoading"
            border
            stripe
            row-key="room_id"
            :empty-text="roomsQueried ? '该时段无可用会议室' : '暂无可预定会议室'"
          >
            <el-table-column prop="room_id" label="会议室 ID" width="110" />
            <el-table-column prop="name" label="名称" min-width="150" />
            <el-table-column prop="location" label="位置" min-width="150">
              <template #default="{ row }">{{ row.location || '-' }}</template>
            </el-table-column>
            <el-table-column prop="capacity" label="容量" width="90" />
            <el-table-column label="设备" min-width="180">
              <template #default="{ row }">
                <el-tag v-for="item in row.equipments || []" :key="item" size="small" effect="plain" style="margin: 2px">{{ item }}</el-tag>
                <span v-if="!row.equipments?.length" class="fl-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="可预定" width="90">
              <template #default="{ row }">
                <el-tag :type="row.bookable ? 'success' : 'info'" size="small">{{ row.bookable ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-perm="'biz:collab:meeting:manage'" link type="primary" @click="openReserve(row)">预定</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="eventVisible" title="新建日程" width="560px">
      <el-alert v-if="eventError" type="error" :closable="false" show-icon :title="eventError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="日历" required>
          <el-select v-model="eventForm.calendar_id" style="width: 100%" placeholder="选择日历">
            <el-option
              v-for="cal in calendars"
              :key="cal.calendar_id"
              :label="`${cal.name}（${cal.cal_type}）`"
              :value="cal.calendar_id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="eventForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="eventForm.start" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="eventForm.end" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="全天">
          <el-switch v-model="eventForm.all_day" />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="eventForm.location" maxlength="128" />
        </el-form-item>
        <el-form-item label="参与人 ID">
          <el-input v-model="eventForm.attendee_ids" placeholder="逗号分隔的数字 ID" />
        </el-form-item>
        <el-form-item label="会议室 ID">
          <el-input v-model="eventForm.room_id" placeholder="可选，正整数" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="eventVisible = false">取消</el-button>
        <el-button type="primary" :loading="eventSaving" @click="submitEvent">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reserveVisible" title="预定会议室" width="520px">
      <el-alert v-if="reserveError" type="error" :closable="false" show-icon :title="reserveError" style="margin-bottom: 12px" />
      <div v-if="reserveRoom" class="fl-muted" style="margin-bottom: 12px">
        会议室：{{ reserveRoom.name }}（ID {{ reserveRoom.room_id }}）
      </div>
      <el-form label-width="100px">
        <el-form-item label="开始时间" required>
          <el-date-picker v-model="reserveForm.start_at" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker v-model="reserveForm.end_at" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="会议 ID">
          <el-input v-model="reserveForm.meeting_id" placeholder="可选，关联已有会议" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reserveVisible = false">取消</el-button>
        <el-button type="primary" :loading="reserveSaving" @click="submitReserve">确认预定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="publicVisible" title="创建公共日历" width="520px">
      <el-alert v-if="publicError" type="error" :closable="false" show-icon :title="publicError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="publicForm.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="publicForm.cal_type" style="width: 100%">
            <el-option label="PUBLIC" value="PUBLIC" />
            <el-option label="TEAM" value="TEAM" />
          </el-select>
        </el-form-item>
        <el-form-item label="颜色">
          <el-input v-model="publicForm.color" placeholder="#1F5EFF" />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-input v-model="publicForm.visible_scope" placeholder="可选，字符串透传" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publicVisible = false">取消</el-button>
        <el-button type="primary" :loading="publicSaving" @click="submitPublicCalendar">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
