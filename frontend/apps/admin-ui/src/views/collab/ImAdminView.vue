<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice } from '@fenglin/ui-admin'
import { collabApi, type CollabConversationAudit } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const activeTab = ref<'audit' | 'words'>('audit')

const rows = ref<CollabConversationAudit[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const auditRange = ref<string[]>([])
const query = reactive({ keyword: '', page: 1, size: 10 })

const saving = ref(false)
const formError = ref('')
const form = reactive({
  words: '',
  action: 'BLOCK',
  scope: 'TENANT',
  enabled: true,
})

const actionOptions = [
  { value: 'BLOCK', label: 'BLOCK（拦截）' },
  { value: 'REPLACE', label: 'REPLACE（替换为 ***）' },
]

const scopeOptions = [
  { value: 'TENANT', label: 'TENANT（全租户）' },
  { value: 'DEPT', label: 'DEPT（部门）' },
]

async function loadAudit() {
  loading.value = true
  error.value = ''
  try {
    const [start, end] = auditRange.value || []
    const result = await collabApi.admin.auditConversations({
      keyword: query.keyword || undefined,
      startTime: start ? new Date(start).getTime() : undefined,
      endTime: end ? new Date(end).getTime() : undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function resetAudit() {
  query.keyword = ''
  query.page = 1
  auditRange.value = []
  void loadAudit()
}

function fillForm(policy: { words: string[]; action: string; scope: string; enabled: boolean }) {
  form.words = (policy.words || []).join('\n')
  form.action = policy.action || 'BLOCK'
  form.scope = policy.scope || 'TENANT'
  form.enabled = policy.enabled
}

async function loadWords() {
  try {
    fillForm(await collabApi.admin.sensitiveWords())
  } catch (err) {
    formError.value = describeError(err)
  }
}

async function submitWords() {
  const words = form.words
    .split(/[\n,，]+/)
    .map((word) => word.trim())
    .filter(Boolean)
  if (!words.length) {
    formError.value = '请至少填写一个敏感词'
    return
  }
  saving.value = true
  formError.value = ''
  try {
    const result = await collabApi.admin.saveSensitiveWords({
      words,
      action: form.action,
      scope: form.scope,
      enabled: form.enabled,
    })
    fillForm(result)
    ElMessage.success(`敏感词策略已保存（策略 ID ${result.policy_id}，共 ${result.word_count} 个词）`)
  } catch (err) {
    formError.value = describeError(err)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadAudit()
  void loadWords()
})
</script>

<template>
  <div>
    <PageHeader
      module="云协 Collab"
      title="IM 会话管理"
      description="管理端查看会话审计指标并维护敏感词策略；不读取会话消息正文。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadAudit">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '敏感词策略已支持 GET 回显，进入页面自动加载已保存的词语与处置动作。',
        '会话审计是只读列表，后端没有单会话消息导出/封禁/解散接口。',
        '管理端不读取会话消息正文，避免越过 biz:collab:im:chat 业务权限。',
      ]"
    />

    <div class="fl-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="会话审计" name="audit">
          <div class="fl-toolbar">
            <el-input v-model="query.keyword" placeholder="会话标题关键词" clearable style="width: 220px" @keyup.enter="query.page = 1; loadAudit()" />
            <el-date-picker
              v-model="auditRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="创建开始时间"
              end-placeholder="创建结束时间"
              style="width: 360px"
            />
            <el-button v-perm="'tenant:collab:im:govern'" type="primary" plain @click="query.page = 1; loadAudit()">查询</el-button>
            <el-button v-perm="'tenant:collab:im:govern'" @click="resetAudit">重置</el-button>
          </div>

          <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

          <el-table :data="rows" v-loading="loading" border stripe row-key="conv_id">
            <el-table-column prop="conv_id" label="会话 ID" min-width="170" show-overflow-tooltip />
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="conv_type" label="类型" width="100" />
            <el-table-column prop="member_count" label="成员数" width="90" />
            <el-table-column prop="msg_count" label="消息数" width="90" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="创建时间" width="180">
              <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @change="loadAudit"
          />
        </el-tab-pane>

        <el-tab-pane label="敏感词策略" name="words">
          <el-alert v-if="formError" type="error" :closable="false" show-icon :title="formError" style="margin-bottom: 12px" />

          <el-form label-width="110px" style="max-width: 720px">
            <el-form-item label="敏感词" required>
              <el-input
                v-model="form.words"
                type="textarea"
                :rows="8"
                placeholder="每行一个，或用英文/中文逗号分隔"
              />
            </el-form-item>
            <el-form-item label="处置动作">
              <el-select v-model="form.action" style="width: 240px">
                <el-option v-for="opt in actionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="生效范围">
              <el-select v-model="form.scope" style="width: 240px">
                <el-option v-for="opt in scopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
            <el-form-item>
              <el-button v-perm="'tenant:collab:im:sensitive:manage'" type="primary" :loading="saving" @click="submitWords">
                保存策略
              </el-button>
            </el-form-item>
          </el-form>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="保存后立即生效，刷新页面自动回显"
            style="margin-top: 12px; max-width: 720px"
          >
            <div class="fl-muted">BLOCK 会拦截命中敏感词的文本消息；REPLACE 会把命中词替换为 ***。</div>
          </el-alert>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>
