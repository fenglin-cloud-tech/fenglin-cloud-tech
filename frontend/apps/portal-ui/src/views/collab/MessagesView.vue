<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { collabApi, type CollabConversation, type CollabMessage } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()

const conversations = ref<CollabConversation[]>([])
const convLoading = ref(false)
const error = ref('')
const convQuery = reactive({ keyword: '' })

const selected = ref<CollabConversation | null>(null)
const messages = ref<CollabMessage[]>([])
const msgLoading = ref(false)
const msgLoadingMore = ref(false)
const nextCursor = ref<number | undefined>(undefined)

const sending = ref(false)
const draft = reactive({ msg_type: 'TEXT', content: '' })

const gapItems = [
  '消息正文只返回 summary 字段，富文本/附件详情不在列表接口中，附件仅展示 file_id。',
  '未读数是会话级 unread_count，没有全量未读汇总接口。',
  '撤回是否允许由后端校验，前端仅按 sender_id 与当前账号比较显示入口。',
]

function fmt(value?: string) {
  return value ? new Date(value).toLocaleString() : '-'
}

function sortMessages() {
  messages.value = [...messages.value].sort((a, b) => a.seq - b.seq)
}

async function loadConversations() {
  convLoading.value = true
  error.value = ''
  try {
    const result = await collabApi.im.conversations({
      keyword: convQuery.keyword || undefined,
      page: 1,
      size: 50,
    })
    conversations.value = result.records
  } catch (err) {
    error.value = describeError(err)
  } finally {
    convLoading.value = false
  }
}

async function loadMessages() {
  if (!selected.value) return
  msgLoading.value = true
  error.value = ''
  try {
    const result = await collabApi.im.messages(selected.value.conversation_id, { size: 50 })
    messages.value = result.records
    nextCursor.value = result.next_cursor
    sortMessages()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    msgLoading.value = false
  }
}

async function loadEarlier() {
  if (!selected.value || nextCursor.value === undefined) return
  msgLoadingMore.value = true
  try {
    const result = await collabApi.im.messages(selected.value.conversation_id, {
      cursor: nextCursor.value,
      size: 50,
    })
    const known = new Set(messages.value.map((item) => item.message_id))
    messages.value = [...result.records.filter((item) => !known.has(item.message_id)), ...messages.value]
    nextCursor.value = result.next_cursor
    sortMessages()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    msgLoadingMore.value = false
  }
}

async function markRead() {
  const conv = selected.value
  if (!conv || !messages.value.length) return
  const latest = messages.value[messages.value.length - 1]
  try {
    await collabApi.im.read(latest.message_id, conv.conversation_id)
    conv.unread_count = 0
    const target = conversations.value.find((item) => item.conversation_id === conv.conversation_id)
    if (target) target.unread_count = 0
  } catch {
    // 已读回执失败不阻断消息浏览
  }
}

async function selectConversation(conv: CollabConversation) {
  selected.value = conv
  messages.value = []
  nextCursor.value = undefined
  await loadMessages()
  await markRead()
}

async function sendMessage() {
  if (!selected.value) return
  const content = draft.content.trim()
  if (!content) {
    ElMessage.warning('请输入消息内容')
    return
  }
  sending.value = true
  try {
    await collabApi.im.send(selected.value.conversation_id, {
      client_msg_id: `${Date.now()}-${Math.random()}`,
      msg_type: draft.msg_type,
      content,
    })
    draft.content = ''
    await loadMessages()
    await markRead()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    sending.value = false
  }
}

