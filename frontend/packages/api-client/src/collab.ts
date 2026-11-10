import type { PageResult } from '@fenglin/shared'
import { get, post, put, request } from './http'

/** 云协（Collab）接口。字段全部为后端 SNAKE_CASE 原样返回，雪花 ID 以字符串透传。 */
export type CollabId = string

export interface CollabMember {
  member_id: number
  role: string
  mute: boolean
  pinned: boolean
  member_status: string
  join_at?: string
}

export interface CollabConversation {
  conversation_id: CollabId
  conv_type: string
  title: string
  member_count: number
  status: string
  owner_id?: number
  last_message_summary?: string
  last_message_at?: string
  unread_count: number
  pinned: boolean
  mute: boolean
  created_at?: string
}

export interface CollabConversationDetail {
  conversation: CollabConversation
  members: CollabMember[]
}

export interface CollabMessage {
  message_id: number
  conversation_id: CollabId
  sender_id: number
  msg_type: string
  summary?: string
  file_id?: string
  reply_to_id?: number
  seq: number
  revoked: boolean
  sent_at?: string
}

export interface CollabMessagePage {
  records: CollabMessage[]
  next_cursor?: number
}

export interface CollabDoc {
  doc_id: CollabId
  title: string
  doc_type: string
  secret_level: string
  current_version: number
  status: string
  owner_id: number
  my_permission: string
  content?: string
  watermark_enabled: boolean
  share_scope: string
  updated_at?: string
}

export interface CollabDocVersion {
  version_no: number
  version_type: string
  editor_id?: number
  created_at?: string
}

export interface CollabCollaborator {
  id: number
  subject_type: string
  subject_id: number
  permission: string
  granted_by?: number
}

export interface CollabComment {
  comment_id: number
  parent_id?: number
  author_id: number
  content: string
  resolved: boolean
  created_at?: string
}

export interface CollabShareLink {
  link_token: string
  url: string
  expire_at?: string
}

export interface CollabCalendar {
  calendar_id: CollabId
  name: string
  cal_type: string
  owner_id?: number
  color?: string
  visible_scope?: string
}

export interface CollabAttendee {
  attendee_id: number
  response: string
  organizer: boolean
}

export interface CollabEvent {
  event_id: CollabId
  calendar_id: CollabId
  title: string
  start_at: string
  end_at: string
  all_day: boolean
  status: string
  meeting_id?: CollabId
  attendees: CollabAttendee[]
}

export interface CollabBusySlot {
  start: string
  end: string
  status: string
}

export interface CollabFreeBusy {
  busy: Record<string, CollabBusySlot[]>
}

export interface CollabSlotView {
  start: string
  end: string
  available_count: number
  score: number
}

export interface CollabSlotSuggestion {
  slots: CollabSlotView[]
}

export interface CollabMeeting {
  meeting_id: CollabId
  title: string
  organizer_id: number
  room_id?: number
  start_at: string
  end_at: string
  meeting_mode: string
  join_code?: string
  status: string
}

export interface CollabCheckinStats {
  checked_in: number
  user_ids: number[]
}

export interface CollabMinutes {
  minutes_id: CollabId
  status: string
  published_at?: string
}

export interface CollabActionItem {
  action_id: number
  content: string
  assignee_id?: number
  due_at?: string
  todo_id?: number
  status: string
}

export interface CollabMeetingDetail {
  meeting: CollabMeeting
  checkins?: CollabCheckinStats
  minutes?: CollabMinutes
  actions: CollabActionItem[]
}

export interface CollabRoom {
  room_id: string
  name: string
  location?: string
  capacity: number
  equipments: string[]
  bookable: boolean
  status: string
}

export interface CollabPeriod {
  period_id: CollabId
  name: string
  start_date?: string
  end_date?: string
  status: string
}

export interface CollabKr {
  kr_id: CollabId
  title: string
  metric_type: string
  target_value?: number
  current_value?: number
  progress?: number
  confidence?: string
}

export interface CollabObjective {
  objective_id: CollabId
  period_id: CollabId
  owner_id: number
  obj_type: string
  title: string
  visibility: string
  status: string
  key_results: CollabKr[]
}

