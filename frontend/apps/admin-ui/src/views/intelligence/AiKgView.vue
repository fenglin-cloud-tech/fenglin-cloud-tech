<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
  '知识图谱没有实体删除、编辑接口，本页只能查询与新增。',
  '没有图数据接口，无法渲染力导向图；路径查询仅返回后端 JSON 与 nodes 数组。',
  '实体与路径查询要求 ai:kg:query；新建实体与关系要求 tenant:ai:kg:manage。',
]

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

onMounted(loadEntities)
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
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

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
</style>
