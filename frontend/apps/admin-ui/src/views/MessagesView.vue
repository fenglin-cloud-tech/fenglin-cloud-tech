<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import {
  messageApi,
  type MessageChannelRequest,
  type MessageChannelView,
  type MessageView,
  type PreferenceView,
  type TemplateRequest,
  type TemplateView,
} from '@/api'
import { describeError } from '@/utils/error'

const activeTab = ref<'templates' | 'channels' | 'messages' | 'preferences'>('templates')

const templates = ref<TemplateView[]>([])
const templateLoading = ref(false)
const templateError = ref('')

const templateVisible = ref(false)
const saving = ref(false)
const formError = ref('')
const form = reactive<{ code: string; name: string; channel: string; title_tpl: string; content_tpl: string; variablesText: string; locale: string; status: string }>({
  code: '',
  name: '',
  channel: 'INAPP',
  title_tpl: '',
  content_tpl: '',
  variablesText: '',
  locale: 'zh-CN',
  status: 'ENABLED',
})

const channels = ref<MessageChannelView[]>([])
const channelLoading = ref(false)
const channelError = ref('')
const channelVisible = ref(false)
const channelSaving = ref(false)
const channelFormError = ref('')
const channelForm = reactive<{
  id?: number
  channel_type: string
  name: string
  configText: string
  enabled: boolean
  is_default: boolean
  remark: string
}>({
  id: undefined,
  channel_type: 'WECOM',
  name: '',
  configText: '{}',
  enabled: true,
  is_default: false,
  remark: '',
})

const messages = ref<MessageView[]>([])
const messageTotal = ref(0)
const unreadCount = ref(0)
const messageLoading = ref(false)
const messageError = ref('')
const messageQuery = reactive({ status: '', channel: '', page: 1, size: 10 })

const preferences = ref<PreferenceView[]>([])
const prefLoading = ref(false)
const prefError = ref('')
const prefSaving = ref(false)

async function loadTemplates() {
  templateLoading.value = true
  templateError.value = ''
  try {
    templates.value = await messageApi.listTemplates()
  } catch (err) {
    templateError.value = describeError(err)
  } finally {
    templateLoading.value = false
  }
}

async function loadChannels() {
  channelLoading.value = true
  channelError.value = ''
  try {
    channels.value = await messageApi.channels()
  } catch (err) {
    channelError.value = describeError(err)
  } finally {
    channelLoading.value = false
  }
}

function openChannel(row?: MessageChannelView) {
  Object.assign(channelForm, {
    id: row?.id,
    channel_type: row?.channel_type ?? 'WECOM',
    name: row?.name ?? '',
    configText: row?.config ?? '{}',
    enabled: row?.enabled ?? true,
    is_default: row?.is_default ?? false,
    remark: row?.remark ?? '',
  })
  channelFormError.value = ''
  channelVisible.value = true
}

async function submitChannel() {
  if (!channelForm.name.trim()) {
    channelFormError.value = '通道名称不能为空'
    return
  }
  let config: Record<string, unknown> = {}
  if (channelForm.configText.trim()) {
    try {
      const parsed = JSON.parse(channelForm.configText)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new Error('not object')
      }
      config = parsed as Record<string, unknown>
    } catch {
      channelFormError.value = '通道配置必须是 JSON 对象'
      return
    }
  }
  channelSaving.value = true
  channelFormError.value = ''
  try {
    const body: MessageChannelRequest = {
      id: channelForm.id,
      channel_type: channelForm.channel_type,
      name: channelForm.name.trim(),
      config,
      enabled: channelForm.enabled,
      is_default: channelForm.is_default,
      remark: channelForm.remark || undefined,
    }
    await messageApi.saveChannel(body)
    ElMessage.success('通道已保存')
    channelVisible.value = false
    await loadChannels()
  } catch (err) {
    channelFormError.value = describeError(err)
  } finally {
    channelSaving.value = false
  }
}

