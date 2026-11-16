<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH, formatDateTime } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const entities = ref<AiRecord[]>([])
const loading = ref(false)
const error = ref('')
const query = reactive({ kb_id: '', keyword: '', limit: 20 })

const pathQuery = reactive({ from_id: '', to_id: '', max_depth: 3 })
const pathLoading = ref(false)
const pathError = ref('')
const pathResult = ref<AiRecord | null>(null)

const entityVisible = ref(false)
const entitySaving = ref(false)
const entityError = ref('')
const entityForm = reactive({
  knowledge_base_id: '',
  name: '',
  entity_type: '',
  description: '',
  aliases: '',
})

const relationVisible = ref(false)
const relationSaving = ref(false)
const relationError = ref('')
const relationForm = reactive({
  from_entity_id: '',
  to_entity_id: '',
  relation_type: '',
  weight: 1,
})

const pathNodes = computed<unknown[]>(() => {
  const nodes = pathResult.value?.nodes
  return Array.isArray(nodes) ? nodes : []
})

const gapItems = [
  '图数据来自 GET /kg/graph（nodes + edges），页面按环形布局渲染，点击节点查看实体详情。',
  '实体与关系支持软删除：删除实体同时软删其关联关系（接口返回 cascade_relations 条数）。',
  '实体与路径查询要求 ai:kg:query；新建/删除实体与关系要求 tenant:ai:kg:manage。',
]

const graphLoading = ref(false)
const graphError = ref('')
const graph = ref<AiRecord | null>(null)

const graphNodes = computed<AiRecord[]>(() => {
  const list = graph.value?.nodes
  return Array.isArray(list) ? list : []
})

const graphEdges = computed<AiRecord[]>(() => {
  const list = graph.value?.edges
  return Array.isArray(list) ? list : []
})