export interface CollabApprovalTemplate {
  template_id: CollabId
  code: string
  name: string
  category: string
  process_key?: string
  enabled: boolean
}

export interface CollabApprovalTask {
  task_id: number
  instance_id: CollabId
  node_key: string
  node_name: string
  assignee_id?: number
  status: string
  action?: string
  handled_at?: string
}

export interface CollabApprovalInstance {
  instance_id: CollabId
  instance_no: string
  template_id: CollabId
  initiator_id: number
  form_data?: Record<string, unknown>
  status: string
  current_node?: string
  created_at?: string
  tasks: CollabApprovalTask[]
}

/** 管理端会话审计页（后端自定义分页结构）。 */
export interface CollabConversationAudit {
  conv_id: CollabId
  title: string
  conv_type: string
  member_count: number
  msg_count: number
  status: string
  created_at?: string
}

export interface CollabConversationAuditPage {
  records: CollabConversationAudit[]
  total: number
  page: number
  size: number
}

export interface CollabSensitiveWordPolicy {
  policy_id: number
  word_count: number
  action: string
  scope: string
  enabled: boolean
  words: string[]
}

export interface CollabDocPolicy {
  confidential_no_link: boolean
  confidential_no_download: boolean
  watermark_template: string
  max_concurrent_editors: number
}

export interface CollabNotification {
  notification_id: string
  category: string
  title: string
  content: string
  read: boolean
  created_at?: string
}

export interface CollabTodo {
  todo_id: string
  source_type: string
  source_id?: string
  title: string
  due_at?: string
  priority: string
  status: string
}

/** 「我的审批」列表后端返回 Map，字段以运行时为准。 */
export type CollabApprovalRow = Record<string, unknown>