async function revokeMessage(message: CollabMessage) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入撤回原因（可选）', '撤回消息', {
      confirmButtonText: '撤回',
      cancelButtonText: '取消',
      inputPlaceholder: '撤回原因',
    })
    reason = result.value?.trim() ?? ''
  } catch {
    return
  }
  try {
    await collabApi.im.revoke(message.message_id, reason || undefined)
    const target = messages.value.find((item) => item.message_id === message.message_id)
    if (target) target.revoked = true
    ElMessage.success('消息已撤回')
    await loadMessages()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(loadConversations)
</script>

<template>
  <div>
    <PageHeader module="云协协作" title="消息" description="左侧会话列表，右侧消息流；支持未读清理、发送与撤回。">
      <template #actions>
        <el-button :loading="convLoading" @click="loadConversations">刷新会话</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-row :gutter="12">
      <el-col :xs="24" :md="8" :lg="7">
        <el-card shadow="never" class="pane">
          <div class="fl-toolbar">
            <el-input
              v-model="convQuery.keyword"
              placeholder="会话标题关键词"
              clearable
              style="flex: 1"
              @keyup.enter="loadConversations"
            />
            <el-button type="primary" plain @click="loadConversations">查询</el-button>
          </div>

          <div v-loading="convLoading" class="conv-list">
            <el-empty v-if="!conversations.length" description="暂无会话" :image-size="60" />
            <div
              v-for="conv in conversations"
              :key="conv.conversation_id"
              class="conv-item"
              :class="{ 'conv-item--active': selected?.conversation_id === conv.conversation_id }"
              @click="selectConversation(conv)"
            >
              <div class="conv-head">
                <span class="conv-title">{{ conv.title }}</span>
                <el-badge v-if="conv.unread_count" :value="conv.unread_count" :max="99" />
              </div>
              <div class="fl-muted">{{ conv.conv_type }} · {{ conv.last_message_summary || '暂无消息' }}</div>
              <div class="fl-muted">{{ fmt(conv.last_message_at) }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16" :lg="17">
        <el-card shadow="never" class="pane">
          <template #header>
            <div class="pane-head">
              <strong>{{ selected ? selected.title : '消息' }}</strong>
              <span v-if="selected" class="fl-muted">
                {{ selected.conv_type }} · 未读 {{ selected.unread_count }}
              </span>
            </div>
          </template>

          <el-empty v-if="!selected" description="请选择左侧会话" :image-size="80" />
          <template v-else>
            <div v-loading="msgLoading">
              <div class="fl-toolbar">
                <el-button
                  v-if="nextCursor !== undefined"
                  size="small"
                  :loading="msgLoadingMore"
                  @click="loadEarlier"
                >
                  加载更早
                </el-button>
                <span class="fl-muted">共 {{ messages.length }} 条</span>
              </div>

              <el-empty v-if="!messages.length" description="暂无消息" :image-size="60" />
              <div v-for="msg in messages" :key="msg.message_id" class="msg-item">
                <div class="msg-meta">
                  <span>用户 {{ msg.sender_id }}</span>
                  <span>{{ fmt(msg.sent_at) }}</span>
                  <el-tag size="small" type="info">#{{ msg.seq }}</el-tag>
                  <el-tag v-if="msg.msg_type !== 'TEXT'" size="small">{{ msg.msg_type }}</el-tag>
                  <el-tag v-if="msg.revoked" size="small" type="danger">已撤回</el-tag>
                  <el-button
                    v-if="msg.sender_id === auth.userId && !msg.revoked"
                    link
                    type="danger"
                    size="small"
                    @click="revokeMessage(msg)"
                  >
                    撤回
                  </el-button>
                </div>
                <div class="msg-body">{{ msg.revoked ? '该消息已撤回' : msg.summary || '（无正文）' }}</div>
                <div v-if="msg.file_id" class="fl-muted">附件 file_id：{{ msg.file_id }}</div>
              </div>
            </div>

            <div class="send-box">
              <div class="send-toolbar">
                <el-select v-model="draft.msg_type" style="width: 110px">
                  <el-option label="TEXT" value="TEXT" />
                  <el-option label="RICH" value="RICH" />
                </el-select>
                <span class="fl-muted">仅展示 summary，RICH 类型正文以后端解析为准</span>
              </div>
              <el-input
                v-model="draft.content"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
                placeholder="输入消息内容"
              />
              <div class="send-actions">
                <el-button type="primary" :loading="sending" @click="sendMessage">发送</el-button>
              </div>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.pane {
  min-height: 520px;
}
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.conv-list {
  max-height: 620px;
  overflow-y: auto;
}
.conv-item {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.2s;
}
.conv-item:hover {
  background: var(--fl-color-fill-light, #f5f7fa);
}
.conv-item--active {
  background: rgba(31, 94, 255, 0.08);
  border-color: rgba(31, 94, 255, 0.3);
}
.conv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.conv-title {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msg-item {
  padding: 8px 10px;
  border-bottom: 1px solid var(--fl-color-border-light, #ebeef5);
}
.msg-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--fl-color-text-secondary);
}
.msg-body {
  margin: 6px 0 2px;
  white-space: pre-wrap;
  word-break: break-word;
}
.send-box {
  margin-top: 16px;
  border-top: 1px solid var(--fl-color-border-light, #ebeef5);
  padding-top: 12px;
}
.send-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.send-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
</style>
