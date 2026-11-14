<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH, formatDateTime } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const kbOptions = ref<AiRecord[]>([])
const kbLoading = ref(false)
const selectedKb = ref<number | undefined>(undefined)

const rows = ref<AiRecord[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AiRecord | null>(null)
const detailError = ref('')

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const form = reactive({
  title: '',
  content: '',
  doc_type: 'TXT',
  source_uri: '',
  sec_level: 2,
  data_source_id: '',
})

const gapItems = [
  '没有文件上传接口，只能提交文本 content 或 source_uri 引用；content 会按段落切分为分块。',
  '审核通过与建索引是两步：APPROVE 会自动生成索引任务，也可在列表单独触发建索引。',
  '文档列表要求 ai:chat:use；详情、新建、提交审核与审核操作要求 tenant:ai:kb:review；建索引要求 ai:index:build。',
]

async function loadKb() {
  kbLoading.value = true
  try {
    const result = await intelligenceApi.kb.list({ page: 1, size: 100 })
    kbOptions.value = result.records ?? []
    if (!selectedKb.value && kbOptions.value.length) {
      selectedKb.value = kbOptions.value[0].id
      await loadDocuments()
    }
  } catch (err) {
    kbOptions.value = []
    error.value = describeError(err)
  } finally {
    kbLoading.value = false
  }
}

async function loadDocuments() {
  if (!selectedKb.value) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await intelligenceApi.documents.list(selectedKb.value, { page: page.value, size: size.value })
    rows.value = result.records ?? []
    total.value = result.total ?? 0
  } catch (err) {
    rows.value = []
    total.value = 0
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function changeKb() {
  page.value = 1
  loadDocuments()
}

function changePage(next: number) {
  page.value = next
  loadDocuments()
}

async function openDetail(row: AiRecord) {
  detailVisible.value = true
  detail.value = null
  detailError.value = ''
  detailLoading.value = true
  try {
    detail.value = await intelligenceApi.documents.detail(row.id)
  } catch (err) {
    detailError.value = describeError(err)
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  createError.value = ''
  form.title = ''
  form.content = ''
  form.doc_type = 'TXT'
  form.source_uri = ''
  form.sec_level = 2
  form.data_source_id = ''
  createVisible.value = true
}

async function submitCreate() {
  createError.value = ''
  if (!selectedKb.value) {
    createError.value = '请先选择知识库'
    return
  }
  if (!form.title.trim()) {
    createError.value = '请填写文档标题'
    return
  }
  if (!form.content.trim() && !form.source_uri.trim()) {
    createError.value = 'content 与 source_uri 至少填写一项'
    return
  }
  createSaving.value = true
  try {
    const created = await intelligenceApi.documents.create({
      knowledge_base_id: selectedKb.value,
      title: form.title.trim(),
      content: form.content.trim() || undefined,
      doc_type: form.doc_type,
      source_uri: form.source_uri.trim() || undefined,
      sec_level: form.sec_level,
      data_source_id: form.data_source_id ? form.data_source_id : undefined,
    })
    ElMessage.success(`文档已创建：${created?.title ?? created?.id ?? ''}`)
    createVisible.value = false
    page.value = 1
    await loadDocuments()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function submitReview(row: AiRecord) {
  try {
    await intelligenceApi.documents.submitReview(row.id)
    ElMessage.success('已提交审核')
    await loadDocuments()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function review(row: AiRecord, action: 'APPROVE' | 'REJECT') {
  try {
    await intelligenceApi.documents.review(row.id, action)
    ElMessage.success(action === 'APPROVE' ? '审核已通过，后端将生成索引任务' : '审核已驳回')
    await loadDocuments()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function buildIndex(row: AiRecord) {
  try {
    await intelligenceApi.documents.index(row.id)
    ElMessage.success('索引任务已提交')
    await loadDocuments()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(loadKb)
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="文档与索引"
      description="按知识库管理文档、审核与索引；数据来自 /api/intelligence/v1/knowledge-bases/{id}/documents。"
    >
      <template #actions>
        <el-select
          v-model="selectedKb"
          :loading="kbLoading"
          placeholder="选择知识库"
          filterable
          style="width: 220px"
          @change="changeKb"
        >
          <el-option
            v-for="kb in kbOptions"
            :key="kb.id"
            :label="`${kb.name ?? kb.code ?? kb.id}`"
            :value="kb.id"
          />
        </el-select>
        <el-button v-perm="'tenant:ai:kb:review'" type="primary" :disabled="!selectedKb" @click="openCreate">新建文档</el-button>
        <el-button :loading="loading" @click="loadDocuments">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-empty v-if="!selectedKb" description="请选择知识库后查看文档" :image-size="70" />
      <el-table v-else v-loading="loading" :data="rows" border stripe max-height="560">
        <el-table-column prop="id" label="ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="190" show-overflow-tooltip />
        <el-table-column prop="doc_type" label="类型" width="90" />
        <el-table-column prop="sec_level" label="密级" width="80" />
        <el-table-column prop="review_status" label="审核状态" width="120" />
        <el-table-column prop="index_status" label="索引状态" width="120" />
        <el-table-column prop="chunk_count" label="分块数" width="90" />
        <el-table-column prop="created_at" label="创建时间" width="150" :formatter="(row: AiRecord) => formatDateTime(row.created_at)" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'tenant:ai:kb:review'" link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-perm="'tenant:ai:kb:review'" link type="primary" @click="submitReview(row)">提交审核</el-button>
            <el-button v-perm="'tenant:ai:kb:review'" link type="success" @click="review(row, 'APPROVE')">通过</el-button>
            <el-button v-perm="'tenant:ai:kb:review'" link type="danger" @click="review(row, 'REJECT')">驳回</el-button>
            <el-button v-perm="'ai:index:build'" link type="warning" @click="buildIndex(row)">建索引</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该知识库暂无文档" :image-size="60" />
        </template>
      </el-table>
      <el-pagination
        v-if="selectedKb"
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="changePage"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="文档详情" size="600px">
      <el-alert v-if="detailError" type="error" :closable="false" show-icon :title="detailError" style="margin-bottom: 12px" />
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="2" border size="small">
          <el-descriptions-item label="标题" :span="2">{{ detail.title ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="知识库">{{ detail.knowledge_base_id ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ detail.doc_type ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="密级">{{ detail.sec_level ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="分块数">{{ detail.chunk_count ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="审核状态">{{ detail.review_status ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="索引状态">{{ detail.index_status ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源 URI" :span="2">{{ detail.source_uri ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">{{ detail.tags ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDateTime(detail.published_at) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detail.created_at) }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!detailLoading" description="暂无详情" :image-size="60" />
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" title="新建文档" width="640px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="120px">
        <el-form-item label="知识库">
          <el-input :model-value="selectedKb" disabled />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="form.doc_type" style="width: 100%">
            <el-option label="TXT" value="TXT" />
            <el-option label="MD" value="MD" />
            <el-option label="HTML" value="HTML" />
            <el-option label="PDF" value="PDF" />
            <el-option label="DOCX" value="DOCX" />
          </el-select>
        </el-form-item>
        <el-form-item label="密级">
          <el-select v-model="form.sec_level" style="width: 100%">
            <el-option v-for="level in [1, 2, 3, 4]" :key="level" :label="`L${level}`" :value="level" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源 URI">
          <el-input v-model="form.source_uri" placeholder="选填，与正文至少填写一项" />
        </el-form-item>
        <el-form-item label="数据源 ID">
          <el-input v-model="form.data_source_id" placeholder="选填，关联 data source id" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="form.content" type="textarea" :rows="7" placeholder="选填，纯文本内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