const nodePositions = computed<Record<string, { x: number; y: number }>>(() => {
  const positions: Record<string, { x: number; y: number }> = {}
  const total = graphNodes.value.length
  const centerX = 360
  const centerY = 210
  const radius = total <= 1 ? 0 : Math.min(170, 90 + total * 12)
  graphNodes.value.forEach((node, index) => {
    const angle = (2 * Math.PI * index) / Math.max(total, 1) - Math.PI / 2
    positions[String(node.id)] = {
      x: centerX + radius * Math.cos(angle),
      y: centerY + radius * Math.sin(angle),
    }
  })
  return positions
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AiRecord | null>(null)

async function loadGraph() {
  graphLoading.value = true
  graphError.value = ''
  try {
    graph.value = await intelligenceApi.kg.graph({
      kbId: query.kb_id ? query.kb_id : undefined,
      depth: 2,
    })
  } catch (err) {
    graph.value = null
    graphError.value = describeError(err)
  } finally {
    graphLoading.value = false
  }
}

async function openEntityDetail(row: AiRecord) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await intelligenceApi.kg.entity(row.id)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function removeEntity(row: AiRecord) {
  try {
    await ElMessageBox.confirm(`确认删除实体「${row.name}」及其关联关系？`, '删除实体', { type: 'warning' })
  } catch {
    return
  }
  try {
    const result = await intelligenceApi.kg.deleteEntity(row.id)
    ElMessage.success(`实体已删除，级联关系 ${result?.cascade_relations ?? 0} 条`)
    await Promise.all([loadEntities(), loadGraph()])
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function loadEntities() {
  loading.value = true
  error.value = ''
  try {
    entities.value = await intelligenceApi.kg.entities({
      kb_id: query.kb_id ? query.kb_id : undefined,
      keyword: query.keyword.trim() || undefined,
      limit: query.limit,
    })
  } catch (err) {
    entities.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function queryPath() {
  if (!pathQuery.from_id || !pathQuery.to_id) {
    pathError.value = '起点与终点实体 ID 必填'
    return
  }
  pathLoading.value = true
  pathError.value = ''
  pathResult.value = null
  try {
    pathResult.value = await intelligenceApi.kg.paths({
      from_id: pathQuery.from_id,
      to_id: pathQuery.to_id,
      max_depth: pathQuery.max_depth,
    })
  } catch (err) {
    pathError.value = describeError(err)
  } finally {
    pathLoading.value = false
  }
}

function openEntity() {
  entityError.value = ''
  entityVisible.value = true
}

async function submitEntity() {
  entityError.value = ''
  if (!entityForm.knowledge_base_id || !entityForm.name.trim() || !entityForm.entity_type.trim()) {
    entityError.value = '知识库 ID、名称与实体类型为必填项'
    return
  }
  entitySaving.value = true
  try {
    await intelligenceApi.kg.createEntity({
      knowledge_base_id: entityForm.knowledge_base_id,
      name: entityForm.name.trim(),
      entity_type: entityForm.entity_type.trim(),
      description: entityForm.description.trim() || undefined,
      aliases: entityForm.aliases.trim()
        ? entityForm.aliases.split(',').map((item) => item.trim()).filter(Boolean)
        : undefined,
    })
    ElMessage.success('实体已创建')
    entityVisible.value = false
    entityForm.knowledge_base_id = ''
    entityForm.name = ''
    entityForm.entity_type = ''
    entityForm.description = ''
    entityForm.aliases = ''
    await loadEntities()
  } catch (err) {
    entityError.value = describeError(err)
  } finally {
    entitySaving.value = false
  }
}

function openRelation() {
  relationError.value = ''
  relationVisible.value = true
}

async function submitRelation() {
  relationError.value = ''
  if (!relationForm.from_entity_id || !relationForm.to_entity_id || !relationForm.relation_type.trim()) {
    relationError.value = '起点、终点与关系类型为必填项'
    return
  }
  relationSaving.value = true
  try {
    await intelligenceApi.kg.createRelation({
      from_entity_id: relationForm.from_entity_id,
      to_entity_id: relationForm.to_entity_id,
      relation_type: relationForm.relation_type.trim(),
      weight: relationForm.weight,
    })
    ElMessage.success('关系已创建')
    relationVisible.value = false
    relationForm.from_entity_id = ''
    relationForm.to_entity_id = ''
    relationForm.relation_type = ''
    relationForm.weight = 1
  } catch (err) {
    relationError.value = describeError(err)
  } finally {
    relationSaving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadEntities(), loadGraph()])
})
</script>

<template>
  <div>
    <PageHeader
      module="云智 Intelligence"
      title="知识图谱"
      description="实体检索、路径查询与实体/关系录入；数据来自 /api/intelligence/v1/kg。"
    >
      <template #actions>
        <el-button v-perm="'tenant:ai:kg:manage'" type="primary" @click="openEntity">新建实体</el-button>
        <el-button v-perm="'tenant:ai:kg:manage'" @click="openRelation">新建关系</el-button>
        <el-button :loading="loading" @click="loadEntities">刷新</el-button>
        <el-button :loading="graphLoading" @click="loadGraph">刷新图谱</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card v-loading="graphLoading" shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div class="fl-toolbar" style="justify-content: space-between">
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
        <g v-for="node in graphNodes" :key="String(node.id)" class="kg-node" @click="openEntityDetail(node)">
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
      <el-empty v-else description="暂无图谱数据（可先新建实体与关系）" :image-size="60" />
    </el-card>

    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>实体查询</template>
      <div class="fl-toolbar" style="margin-bottom: 12px">
        <el-input v-model="query.kb_id" placeholder="知识库 ID" clearable style="width: 160px" />
        <el-input v-model="query.keyword" placeholder="实体名称关键字" clearable style="width: 200px" @keyup.enter="loadEntities" />
        <el-input-number v-model="query.limit" :min="1" :max="200" />
        <el-button type="primary" :loading="loading" @click="loadEntities">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="entities" border stripe max-height="420">
        <el-table-column prop="id" label="实体 ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="knowledge_base_id" label="知识库" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="entity_type" label="类型" width="130" />
        <el-table-column prop="normalized_name" label="归一名称" min-width="150" />
        <el-table-column prop="aliases" label="别名" min-width="180" show-overflow-tooltip />
        <el-table-column prop="confidence" label="置信度" width="100" />
        <el-table-column prop="created_at" label="创建时间" width="150" :formatter="(row: AiRecord) => formatDateTime(row.created_at)" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEntityDetail(row)">详情</el-button>
            <el-button v-perm="'tenant:ai:kg:manage'" link type="danger" @click="removeEntity(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无实体" :image-size="60" />
        </template>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>路径查询</template>
      <div class="fl-toolbar" style="margin-bottom: 12px">
        <el-input v-model="pathQuery.from_id" placeholder="起点实体 ID" style="width: 160px" />
        <el-input v-model="pathQuery.to_id" placeholder="终点实体 ID" style="width: 160px" />
        <el-input-number v-model="pathQuery.max_depth" :min="1" :max="6" />
        <el-button v-perm="'ai:kg:query'" type="primary" :loading="pathLoading" @click="queryPath">查询路径</el-button>
      </div>
      <el-alert v-if="pathError" type="error" :closable="false" show-icon :title="pathError" style="margin-bottom: 12px" />

      <el-table v-if="pathNodes.length" :data="pathNodes" border size="small" style="margin-bottom: 12px">
        <el-table-column type="index" label="#" width="70" />
        <el-table-column label="节点 ID">
          <template #default="{ row }">{{ row }}</template>
        </el-table-column>
      </el-table>

      <pre v-if="pathResult" class="fl-mono path-json">{{ JSON.stringify(pathResult, null, 2) }}</pre>
      <el-empty v-else description="尚未查询路径" :image-size="60" />
    </el-card>

    <el-dialog v-model="entityVisible" title="新建实体" width="560px">
      <el-alert v-if="entityError" type="error" :closable="false" show-icon :title="entityError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="知识库 ID" required><el-input v-model="entityForm.knowledge_base_id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="entityForm.name" maxlength="200" /></el-form-item>
        <el-form-item label="实体类型" required><el-input v-model="entityForm.entity_type" placeholder="如 PRODUCT / PERSON" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="entityForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="别名"><el-input v-model="entityForm.aliases" placeholder="英文逗号分隔，选填" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="entityVisible = false">取消</el-button>
        <el-button type="primary" :loading="entitySaving" @click="submitEntity">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationVisible" title="新建关系" width="560px">
      <el-alert v-if="relationError" type="error" :closable="false" show-icon :title="relationError" style="margin-bottom: 12px" />
      <el-form label-width="120px">
        <el-form-item label="起点实体 ID" required><el-input v-model="relationForm.from_entity_id" /></el-form-item>
        <el-form-item label="终点实体 ID" required><el-input v-model="relationForm.to_entity_id" /></el-form-item>
        <el-form-item label="关系类型" required><el-input v-model="relationForm.relation_type" placeholder="如 BELONGS_TO" /></el-form-item>
        <el-form-item label="权重"><el-input-number v-model="relationForm.weight" :min="0" :max="100" :step="0.1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationVisible = false">取消</el-button>
        <el-button type="primary" :loading="relationSaving" @click="submitRelation">创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="实体详情" size="520px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="实体 ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ detail.name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ detail.entity_type || '-' }}</el-descriptions-item>
            <el-descriptions-item label="归一名称">{{ detail.normalized_name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="知识库">{{ detail.knowledge_base_id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="置信度">{{ detail.confidence ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">关联关系（{{ detail.relations?.length ?? 0 }}）</el-divider>
          <el-table :data="detail.relations ?? []" border size="small" max-height="320">
            <el-table-column label="关系" min-width="140">
              <template #default="{ row }">
                {{ row.source_name || row.source_entity_id }} → {{ row.target_name || row.target_entity_id }}
              </template>
            </el-table-column>
            <el-table-column prop="relation_type" label="类型" width="120" />
            <el-table-column prop="weight" label="权重" width="80" />
          </el-table>
        </template>
        <el-empty v-else-if="!detailLoading" description="暂无详情" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.path-json {
  margin: 0;
  padding: 12px;
  max-height: 320px;
  overflow: auto;
  background: var(--fl-color-fill-light, #f5f7fa);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
.kg-graph {
  width: 100%;
  height: 420px;
  background: var(--fl-color-fill-light, #f7f9fc);
  border-radius: 6px;
}
.kg-node {
  cursor: pointer;
}
</style>