export const collabApi = {
  im: {
    conversations(params: { keyword?: string; page?: number; size?: number }) {
      return get<PageResult<CollabConversation>>('/api/collab/v1/im/conversations', params)
    },
    createConversation(body: { conv_type: string; title: string; member_ids: number[]; announcement?: string }) {
      return post<CollabConversationDetail>('/api/collab/v1/im/conversations', body)
    },
    conversation(convId: CollabId) {
      return get<CollabConversationDetail>(`/api/collab/v1/im/conversations/${convId}`)
    },
    addMembers(convId: CollabId, memberIds: number[]) {
      return post<CollabMember[]>(`/api/collab/v1/im/conversations/${convId}/members`, { member_ids: memberIds })
    },
    removeMember(convId: CollabId, memberId: number) {
      return request<void>(`/api/collab/v1/im/conversations/${convId}/members/${memberId}`, { method: 'DELETE' })
    },
    leave(convId: CollabId) {
      return post<void>(`/api/collab/v1/im/conversations/${convId}/leave`)
    },
    dissolve(convId: CollabId) {
      return post<void>(`/api/collab/v1/im/conversations/${convId}/dissolve`)
    },
    messages(convId: CollabId, params: { cursor?: number; size?: number } = {}) {
      return get<CollabMessagePage>(`/api/collab/v1/im/conversations/${convId}/messages`, params)
    },
    send(convId: CollabId, body: {
      client_msg_id?: string
      msg_type?: string
      content?: string
      reply_to_id?: number
      mention_ids?: number[]
      file_id?: string
    }) {
      return post<CollabMessage>(`/api/collab/v1/im/conversations/${convId}/messages`, body)
    },
    revoke(messageId: number, reason?: string) {
      return post<void>(`/api/collab/v1/im/messages/${messageId}/revoke`, { reason })
    },
    read(messageId: number, conversationId: CollabId) {
      return post<{ conversation_id: CollabId; unread_count: number }>(
        `/api/collab/v1/im/messages/${messageId}/read?conversationId=${encodeURIComponent(String(conversationId))}`,
      )
    },
    react(messageId: number, emoji: string) {
      return post<void>(`/api/collab/v1/im/messages/${messageId}/reactions`, { emoji })
    },
    search(params: {
      conversationId?: CollabId
      senderId?: number
      msgType?: string
      keyword?: string
      startMs?: number
      endMs?: number
      page?: number
      size?: number
    }) {
      return get<PageResult<CollabMessage>>('/api/collab/v1/im/messages/search', params)
    },
  },

  docs: {
    list(params: { keyword?: string; page?: number; size?: number }) {
      return get<PageResult<CollabDoc>>('/api/collab/v1/docs', params)
    },
    create(body: { title: string; doc_type?: string; folder_id?: string; secret_level?: string }) {
      return post<CollabDoc>('/api/collab/v1/docs', body)
    },
    detail(docId: CollabId) {
      return get<CollabDoc>(`/api/collab/v1/docs/${docId}`)
    },
    update(docId: CollabId, body: { title?: string; content?: string; version?: number }) {
      return put<CollabDoc>(`/api/collab/v1/docs/${docId}`, body)
    },
    versions(docId: CollabId, params: { page?: number; size?: number } = {}) {
      return get<PageResult<CollabDocVersion>>(`/api/collab/v1/docs/${docId}/versions`, params)
    },
    createVersion(docId: CollabId, body: { version_type?: string; comment?: string } = {}) {
      return post<{ version_no: number; snapshot_file_id?: string; editor_id?: number }>(
        `/api/collab/v1/docs/${docId}/versions`,
        body,
      )
    },
    rollback(docId: CollabId, versionNo: number) {
      return post<{ current_version: number; rolled_back_from: number }>(
        `/api/collab/v1/docs/${docId}/rollback/${versionNo}`,
      )
    },
    collaborators(docId: CollabId) {
      return get<CollabCollaborator[]>(`/api/collab/v1/docs/${docId}/collaborators`)
    },
    addCollaborator(docId: CollabId, body: { subject_type: string; subject_id: number; permission: string }) {
      return post<CollabCollaborator>(`/api/collab/v1/docs/${docId}/collaborators`, body)
    },
    shareLink(docId: CollabId, body: { expire_days?: number; access_code?: string } = {}) {
      return post<CollabShareLink>(`/api/collab/v1/docs/${docId}/share-link`, body)
    },
    comments(docId: CollabId) {
      return get<CollabComment[]>(`/api/collab/v1/docs/${docId}/comments`)
    },
    createComment(docId: CollabId, body: { content: string; parent_id?: number; mention_ids?: number[] }) {
      return post<CollabComment>(`/api/collab/v1/docs/${docId}/comments`, body)
    },
  },

  calendar: {
    calendars() {
      return get<CollabCalendar[]>('/api/collab/v1/calendars')
    },
    createCalendar(body: { name: string; cal_type: string; color?: string; visible_scope?: string; dept_id?: number }) {
      return post<CollabCalendar>('/api/collab/v1/calendars', body)
    },
    events(params: { startTime: string; endTime: string; calendarId?: CollabId }) {
      return get<CollabEvent[]>('/api/collab/v1/events', params)
    },
    createEvent(body: {
      calendar_id: CollabId
      title: string
      description?: string
      location?: string
      start_at: string
      end_at: string
      all_day?: boolean
      rrule?: string
      reminder_minutes?: number[]
      attendee_ids?: number[]
      room_id?: string
    }) {
      return post<CollabEvent>('/api/collab/v1/events', body)
    },
    event(eventId: CollabId) {
      return get<CollabEvent>(`/api/collab/v1/events/${eventId}`)
    },
    updateEvent(eventId: CollabId, body: {
      title?: string
      description?: string
      location?: string
      start_at?: string
      end_at?: string
      all_day?: boolean
    }) {
      return put<CollabEvent>(`/api/collab/v1/events/${eventId}`, body)
    },
    cancelEvent(eventId: CollabId) {
      return post<void>(`/api/collab/v1/events/${eventId}/cancel`)
    },
    freeBusy(body: { user_ids: number[]; start_time?: string; end_time?: string }) {
      return post<CollabFreeBusy>('/api/collab/v1/events/free-busy', body)
    },
    suggestSlots(body: { user_ids?: number[]; duration_minutes?: number; search_days?: number } = {}) {
      return post<CollabSlotSuggestion>('/api/collab/v1/events/suggest-slots', body)
    },
  },

  meetings: {
    list(params: { page?: number; size?: number } = {}) {
      return get<PageResult<CollabMeeting>>('/api/collab/v1/meetings', params)
    },
    create(body: {
      title: string
      start_at: string
      end_at: string
      meeting_mode?: string
      room_id?: string
      external_allowed?: boolean
      agenda?: string
    }) {
      return post<CollabMeeting>('/api/collab/v1/meetings', body)
    },
    detail(meetingId: CollabId) {
      return get<CollabMeetingDetail>(`/api/collab/v1/meetings/${meetingId}`)
    },
    checkin(meetingId: CollabId, body: { method?: string; code?: string } = {}) {
      return post<{ checked_in: boolean; checkin_at?: string }>(`/api/collab/v1/meetings/${meetingId}/checkin`, body)
    },
    saveMinutes(meetingId: CollabId, body: {
      template_id?: number
      topics?: string[]
      conclusions?: string[]
      content?: string
      publish?: boolean
    }) {
      return post<{ minutes_id: CollabId; status: string }>(`/api/collab/v1/meetings/${meetingId}/minutes`, body)
    },
    addActions(meetingId: CollabId, body: {
      minutes_id?: CollabId
      actions: Array<{ content: string; assignee_id?: number; due_at?: string }>
    }) {
      return post<{ todo_ids: number[] }>(`/api/collab/v1/meetings/${meetingId}/actions`, body)
    },
    cancel(meetingId: CollabId) {
      return post<void>(`/api/collab/v1/meetings/${meetingId}/cancel`)
    },
    rooms(params: { bookable?: boolean } = {}) {
      return get<CollabRoom[]>('/api/collab/v1/meeting-rooms', params)
    },
    availableRooms(params: { startTime: string; endTime: string }) {
      return get<CollabRoom[]>('/api/collab/v1/meeting-rooms/available', params)
    },
    reserve(roomId: CollabId, body: { start_at: string; end_at: string; meeting_id?: CollabId }) {
      return post<{ reservation_id: string; status: string }>(
        `/api/collab/v1/meeting-rooms/${roomId}/reserve`,
        body,
      )
    },
  },

  okr: {
    periods() {
      return get<CollabPeriod[]>('/api/collab/v1/okr/periods')
    },
    closePeriod(periodId: CollabId) {
      return post<void>(`/api/collab/v1/okr/periods/${periodId}/close`)
    },
    objectives(params: { periodId?: CollabId; ownerId?: number } = {}) {
      return get<CollabObjective[]>('/api/collab/v1/okr/objectives', params)
    },
    createObjective(body: {
      period_id: CollabId
      obj_type: string
      dept_id?: number
      title: string
      description?: string
      visibility?: string
    }) {
      return post<CollabObjective>('/api/collab/v1/okr/objectives', body)
    },
    createKr(objectiveId: CollabId, body: {
      title: string
      metric_type: string
      target_value?: number
      current_value?: number
      confidence?: string
    }) {
      return post<CollabKr>(`/api/collab/v1/okr/objectives/${objectiveId}/key-results`, body)
    },
    align(objectiveId: CollabId, body: { parent_objective_id: CollabId; align_type?: string; weight?: number }) {
      return post<{ alignment_id: number }>(`/api/collab/v1/okr/objectives/${objectiveId}/align`, body)
    },
    progress(krId: CollabId, body: { progress: number; confidence?: string; note?: string }) {
      return post<{ progress: number; confidence?: string; reported_at?: string }>(
        `/api/collab/v1/okr/key-results/${krId}/progress`,
        body,
      )
    },
    alignmentGraph(params: { periodId?: CollabId } = {}) {
      return get<{
        nodes: Array<{ id: string; title: string; obj_type: string; owner_id?: number }>
        edges: Array<{ child_id: string; parent_id: string; align_type: string }>
      }>('/api/collab/v1/okr/alignment-graph', params)
    },
  },

  approvals: {
    templates() {
      return get<CollabApprovalTemplate[]>('/api/collab/v1/approvals/templates')
    },
    submit(body: {
      template_id: CollabId
      form_data?: Record<string, unknown>
      approver_id?: number
      idempotency_key?: string
    }) {
      return post<CollabApprovalInstance>('/api/collab/v1/approvals/instances', body)
    },
    instance(instanceId: CollabId) {
      return get<CollabApprovalInstance>(`/api/collab/v1/approvals/instances/${instanceId}`)
    },
    cancel(instanceId: CollabId) {
      return post<void>(`/api/collab/v1/approvals/instances/${instanceId}/cancel`)
    },
    myInstances(params: { page?: number; size?: number } = {}) {
      return get<CollabApprovalRow[]>('/api/collab/v1/approvals/instances/my', params)
    },
    myTasks() {
      return get<CollabApprovalRow[]>('/api/collab/v1/approvals/tasks/my')
    },
    approve(taskId: string | number, comment?: string) {
      return post<{ task_status: string; instance_status: string }>(
        `/api/collab/v1/approvals/tasks/${taskId}/approve`,
        { comment },
      )
    },
    reject(taskId: string | number, comment?: string) {
      return post<{ task_status: string; instance_status: string }>(
        `/api/collab/v1/approvals/tasks/${taskId}/reject`,
        { comment },
      )
    },
    transfer(taskId: string | number, body: { assignee_id: number; reason?: string }) {
      return post<void>(`/api/collab/v1/approvals/tasks/${taskId}/transfer`, body)
    },
    addSign(taskId: string | number, body: { assignee_id: number; position?: string }) {
      return post<void>(`/api/collab/v1/approvals/tasks/${taskId}/add-sign`, body)
    },
  },

  workbench: {
    todos(params: { status?: string; page?: number; size?: number } = {}) {
      return get<PageResult<CollabTodo>>('/api/collab/v1/workbench/todos', params)
    },
    completeTodo(todoId: string | number) {
      return post<void>(`/api/collab/v1/workbench/todos/${todoId}/done`)
    },
    notifications(params: { unreadOnly?: boolean; page?: number; size?: number } = {}) {
      return get<PageResult<CollabNotification>>('/api/collab/v1/notifications', params)
    },
    readNotification(notificationId: string | number) {
      return post<{ notification_id: string; read: boolean }>(
        `/api/collab/v1/notifications/${notificationId}/read`,
      )
    },
  },

  admin: {
    auditConversations(params: {
      keyword?: string
      startTime?: number
      endTime?: number
      page?: number
      size?: number
    } = {}) {
      return get<CollabConversationAuditPage>('/api/collab/v1/admin/im/audit/conversations', params)
    },
    sensitiveWords() {
      return get<CollabSensitiveWordPolicy>('/api/collab/v1/admin/im/sensitive-words')
    },
    saveSensitiveWords(body: { words: string[]; action: string; scope?: string; enabled?: boolean }) {
      return post<CollabSensitiveWordPolicy>('/api/collab/v1/admin/im/sensitive-words', body)
    },
    docPolicy() {
      return get<CollabDocPolicy>('/api/collab/v1/admin/docs/policy')
    },
    saveDocPolicy(body: {
      confidential_no_link?: boolean
      confidential_no_download?: boolean
      watermark_template?: string
      max_concurrent_editors?: number
    }) {
      return put<CollabDocPolicy>('/api/collab/v1/admin/docs/policy', body)
    },
    createPublicCalendar(body: {
      name: string
      cal_type: string
      color?: string
      visible_scope?: string
      dept_id?: number
    }) {
      return post<CollabCalendar>('/api/collab/v1/admin/calendars', body)
    },
    createMeetingRoom(body: {
      name: string
      location?: string
      capacity?: number
      equipments?: string[]
      dept_id?: number
      bookable?: boolean
    }) {
      return post<CollabRoom>('/api/collab/v1/admin/meeting-rooms', body)
    },
    upsertPeriod(body: {
      name: string
      start_date?: string
      end_date?: string
      score_policy?: string
      status?: string
    }) {
      return put<CollabPeriod>('/api/collab/v1/admin/okr/periods', body)
    },
    createApprovalTemplate(body: {
      code: string
      name: string
      category?: string
      form_schema?: Record<string, unknown>
      process_key?: string
      enabled?: boolean
      dept_scope?: number[]
    }) {
      return post<CollabApprovalTemplate>('/api/collab/v1/admin/approvals/templates', body)
    },
  },
}
