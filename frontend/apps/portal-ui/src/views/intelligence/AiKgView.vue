<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH_COMFORT, formatDateTime } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const canQuery = auth.can('ai:kg:query')
const canLoadKb = auth.can('ai:chat:use')

const form = reactive({
  kb_id: undefined as number | undefined,
  keyword: '',
  limit: 20,
})
const entities = ref<AiRecord[]>([])
const loading = ref(false)
const error = ref('')
const searched = ref(false)

const pathForm = reactive({
  from_id: undefined as string | undefined,
  to_id: undefined as string | undefined,
  max_depth: 3,
})
const pathLoading = ref(false)
const pathError = ref('')
const pathResult = ref<AiRecord | null>(null)

const kbOptions = ref<AiRecord[]>([])

const gapItems = [
  '平台未提供图形化图谱渲染接口，本页只能展示实体列表与路径 JSON 结果。',
  '没有实体详情接口，实体记录仅包含名称、类型、别名、置信度等列表字段。',
  '页面只读：新建实体 / 关系需 tenant:ai:kg:manage，员工端不提供入口。',
]

function confidenceText(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  return Number.isNaN(num) ? String(value) : num.toFixed(4)
}

function aliasesText(value: unknown) {
  if (!value) return '-'
  if (Array.isArray(value)) return value.join('、')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
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

async function loadEntities() {
  if (!canQuery) return
  loading.value = true
  error.value = ''
  searched.value = true
  try {
    entities.value = await intelligenceApi.kg.entities({
      kb_id: form.kb_id,
      keyword: form.keyword.trim() || undefined,
      limit: form.limit,
    })
  } catch (err) {
    error.value = describeError(err)
    entities.value = []
  } finally {
    loading.value = false
  }
}

async function loadPath() {
  if (!canQuery) return
  if (!pathForm.from_id || !pathForm.to_id) {
    ElMessage.warning('请输入起始实体 ID 与目标实体 ID')
    return
  }
  pathLoading.value = true
  pathError.value = ''
  pathResult.value = null
  try {
    pathResult.value = await intelligenceApi.kg.paths({
      from_id: pathForm.from_id,
      to_id: pathForm.to_id,
      max_depth: pathForm.max_depth,
    })
  } catch (err) {
    pathError.value = describeError(err)
  } finally {
    pathLoading.value = false
  }
}

function resetPath() {
  pathResult.value = null
  pathError.value = ''
}

onMounted(loadKbs)
</script>

<template>
  <div>
    <PageHeader
      module="丰麟云智"
      title="知识图谱"
      description="实体检索与关系路径查询；数据来自 /kg/entities 与 /kg/paths。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadEntities">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="!canQuery" type="info" :closable="false" show-icon
      title="缺少权限 ai:kg:query，无法查询知识图谱。" style="margin-bottom: 12px" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header><span>实体查询</span></template>
      <el-form label-width="80px" size="small" :disabled="!canQuery">
        <el-form-item label="知识库">
          <el-select
            v-model="form.kb_id"
            clearable
            style="width: 260px"
            :disabled="!canLoadKb"
            placeholder="不选择则查询全部"
          >
            <el-option v-for="kb in kbOptions" :key="String(kb.id)" :label="kb.name || kb.code" :value="kb.id" />
          </el-select>
          <span v-if="!canLoadKb" class="fl-muted" style="margin-left: 12px">
            缺少权限 ai:chat:use，无法加载知识库下拉，可直接使用接口默认范围
          </span>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keyword" style="width: 260px" clearable placeholder="按实体名称模糊匹配" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="form.limit" :min="1" :max="200" size="small" />
          <el-button type="primary" style="margin-left: 12px" :loading="loading" @click="loadEntities">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div class="card-head">
          <span>实体列表</span>
          <span class="fl-muted">共 {{ entities.length }} 个实体</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="entities" border stripe size="small">
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="entity_type" label="类型" width="130" />
        <el-table-column label="别名" min-width="160">
          <template #default="{ row }">{{ aliasesText(row.aliases) }}</template>
        </el-table-column>
        <el-table-column label="置信度" width="100">
          <template #default="{ row }">{{ confidenceText(row.confidence) }}</template>
        </el-table-column>
        <el-table-column prop="knowledge_base_id" label="知识库ID" :width="ID_COLUMN_WIDTH_COMFORT" show-overflow-tooltip />
        <el-table-column prop="created_at" label="创建时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty
            v-if="canQuery"
            :description="searched ? '没有匹配的实体，可调整关键词后重试' : '输入条件后点击查询'"
            :image-size="70"
          />
        </template>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span>关系路径</span></template>
      <el-form label-width="90px" size="small" :disabled="!canQuery">
        <el-form-item label="起始实体">
          <el-input v-model="pathForm.from_id" placeholder="19 位实体 ID" maxlength="25" />
        </el-form-item>
        <el-form-item label="目标实体">
          <el-input v-model="pathForm.to_id" placeholder="19 位实体 ID" maxlength="25" />
        </el-form-item>
        <el-form-item label="最大深度">
          <el-input-number v-model="pathForm.max_depth" :min="1" :max="5" size="small" />
          <el-button type="primary" style="margin-left: 12px" :loading="pathLoading" @click="loadPath">查询路径</el-button>
          <el-button @click="resetPath">清空</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="pathError" type="error" :closable="false" show-icon :title="pathError" style="margin-bottom: 8px" />

      <template v-if="pathResult">
        <el-alert
          v-if="pathResult.found === false"
          type="info"
          :closable="false"
          show-icon
          :title="`在最大深度内未找到 ${pathForm.from_id} 到 ${pathForm.to_id} 的关系路径`"
          style="margin-bottom: 8px"
        />
        <el-descriptions :column="3" border size="small" style="margin-bottom: 8px">
          <el-descriptions-item label="找到路径">{{ pathResult.found === true ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="起始">{{ pathResult.from ?? pathForm.from_id }}</el-descriptions-item>
          <el-descriptions-item label="目标">{{ pathResult.to ?? pathForm.to_id }}</el-descriptions-item>
        </el-descriptions>
        <pre class="path-json">{{ JSON.stringify(pathResult, null, 2) }}</pre>
      </template>
      <el-empty v-else description="填写起止实体后查询关系路径" :image-size="70" />
    </el-card>
  </div>
</template>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.path-json {
  margin: 0;
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 320px;
  overflow: auto;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
