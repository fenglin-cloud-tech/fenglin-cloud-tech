<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, formatDateTime } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canChat = auth.can('ai:chat:use')
const canSend = auth.can('ai:chat:rag')
const canFeedback = auth.can('ai:chat:feedback')

const conversations = ref<AiRecord[]>([])
const conversationTotal = ref(0)
const convPage = ref(1)
const convSize = ref(10)
const conversationLoading = ref(false)
const activeId = ref<string | number | null>(null)

const messages = ref<AiRecord[]>([])
const messageTotal = ref(0)
const msgPage = ref(1)
const msgSize = ref(50)
const messageLoading = ref(false)
const sending = ref(false)

const kbOptions = ref<AiRecord[]>([])
const loading = ref(false)
const error = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createForm = reactive({ title: '', type: 'RAG', kb_ids: [] as number[] })
const sendForm = reactive({ content: '', sec_level: 2, model_code: '' })

const feedbackVisible = ref(false)
const feedbackSaving = ref(false)
const feedbackForm = reactive({ messageId: '' as string | number, feedback_type: 'LIKE', rating: 0, comment: '' })

const gapItems = [
  '后端未提供流式输出（SSE）接口，发送消息后需同步等待完整回答返回。',
  '引用只返回文档标题、片段与相似度分数，点击引用无独立详情接口；预览能力见知识搜索页。',
  '反馈接口 POST /copilot/messages/{id}/feedback 需要 ai:chat:feedback，无权限时不显示反馈按钮。',
]

const activeConversation = computed(() => conversations.value.find((item) => item.id === activeId.value))

function kbText(value: unknown) {
  if (!value) return '-'
  if (Array.isArray(value)) return value.join('、')
  return String(value)
}

async function loadConversations() {
  if (!canChat) return
  conversationLoading.value = true
  try {
    const result = await intelligenceApi.copilot.conversations({ page: convPage.value, size: convSize.value })
    conversations.value = result.records
    conversationTotal.value = result.total
    if (!activeId.value && result.records.length > 0) await selectConversation(result.records[0])
  } catch (err) {
    error.value = describeError(err)
  } finally {
    conversationLoading.value = false
  }
}

async function loadKbs() {
  if (!canChat) return
  try {
    kbOptions.value = (await intelligenceApi.kb.list({ page: 1, size: 100 })).records
  } catch {
    kbOptions.value = []
  }
}

async function selectConversation(row: AiRecord) {
  activeId.value = row.id
  msgPage.value = 1
  await loadMessages()
}

async function loadMessages() {
  if (!canChat || !activeId.value) return
  messageLoading.value = true
  try {
    const result = await intelligenceApi.copilot.messages(activeId.value, { page: msgPage.value, size: msgSize.value })
    messages.value = result.records
    messageTotal.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    messageLoading.value = false
  }
}

async function createConversation() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请输入会话标题')
    return
  }
  createSaving.value = true
  try {
    const created = await intelligenceApi.copilot.createConversation({
      title: createForm.title.trim(),
      type: createForm.type,
      kb_ids: createForm.kb_ids,
    })
    ElMessage.success('会话已创建')
    createVisible.value = false
    createForm.title = ''
    createForm.kb_ids = []
    activeId.value = created.id
    msgPage.value = 1
    messages.value = []
    messageTotal.value = 0
    await loadConversations()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    createSaving.value = false
  }
}

