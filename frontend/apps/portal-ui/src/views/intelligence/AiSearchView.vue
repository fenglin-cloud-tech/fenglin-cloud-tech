<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()

const canQuery = auth.can('ai:search:query')
const canSecure = auth.can('ai:search:secure')
const canView = auth.can('ai:search:view')
const canLoadKb = auth.can('ai:chat:use')

const mode = ref<string>('hybrid')
const form = reactive({
  query: '',
  top_k: 10,
  sec_level: 2,
  kb_ids: [] as number[],
  query_type: 'HYBRID',
})

const hits = ref<AiRecord[]>([])
const total = ref(0)
const latency = ref<number | null>(null)
const degraded = ref(false)
const degradeReason = ref('')
const searched = ref(false)
const loading = ref(false)
const error = ref('')

const kbOptions = ref<AiRecord[]>([])

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewDoc = ref<AiRecord | null>(null)
const previewChunks = ref<AiRecord[]>([])

const gapItems = [
  '向量召回依赖平台 AI 中台的嵌入服务，未配置时后端返回 degraded=true 并降级为词法召回（degrade_reason 会说明原因）。',
  '检索结果只返回文档标题、分块内容与相似度分数，平台无高亮、分面（facet）与聚合统计接口。',
  '预览接口 GET /documents/{id}/preview 需要 ai:search:view，且文档密级不得高于请求的 sec_level。',
]

function hitTitle(hit: AiRecord) {
  return hit.document_title || hit.title || `文档 ${hit.document_id ?? '-'}`
}

function hitSnippet(hit: AiRecord) {
  return hit.content || hit.snippet || hit.chunk || '-'
}

function hitScore(hit: AiRecord) {
  if (hit.score === null || hit.score === undefined) return '-'
  const value = Number(hit.score)
  return Number.isNaN(value) ? String(hit.score) : value.toFixed(4)
}

async function loadKbs() {
  if (!canLoadKb) return
  try {
    const result = await intelligenceApi.kb.list({ page: 1, size: 100 })
    kbOptions.value = result.records
  } catch {
    kbOptions.value = []
  }
}

