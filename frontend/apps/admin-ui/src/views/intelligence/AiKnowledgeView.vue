<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<AiRecord[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const page = ref(1)
const size = ref(20)

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const detailVisible = ref(false)
const detailRow = ref<AiRecord | null>(null)
const form = reactive({
  code: '',
  name: '',
  description: '',
  scope_type: 'TENANT',
  default_sec_level: 2,
  embedding_model: 'bge-large-zh',
})

const gapItems = [
  '知识库 ACL 成员配置在 intelligence-service 没有 REST 接口，本页只维护知识库主体信息。',
  '文档、分块与索引需要在「文档与索引」页维护，本页不提供文档入口。',
  '列表接口要求 ai:chat:use，创建接口要求 tenant:ai:kb:manage；scope_type 后端默认 TENANT。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await intelligenceApi.kb.list({ page: page.value, size: size.value })
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

function openCreate() {
  createError.value = ''
  createVisible.value = true
}

function openDetail(row: AiRecord) {
  detailRow.value = row
  detailVisible.value = true
}

async function submitCreate() {
  createError.value = ''
  if (!form.code.trim() || !form.name.trim()) {
    createError.value = '编码与名称为必填项'
    return
  }
  createSaving.value = true
  try {
    const created = await intelligenceApi.kb.create({
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      scope_type: form.scope_type,
      default_sec_level: form.default_sec_level,
      embedding_model: form.embedding_model.trim() || undefined,
    })
    ElMessage.success(`知识库已创建：${created?.code ?? created?.id ?? ''}`)
    createVisible.value = false
    form.code = ''
    form.name = ''
    form.description = ''
    form.scope_type = 'TENANT'
    form.default_sec_level = 2
    form.embedding_model = 'bge-large-zh'
    page.value = 1
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="知识库"
      description="知识库编码、可见范围、默认密级与向量模型；数据来自 /api/intelligence/v1/knowledge-bases。"
    >
      <template #actions>
        <el-button v-perm="'tenant:ai:kb:manage'" type="primary" @click="openCreate">新建知识库</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe max-height="580" @row-click="openDetail">
        <el-table-column prop="id" label="ID" width="170" />
        <el-table-column prop="code" label="编码" min-width="150" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column label="可见范围" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.scope_type === 'PUBLIC' ? 'success' : 'info'">
              {{ row.scope_type ?? 'TENANT' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="default_sec_level" label="默认密级" width="100" />
        <el-table-column prop="embedding_model" label="向量模型" min-width="160" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="created_at" label="创建时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无知识库" :image-size="60" />
        </template>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="changePage"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="知识库详情" size="520px">
      <el-descriptions v-if="detailRow" :column="1" border size="small">
        <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
        <el-descriptions-item label="编码">{{ detailRow.code ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detailRow.name ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailRow.description ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="可见范围">{{ detailRow.scope_type ?? 'TENANT' }}</el-descriptions-item>
        <el-descriptions-item label="默认密级">{{ detailRow.default_sec_level ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="向量模型">{{ detailRow.embedding_model ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="向量集合">{{ detailRow.vector_collection ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="ES 索引">{{ detailRow.es_index ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailRow.status ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailRow.created_at ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailRow.updated_at ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无详情" :image-size="60" />
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="ACL 成员没有查询接口，详情仅展示列表返回字段。"
        style="margin-top: 12px"
      />
    </el-drawer>

    <el-dialog v-model="createVisible" title="新建知识库" width="560px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="编码" required>
          <el-input v-model="form.code" maxlength="64" placeholder="如 kb-product" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="form.scope_type" style="width: 100%">
            <el-option label="租户内（TENANT）" value="TENANT" />
            <el-option label="公开（PUBLIC）" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认密级">
          <el-select v-model="form.default_sec_level" style="width: 100%">
            <el-option v-for="level in [1, 2, 3, 4]" :key="level" :label="`L${level}`" :value="level" />
          </el-select>
        </el-form-item>
        <el-form-item label="向量模型">
          <el-input v-model="form.embedding_model" placeholder="bge-large-zh" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