async function send() {
  if (!activeId.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  if (!sendForm.content.trim()) {
    ElMessage.warning('请输入内容')
    return
  }
  sending.value = true
  error.value = ''
  try {
    await intelligenceApi.copilot.send(activeId.value, {
      content: sendForm.content.trim(),
      sec_level: sendForm.sec_level,
      model_code: sendForm.model_code || undefined,
    })
    sendForm.content = ''
    msgPage.value = Math.max(1, Math.ceil((messageTotal.value + 2) / msgSize.value))
    await loadMessages()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    sending.value = false
  }
}

function openFeedback(message: AiRecord, type: 'LIKE' | 'DISLIKE') {
  feedbackForm.messageId = message.id
  feedbackForm.feedback_type = type
  feedbackForm.rating = type === 'LIKE' ? 5 : 1
  feedbackForm.comment = ''
  feedbackVisible.value = true
}

async function submitFeedback() {
  if (!feedbackForm.messageId) return
  feedbackSaving.value = true
  try {
    await intelligenceApi.copilot.feedback(feedbackForm.messageId, {
      feedback_type: feedbackForm.feedback_type,
      rating: feedbackForm.rating || undefined,
      comment: feedbackForm.comment || undefined,
    })
    ElMessage.success('反馈已提交')
    feedbackVisible.value = false
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    feedbackSaving.value = false
  }
}

onMounted(async () => {
  loading.value = true
  await Promise.all([loadConversations(), loadKbs()])
  loading.value = false
})

</script>

<template>
  <div>
    <PageHeader module="丰麟云智" title="AI Copilot" description="基于知识库的检索增强问答；会话与消息来自 /copilot/conversations 真实接口。">
      <template #actions>
        <el-button v-if="canChat" type="primary" @click="createVisible = true">新建会话</el-button>
        <el-button :loading="loading" @click="loadConversations">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />
    <el-alert v-if="!canChat" type="info" :closable="false" show-icon title="缺少权限 ai:chat:use，无法读取会话与知识库列表。" style="margin-bottom: 12px" />

    <el-row v-else :gutter="12">
      <el-col :span="7">
        <el-card shadow="never" class="copilot-panel">
          <template #header>
            <div class="panel-head"><span>会话列表</span><span class="fl-muted">共 {{ conversationTotal }} 条</span></div>
          </template>
          <div v-loading="conversationLoading" class="conversation-list">
            <div
              v-for="item in conversations"
              :key="String(item.id)"
              class="conversation-item"
              :class="{ 'conversation-item--active': item.id === activeId }"
              @click="selectConversation(item)"
            >
              <div class="conversation-item__main">
                <span class="conversation-item__title">{{ item.title || '未命名会话' }}</span>
                <span class="fl-muted">{{ item.type || '-' }} · 知识库 {{ kbText(item.kb_ids) }}</span>
              </div>
              <span class="fl-muted">{{ item.message_count ?? 0 }}</span>
            </div>
            <el-empty v-if="conversations.length === 0" description="暂无会话" :image-size="60" />
          </div>
          <el-pagination style="margin-top: 8px; justify-content: flex-end" layout="prev, pager, next"
            :total="conversationTotal" :page-size="convSize" :current-page="convPage"
            @current-change="(next: number) => { convPage = next; loadConversations() }" />
        </el-card>
      </el-col>

      <el-col :span="17">
        <el-card shadow="never" class="copilot-panel">
          <template #header>
            <div class="panel-head">
              <span>{{ activeConversation?.title || '消息' }}</span>
              <span class="fl-muted">共 {{ messageTotal }} 条消息</span>
            </div>
          </template>

          <div v-loading="messageLoading" class="message-list">
            <div v-for="message in messages" :key="String(message.id)" class="message-item">
              <div class="message-item__head">
                <el-tag size="small" :type="message.role === 'ASSISTANT' ? 'success' : 'info'">{{ message.role || '-' }}</el-tag>
                <span class="fl-muted">{{ formatDateTime(message.created_at) }}</span>
                <template v-if="message.role === 'ASSISTANT' && canFeedback">
                  <el-button link type="primary" @click="openFeedback(message, 'LIKE')">点赞</el-button>
                  <el-button link type="danger" @click="openFeedback(message, 'DISLIKE')">点踩</el-button>
                </template>
              </div>
              <div class="message-item__content">{{ message.content }}</div>
              <div v-if="message.citations?.length" class="citation-list">
                <div class="fl-muted">引用来源：</div>
                <div v-for="citation in message.citations" :key="String(citation.id ?? citation.chunk_id)" class="citation-item">
                  <span>{{ citation.document_title || '未命名文档' }}</span>
                  <span class="fl-muted">分数 {{ citation.score ?? '-' }}</span>
                </div>
              </div>
            </div>
            <el-empty v-if="messages.length === 0" description="暂无消息，选择会话后可提问" :image-size="70" />
          </div>

          <el-pagination v-if="messageTotal > msgSize" style="margin-top: 8px; justify-content: flex-end" layout="prev, pager, next"
            :total="messageTotal" :page-size="msgSize" :current-page="msgPage"
            @current-change="(next: number) => { msgPage = next; loadMessages() }" />

          <el-divider />
          <el-form label-width="80px" size="small">
            <el-form-item label="提问">
              <el-input v-model="sendForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="基于所选知识库提问" />
            </el-form-item>
            <el-form-item label="密级">
              <el-select v-model="sendForm.sec_level" style="width: 120px">
                <el-option v-for="level in [1, 2, 3, 4]" :key="level" :label="`L${level}`" :value="level" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型">
              <el-input v-model="sendForm.model_code" placeholder="可选，留空由后端决定" />
            </el-form-item>
            <el-form-item>
              <el-button v-if="canSend" type="primary" :loading="sending" @click="send">发送</el-button>
              <span v-else class="fl-muted">缺少权限 ai:chat:rag，无法发送消息。</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="createVisible" title="新建会话" width="520px">
      <el-form label-width="90px">
        <el-form-item label="标题"><el-input v-model="createForm.title" maxlength="60" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.type" style="width: 100%">
            <el-option label="RAG 检索问答" value="RAG" />
            <el-option label="通用对话" value="CHAT" />
            <el-option label="智能体" value="AGENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识库">
          <el-select v-model="createForm.kb_ids" multiple collapse-tags style="width: 100%" placeholder="可选择多个知识库">
            <el-option v-for="kb in kbOptions" :key="String(kb.id)" :label="kb.name || kb.code" :value="kb.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="createConversation">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="feedbackVisible" :title="feedbackForm.feedback_type === 'LIKE' ? '点赞' : '点踩'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="评分"><el-rate v-model="feedbackForm.rating" :max="5" clearable show-score /></el-form-item>
        <el-form-item label="说明"><el-input v-model="feedbackForm.comment" type="textarea" :rows="3" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackSaving" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.copilot-panel { min-height: 560px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; }
.conversation-list { max-height: 520px; overflow-y: auto; }
.conversation-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px; border-radius: 6px; cursor: pointer; }
.conversation-item:hover { background: var(--el-fill-color-light); }
.conversation-item--active { background: var(--el-color-primary-light-9); }
.conversation-item__main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.conversation-item__title { font-size: 14px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.message-list { max-height: 420px; overflow-y: auto; }
.message-item { padding: 10px 0; border-bottom: 1px dashed var(--fl-color-border); }
.message-item__head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.message-item__content { white-space: pre-wrap; word-break: break-word; font-size: 13px; line-height: 1.7; }
.citation-list { margin-top: 8px; padding: 8px; background: var(--el-fill-color-lighter); border-radius: 4px; font-size: 12px; }
.citation-item { display: flex; justify-content: space-between; gap: 12px; padding: 2px 0; }
.fl-muted { color: var(--fl-color-text-secondary); font-size: 12px; }
</style>