async function search() {
  if (!form.query.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  if (mode.value === 'secure' && !canSecure) {
    ElMessage.warning('缺少安全检索权限 ai:search:secure')
    return
  }
  loading.value = true
  error.value = ''
  searched.value = true
  try {
    const body: AiRecord = {
      query: form.query.trim(),
      top_k: form.top_k,
      sec_level: form.sec_level,
      query_type: form.query_type,
    }
    if (form.kb_ids.length > 0) body.kb_ids = form.kb_ids
    const result =
      mode.value === 'secure'
        ? await intelligenceApi.search.secure({ ...body, query_type: undefined })
        : await intelligenceApi.search.hybrid(body)
    hits.value = Array.isArray(result.hits) ? result.hits : []
    total.value = Number(result.total ?? hits.value.length)
    latency.value = result.latency_ms === undefined ? null : Number(result.latency_ms)
    degraded.value = Boolean(result.degraded)
    degradeReason.value = result.degrade_reason ? String(result.degrade_reason) : ''
  } catch (err) {
    error.value = describeError(err)
    hits.value = []
    total.value = 0
    degraded.value = false
    degradeReason.value = ''
  } finally {
    loading.value = false
  }
}

async function openPreview(hit: AiRecord) {
  const docId = Number(hit.document_id)
  if (!docId) {
    ElMessage.warning('该结果缺少 document_id，无法预览')
    return
  }
  previewVisible.value = true
  previewLoading.value = true
  previewDoc.value = null
  previewChunks.value = []
  try {
    const result = await intelligenceApi.search.preview(docId, form.sec_level)
    previewDoc.value = result
    previewChunks.value = Array.isArray(result.chunks) ? result.chunks : []
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    previewLoading.value = false
  }
}

onMounted(loadKbs)
</script>

<template>
  <div>
    <PageHeader
      module="丰麟云智"
      title="知识搜索"
      description="混合检索与安全检索；结果来自 /search/hybrid 与 /search/secure。"
    >
      <template #actions>
        <el-button :loading="loading" @click="search">搜索</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form label-width="80px" size="small">
        <el-form-item label="检索模式">
          <el-radio-group v-model="mode">
            <el-radio-button label="hybrid">普通检索</el-radio-button>
            <el-radio-button label="secure" :disabled="!canSecure">安全检索</el-radio-button>
          </el-radio-group>
          <span v-if="!canSecure" class="fl-muted" style="margin-left: 12px">缺少权限 ai:search:secure</span>
        </el-form-item>
        <el-form-item label="检索内容">
          <el-input
            v-model="form.query"
            maxlength="200"
            show-word-limit
            placeholder="输入关键词或问题"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="参数">
          <div class="param-row">
            <span class="fl-muted">Top K</span>
            <el-input-number v-model="form.top_k" :min="1" :max="50" size="small" />
            <span class="fl-muted">密级</span>
            <el-select v-model="form.sec_level" style="width: 100px">
              <el-option v-for="level in [1, 2, 3, 4]" :key="level" :label="`L${level}`" :value="level" />
            </el-select>
            <span class="fl-muted">查询类型</span>
            <el-select v-model="form.query_type" style="width: 140px" :disabled="mode === 'secure'">
              <el-option label="HYBRID" value="HYBRID" />
              <el-option label="KEYWORD" value="KEYWORD" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="知识库">
          <el-select
            v-model="form.kb_ids"
            multiple
            collapse-tags
            clearable
            style="width: 100%"
            :disabled="!canLoadKb"
            placeholder="不选择则检索全部知识库"
          >
            <el-option v-for="kb in kbOptions" :key="String(kb.id)" :label="kb.name || kb.code" :value="Number(kb.id)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button v-if="canQuery" type="primary" :loading="loading" @click="search">开始检索</el-button>
          <span v-else class="fl-muted">缺少权限 ai:search:query，无法执行检索。</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="degraded"
      type="warning"
      :closable="false"
      show-icon
      :title="degradeReason || '当前检索已降级为词法召回。'"
      style="margin-bottom: 12px"
    />

    <el-card shadow="never">
      <template #header>
        <div class="result-head">
          <span>检索结果</span>
          <span class="fl-muted">
            共 {{ total }} 条<span v-if="latency !== null"> · 耗时 {{ latency }} ms</span>
          </span>
        </div>
      </template>

      <el-table v-loading="loading" :data="hits" border stripe size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="hit-detail">{{ hitSnippet(row) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="文档标题" min-width="220">
          <template #default="{ row }">{{ hitTitle(row) }}</template>
        </el-table-column>
        <el-table-column label="分块" width="100">
          <template #default="{ row }">{{ row.seq_no ?? row.chunk_id ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="密级" width="80">
          <template #default="{ row }">{{ row.sec_level ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="分数" width="100">
          <template #default="{ row }">{{ hitScore(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="canView" link type="primary" @click="openPreview(row)">预览</el-button>
            <span v-else class="fl-muted">无权限</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="searched && hits.length === 0 && !loading"
        description="没有匹配结果，可调整关键词或密级后重试"
        :image-size="70"
      />
    </el-card>

    <el-drawer v-model="previewVisible" title="文档预览" size="620px">
      <div v-loading="previewLoading">
        <template v-if="previewDoc">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="标题" :span="2">{{ previewDoc.title || '-' }}</el-descriptions-item>
            <el-descriptions-item label="文档ID">{{ previewDoc.id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ previewDoc.doc_type || '-' }}</el-descriptions-item>
            <el-descriptions-item label="密级">{{ previewDoc.sec_level ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="索引状态">{{ previewDoc.index_status || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">分块</el-divider>
          <div v-for="chunk in previewChunks" :key="String(chunk.id)" class="chunk-item">
            <div class="chunk-item__head">
              <span>#{{ chunk.seq_no ?? '-' }}</span>
              <span class="fl-muted">密级 L{{ chunk.sec_level ?? '-' }}</span>
            </div>
            <div class="chunk-item__content">{{ chunk.content }}</div>
          </div>
          <el-empty v-if="previewChunks.length === 0" description="该文档暂无分块" :image-size="60" />
        </template>
        <el-empty v-else-if="!previewLoading" description="未获取到预览内容" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hit-detail {
  padding: 8px 16px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.chunk-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--fl-color-border);
}
.chunk-item__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  font-size: 13px;
}
.chunk-item__content {
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
