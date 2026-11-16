<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
  '图谱视图来自 GET /kg/graph（nodes + edges），按环形布局渲染，点击节点查看实体详情与关联关系。',
  '实体详情来自 GET /kg/entities/{id}，包含实体字段与双向关系；员工端只读。',
  '页面只读：新建实体 / 关系与删除需 tenant:ai:kg:manage，员工端不提供入口。',
]

const graphLoading = ref(false)
const graphError = ref('')
const graph = ref<AiRecord | null>(null)

const graphNodes = computed<AiRecord[]>(() => (Array.isArray(graph.value?.nodes) ? graph.value!.nodes : []))
const graphEdges = computed<AiRecord[]>(() => (Array.isArray(graph.value?.edges) ? graph.value!.edges : []))

const nodePositions = computed<Record<string, { x: number; y: number }>>(() => {
  const positions: Record<string, { x: number; y: number }> = {}
  const total = graphNodes.value.length
  const radius = total <= 1 ? 0 : Math.min(170, 90 + total * 12)
  graphNodes.value.forEach((node, index) => {
    const angle = (2 * Math.PI * index) / Math.max(total, 1) - Math.PI / 2
    positions[String(node.id)] = {
      x: 360 + radius * Math.cos(angle),
      y: 210 + radius * Math.sin(angle),
    }
  })
  return positions
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AiRecord | null>(null)

async function loadGraph() {
  if (!canQuery) return
  graphLoading.value = true
  graphError.value = ''
  try {
    graph.value = await intelligenceApi.kg.graph({ kbId: form.kb_id, depth: 2 })
  } catch (err) {
    graph.value = null
    graphError.value = describeError(err)
  } finally {
    graphLoading.value = false
  }
}

async function openEntity(entity: AiRecord) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await intelligenceApi.kg.entity(entity.id)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

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

onMounted(async () => {
  await Promise.all([loadKbs(), loadGraph()])
})
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
        <el-button :loading="graphLoading" @click="loadGraph">刷新图谱</el-button>
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

    <el-card v-loading="graphLoading" shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div class="card-head">
          <span>图谱视图</span>
          <span class="fl-muted">节点 {{ graphNodes.length }} · 关系 {{ graphEdges.length }}</span>
        </div>
      </template>
      <el-alert v-if="graphError" type="error" :closable="false" show-icon :title="graphError" style="margin-bottom: 12px" />
      <svg v-if="graphNodes.length" class="kg-graph" viewBox="0 0 720 420" role="img" aria-label="知识图谱">
        <line
          v-for="edge in graphEdges"
          :key="`e-${edge.id}`"
          :x1="nodePositions[String(edge.source)]?.x ?? 0"
          :y1="nodePositions[String(edge.source)]?.y ?? 0"
          :x2="nodePositions[String(edge.target)]?.x ?? 0"
          :y2="nodePositions[String(edge.target)]?.y ?? 0"
          stroke="#c0c4cc"
          stroke-width="1.5"
        />
        <g v-for="node in graphNodes" :key="String(node.id)" class="kg-node" @click="openEntity(node)">
          <circle
            :cx="nodePositions[String(node.id)]?.x ?? 0"
            :cy="nodePositions[String(node.id)]?.y ?? 0"
            r="18"
            fill="#409eff"
            fill-opacity="0.85"
          >
            <title>{{ node.name }}（{{ node.entity_type }}）</title>
          </circle>
          <text
            :x="(nodePositions[String(node.id)]?.x ?? 0) + 22"
            :y="(nodePositions[String(node.id)]?.y ?? 0) + 4"
            font-size="11"
            fill="#303133"
          >{{ node.name }}</text>
        </g>
      </svg>
      <el-empty v-else description="暂无图谱数据" :image-size="60" />
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
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEntity(row)">详情</el-button>
          </template>
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

    <el-drawer v-model="detailVisible" title="实体详情" size="520px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="名称">{{ detail.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ detail.entity_type || '-' }}</el-descriptions-item>
            <el-descriptions-item label="归一名称">{{ detail.normalized_name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="别名">{{ aliasesText(detail.aliases) }}</el-descriptions-item>
            <el-descriptions-item label="置信度">{{ confidenceText(detail.confidence) }}</el-descriptions-item>
            <el-descriptions-item label="知识库">{{ detail.knowledge_base_id || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">关联关系（{{ detail.relations?.length ?? 0 }}）</el-divider>
          <el-table :data="detail.relations ?? []" border size="small" max-height="300">
            <el-table-column label="关系" min-width="150">
              <template #default="{ row }">
                {{ row.source_name || row.source_entity_id }} → {{ row.target_name || row.target_entity_id }}
              </template>
            </el-table-column>
            <el-table-column prop="relation_type" label="类型" width="110" />
            <el-table-column prop="weight" label="权重" width="70" />
          </el-table>
        </template>
        <el-empty v-else-if="!detailLoading" description="暂无详情" :image-size="60" />
      </div>
    </el-drawer>
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
.kg-graph {
  width: 100%;
  height: 420px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}
.kg-node {
  cursor: pointer;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
