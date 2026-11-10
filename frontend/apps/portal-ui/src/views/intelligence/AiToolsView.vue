<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canWrite = auth.can('ai:writing:use')
const canSummarize = auth.can('ai:summarize:use')
const canTranslate = auth.can('ai:translate:use')

const activeTab = ref('write')
const input = ref('')
const targetLang = ref('英文')
const loading = ref(false)
const error = ref('')
const result = ref<AiRecord | null>(null)
const resultText = ref('')
const degraded = ref(false)

const gapItems = [
  '文本能力依赖平台 AI 网关（chat 模型）；网关不可用时后端会直接返回错误，页面如实展示错误信息。',
  '后端未提供文本助手历史记录 / 收藏接口，结果仅保留在当前页面，刷新后清空。',
  '三个能力分别需要 ai:writing:use、ai:summarize:use、ai:translate:use，无权限的标签页不会发起请求。',
]

const tabs = computed(() => [
  { name: 'write', label: '写作', allowed: canWrite, permission: 'ai:writing:use' },
  { name: 'summarize', label: '摘要', allowed: canSummarize, permission: 'ai:summarize:use' },
  { name: 'translate', label: '翻译', allowed: canTranslate, permission: 'ai:translate:use' },
])

const langOptions = reactive(['英文', '中文', '日文', '韩文', '法文', '德文'])

const currentTab = computed(() => tabs.value.find((item) => item.name === activeTab.value))

const canRun = computed(() => Boolean(currentTab.value?.allowed))

const placeholder = computed(() => {
  if (activeTab.value === 'write') return '输入要点、提纲或零散素材，生成正式文稿'
  if (activeTab.value === 'summarize') return '粘贴需要总结的长文本，保留关键数字与结论'
  return '粘贴需要翻译的文本'
})

function pickText(data: AiRecord): string {
  const candidates = [data.content, data.answer, data.result, data.text, data.output]
  const found = candidates.find((item) => typeof item === 'string' && item.trim().length > 0)
  if (found) return String(found)
  return ''
}

function switchTab(name: string) {
  if (activeTab.value === name) return
  activeTab.value = name
  result.value = null
  resultText.value = ''
  error.value = ''
  degraded.value = false
}

function clearAll() {
  input.value = ''
  result.value = null
  resultText.value = ''
  error.value = ''
  degraded.value = false
}

async function run() {
  if (!input.value.trim()) {
    ElMessage.warning('请输入文本内容')
    return
  }
  if (activeTab.value === 'translate' && !targetLang.value.trim()) {
    ElMessage.warning('请选择目标语言')
    return
  }
  loading.value = true
  error.value = ''
  result.value = null
  resultText.value = ''
  degraded.value = false
  try {
    let data: AiRecord
    if (activeTab.value === 'write') {
      data = await intelligenceApi.copilot.write({ text: input.value.trim() })
    } else if (activeTab.value === 'summarize') {
      data = await intelligenceApi.copilot.summarize({ text: input.value.trim() })
    } else {
      data = await intelligenceApi.copilot.translate({ text: input.value.trim(), target_lang: targetLang.value })
    }
    result.value = data
    resultText.value = pickText(data)
    degraded.value = Boolean(data.degraded)
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function copyResult() {
  if (!resultText.value) return
  navigator.clipboard
    .writeText(resultText.value)
    .then(() => ElMessage.success('已复制'))
    .catch(() => ElMessage.error('复制失败，请手动选择文本'))
}
</script>

<template>
  <div>
    <PageHeader
      module="丰麟云智"
      title="文本工具箱"
      description="写作、摘要与翻译；文本由 /copilot/text:* 接口调用平台 AI 网关生成。"
    >
      <template #actions>
        <el-button @click="clearAll">清空</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="(name: string | number) => switchTab(String(name))">
        <el-tab-pane v-for="tab in tabs" :key="tab.name" :name="tab.name" :label="tab.label">
          <el-alert
            v-if="!tab.allowed"
            type="info"
            :closable="false"
            show-icon
            :title="`缺少权限 ${tab.permission}，该能力不可用。`"
          />
          <template v-else>
            <el-form label-width="80px" size="small">
              <el-form-item v-if="tab.name === 'translate'" label="目标语言">
                <el-select v-model="targetLang" style="width: 200px">
                  <el-option v-for="lang in langOptions" :key="lang" :label="lang" :value="lang" />
                </el-select>
              </el-form-item>
              <el-form-item label="输入文本">
                <el-input
                  v-model="input"
                  type="textarea"
                  :rows="8"
                  maxlength="4000"
                  show-word-limit
                  :placeholder="placeholder"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="loading" :disabled="!canRun" @click="run">
                  {{ tab.name === 'write' ? '生成文稿' : tab.name === 'summarize' ? '生成摘要' : '开始翻译' }}
                </el-button>
              </el-form-item>
            </el-form>

            <el-divider content-position="left">处理结果</el-divider>
            <div v-loading="loading" class="result-box">
              <el-alert
                v-if="degraded"
                type="warning"
                :closable="false"
                show-icon
                title="平台 AI 网关返回降级结果（degraded=true），内容可能来自兜底策略。"
                style="margin-bottom: 8px"
              />
              <template v-if="resultText">
                <div class="result-text">{{ resultText }}</div>
                <div class="result-meta">
                  <span class="fl-muted">
                    {{ result?.mode || tab.name }} · 输入 token {{ result?.token_in ?? '-' }} · 输出 token
                    {{ result?.token_out ?? '-' }}
                  </span>
                  <el-button link type="primary" @click="copyResult">复制</el-button>
                </div>
              </template>
              <el-empty v-else-if="!loading" description="暂无结果，请在上方输入文本后执行" :image-size="70" />
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.result-box {
  min-height: 140px;
}
.result-text {
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.result-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