async function toggleChannel(row: MessageChannelView) {
  try {
    await messageApi.toggleChannel(row.id, !row.enabled)
    ElMessage.success(row.enabled ? '已停用' : '已启用')
    await loadChannels()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function removeChannel(row: MessageChannelView) {
  try {
    await ElMessageBox.confirm(`确认删除通道「${row.name}」？`, '删除通道', { type: 'warning' })
    await messageApi.deleteChannel(row.id)
    ElMessage.success('已删除')
    await loadChannels()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(describeError(err))
    }
  }
}

async function loadMessages() {
  messageLoading.value = true
  messageError.value = ''
  try {
    const result = await messageApi.list(messageQuery)
    messages.value = result.records
    messageTotal.value = result.total
    unreadCount.value = result.unread_count
  } catch (err) {
    messageError.value = describeError(err)
  } finally {
    messageLoading.value = false
  }
}

async function loadPreferences() {
  prefLoading.value = true
  prefError.value = ''
  try {
    preferences.value = await messageApi.preferences()
  } catch (err) {
    prefError.value = describeError(err)
  } finally {
    prefLoading.value = false
  }
}

function openTemplate(row?: TemplateView) {
  Object.assign(form, {
    code: row?.code ?? '',
    name: row?.name ?? '',
    channel: row?.channel ?? 'INAPP',
    title_tpl: row?.title_tpl ?? '',
    content_tpl: row?.content_tpl ?? '',
    variablesText: typeof row?.variables === 'string' ? row.variables : '',
    locale: row?.locale ?? 'zh-CN',
    status: row?.status ?? 'ENABLED',
  })
  formError.value = ''
  templateVisible.value = true
}

async function submitTemplate() {
  if (!form.code || !form.name || !form.channel) {
    formError.value = '模板编码、名称、渠道为必填项'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    const body: TemplateRequest = {
      code: form.code,
      name: form.name,
      channel: form.channel,
      title_tpl: form.title_tpl || undefined,
      content_tpl: form.content_tpl || undefined,
      variables: form.variablesText ? form.variablesText.split(',').map((item) => item.trim()).filter(Boolean) : [],
      locale: form.locale || undefined,
      status: form.status || undefined,
    }
    await messageApi.upsertTemplate(body)
    ElMessage.success('模板已保存')
    templateVisible.value = false
    await loadTemplates()
  } catch (err) {
    formError.value = describeError(err)
  } finally {
    saving.value = false
  }
}

async function markRead(row: MessageView) {
  try {
    await messageApi.read(row.message_id)
    ElMessage.success('已标记已读')
    await loadMessages()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function savePreferences() {
  prefSaving.value = true
  try {
    preferences.value = await messageApi.updatePreferences(preferences.value)
    ElMessage.success('偏好已保存')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    prefSaving.value = false
  }
}

function addPreference() {
  preferences.value.push({ event_type: '', channels: ['INAPP'], quiet_hours_from: '22:00', quiet_hours_to: '08:00' })
}

onMounted(() => {
  void loadTemplates()
  void loadChannels()
  void loadMessages()
  void loadPreferences()
})
</script>

<template>
  <div>
    <PageHeader module="消息中台 · A-18" title="消息中心" description="消息模板、站内消息与通知偏好；短信/邮件通道由后端适配器承载。">
      <template #actions>
        <el-button @click="loadTemplates(); loadMessages(); loadPreferences()">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '消息模板 variables 字段：请求为字符串数组，响应为字符串（后端契约不一致），本页按实际响应兼容展示。',
        '消息模板无删除/启用接口，仅支持按 code 幂等新增或覆盖。',
        '通知偏好需 biz:message:preference:manage 权限，缺少时保存返回 403。',
      ]"
    />

    <div class="fl-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="模板管理" name="templates">
          <div class="fl-toolbar">
            <el-button type="primary" @click="openTemplate()">新增 / 覆盖模板</el-button>
            <span class="fl-muted">共 {{ templates.length }} 个模板</span>
          </div>
          <el-alert v-if="templateError" type="error" :closable="false" show-icon :title="templateError" style="margin-bottom: 12px" />
          <el-table :data="templates" v-loading="templateLoading" border stripe>
            <el-table-column prop="code" label="模板编码" width="180" />
            <el-table-column prop="name" label="名称" width="150" />
            <el-table-column prop="channel" label="渠道" width="100" />
            <el-table-column prop="title_tpl" label="标题模板" min-width="180" />
            <el-table-column prop="content_tpl" label="内容模板" min-width="220" show-overflow-tooltip />
            <el-table-column prop="locale" label="语言" width="90" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTemplate(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="通道管理" name="channels">
          <div class="fl-toolbar">
            <el-button type="primary" @click="openChannel()">新增通道</el-button>
            <span class="fl-muted">共 {{ channels.length }} 个通道</span>
          </div>
          <el-alert v-if="channelError" type="error" :closable="false" show-icon :title="channelError" style="margin-bottom: 12px" />
          <el-table :data="channels" v-loading="channelLoading" border stripe>
            <el-table-column prop="channel_type" label="类型" width="110" />
            <el-table-column prop="name" label="名称" width="180" />
            <el-table-column label="默认" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.is_default" type="success" size="small">默认</el-tag>
                <span v-else class="fl-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="config" label="配置" min-width="240" show-overflow-tooltip />
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openChannel(row)">编辑</el-button>
                <el-button link :type="row.enabled ? 'warning' : 'success'" @click="toggleChannel(row)">
                  {{ row.enabled ? '停用' : '启用' }}
                </el-button>
                <el-button link type="danger" @click="removeChannel(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`我的消息（未读 ${unreadCount}）`" name="messages">
          <div class="fl-toolbar">
            <el-select v-model="messageQuery.status" placeholder="全部状态" clearable style="width: 150px">
              <el-option label="UNREAD" value="UNREAD" />
              <el-option label="READ" value="READ" />
            </el-select>
            <el-select v-model="messageQuery.channel" placeholder="全部渠道" clearable style="width: 150px">
              <el-option label="INAPP" value="INAPP" />
              <el-option label="SMS" value="SMS" />
              <el-option label="EMAIL" value="EMAIL" />
            </el-select>
            <el-button type="primary" plain @click="messageQuery.page = 1; loadMessages()">查询</el-button>
          </div>
          <el-alert v-if="messageError" type="error" :closable="false" show-icon :title="messageError" style="margin-bottom: 12px" />
          <el-table :data="messages" v-loading="messageLoading" border stripe>
            <el-table-column prop="message_no" label="消息编号" width="180" />
            <el-table-column prop="channel" label="渠道" width="90" />
            <el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="发送时间" width="170">
              <template #default="{ row }">{{ row.sent_at ? new Date(row.sent_at).toLocaleString() : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'READ'" link type="primary" @click="markRead(row)">标记已读</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="messageQuery.page"
            v-model:page-size="messageQuery.size"
            :total="messageTotal"
            layout="total, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @change="loadMessages"
          />
        </el-tab-pane>

        <el-tab-pane label="通知偏好" name="preferences">
          <div class="fl-toolbar">
            <el-button @click="addPreference">新增事件偏好</el-button>
            <el-button type="primary" :loading="prefSaving" @click="savePreferences">保存</el-button>
          </div>
          <el-alert v-if="prefError" type="error" :closable="false" show-icon :title="prefError" style="margin-bottom: 12px" />
          <el-table :data="preferences" v-loading="prefLoading" border>
            <el-table-column label="事件类型" min-width="180">
              <template #default="{ row }"><el-input v-model="row.event_type" placeholder="例如 WORKFLOW_TODO" /></template>
            </el-table-column>
            <el-table-column label="渠道" min-width="220">
              <template #default="{ row }">
                <el-checkbox-group v-model="row.channels">
                  <el-checkbox value="INAPP">站内</el-checkbox>
                  <el-checkbox value="SMS">短信</el-checkbox>
                  <el-checkbox value="EMAIL">邮件</el-checkbox>
                </el-checkbox-group>
              </template>
            </el-table-column>
            <el-table-column label="免打扰开始" width="150">
              <template #default="{ row }"><el-input v-model="row.quiet_hours_from" placeholder="22:00" /></template>
            </el-table-column>
            <el-table-column label="免打扰结束" width="150">
              <template #default="{ row }"><el-input v-model="row.quiet_hours_to" placeholder="08:00" /></template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button link type="danger" @click="preferences.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="templateVisible" title="消息模板" width="620px">
      <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="模板编码" required><el-input v-model="form.code" :disabled="!!form.code && templates.some((t) => t.code === form.code)" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="渠道" required>
          <el-select v-model="form.channel" style="width: 100%">
            <el-option label="站内 INAPP" value="INAPP" />
            <el-option label="短信 SMS" value="SMS" />
            <el-option label="邮件 EMAIL" value="EMAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题模板"><el-input v-model="form.title_tpl" /></el-form-item>
        <el-form-item label="内容模板"><el-input v-model="form.content_tpl" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="变量"><el-input v-model="form.variablesText" placeholder="逗号分隔，例如 name,date" /></el-form-item>
        <el-form-item label="语言"><el-input v-model="form.locale" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="ENABLED" value="ENABLED" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="channelVisible" :title="channelForm.id ? '编辑通道' : '新增通道'" width="620px">
      <el-alert v-if="channelFormError" type="error" :closable="false" show-icon :title="channelFormError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="通道类型" required>
          <el-select v-model="channelForm.channel_type" style="width: 100%">
            <el-option label="站内 INAPP" value="INAPP" />
            <el-option label="短信 SMS" value="SMS" />
            <el-option label="邮件 EMAIL" value="EMAIL" />
            <el-option label="企业微信 WECOM" value="WECOM" />
            <el-option label="钉钉 DINGTALK" value="DINGTALK" />
            <el-option label="Webhook" value="WEBHOOK" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="channelForm.name" maxlength="100" /></el-form-item>
        <el-form-item label="通道配置">
          <el-input v-model="channelForm.configText" type="textarea" :rows="6"
                    placeholder='JSON 对象，例如 {"webhook":"https://..."}' />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="channelForm.enabled" /></el-form-item>
        <el-form-item label="设为默认"><el-switch v-model="channelForm.is_default" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="channelForm.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="channelVisible = false">取消</el-button>
        <el-button type="primary" :loading="channelSaving" @click="submitChannel">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
