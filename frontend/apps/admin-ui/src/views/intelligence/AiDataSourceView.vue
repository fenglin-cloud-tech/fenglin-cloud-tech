<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH, formatDateTime } from '@fenglin/ui-admin'
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
const form = reactive({
  code: '',
  name: '',
  category: 'FILE',
  connector_type: 'LOCAL_FILE',
  endpoint: '',
  configText: '',
  sync_mode: 'FULL',
  sync_cron: '',
  credential_secret: '',
})

const gapItems = [
  '连通性测试按连接器类型实测：LOCAL_FILE 校验路径存在与可读，HTTP 发 HEAD（3 秒），DB 用 JDBC 直连（3 秒）；其余连接器需部署期专用探针。',
  '手动同步会重建该数据源下 APPROVED 文档的索引任务并刷新分块计数，不改变审核状态。',
  '凭据只写不读：列表仅返回 has_credential，接口不会回显 credential_secret。',
]

const testingId = ref<string | number | null>(null)
const syncingId = ref<string | number | null>(null)
const probeVisible = ref(false)
const probeResult = ref<AiRecord | null>(null)

async function testSource(row: AiRecord) {
  testingId.value = row.id
  probeResult.value = null
  try {
    probeResult.value = await intelligenceApi.dataSources.test(row.id)
    probeVisible.value = true
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    testingId.value = null
  }
}

async function syncSource(row: AiRecord) {
  syncingId.value = row.id
  try {
    const result = await intelligenceApi.dataSources.sync(row.id)
    ElMessage.success(`同步完成：文档 ${result.document_count ?? 0} 篇，重建索引 ${result.indexed ?? 0} 篇`)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    syncingId.value = null
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await intelligenceApi.dataSources.list({ page: page.value, size: size.value })
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

function parseConfig(): AiRecord | undefined {
  const text = form.configText.trim()
  if (!text) return undefined
  const parsed = JSON.parse(text) as unknown
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('config 必须是 JSON 对象')
  }
  return parsed as AiRecord
}

async function submitCreate() {
  createError.value = ''
  if (!form.code.trim() || !form.name.trim()) {
    createError.value = '编码与名称为必填项'
    return
  }
  let config: AiRecord | undefined
  try {
    config = parseConfig()
  } catch (err) {
    createError.value = `config 解析失败：${(err as Error).message}`
    return
  }
  createSaving.value = true
  try {
    const created = await intelligenceApi.dataSources.create({
      code: form.code.trim(),
      name: form.name.trim(),
      category: form.category,
      connector_type: form.connector_type,
      endpoint: form.endpoint.trim() || undefined,
      config,
      sync_mode: form.sync_mode,
      sync_cron: form.sync_cron.trim() || undefined,
      credential_secret: form.credential_secret.trim() || undefined,
    })
    ElMessage.success(
      `数据源已创建：${created?.code ?? created?.id ?? ''}${created?.has_credential ? '（已写入凭据）' : ''}`,
    )
    createVisible.value = false
    form.code = ''
    form.name = ''
    form.category = 'FILE'
    form.connector_type = 'LOCAL_FILE'
    form.endpoint = ''
    form.configText = ''
    form.sync_mode = 'FULL'
    form.sync_cron = ''
    form.credential_secret = ''
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
      title="数据源"
      description="登记知识库与 BI 共用的文件、数据库、API 数据源；数据来自 /api/intelligence/v1/admin/data-sources。"
    >
      <template #actions>
        <el-button v-perm="'tenant:ai:source:manage'" type="primary" @click="openCreate">新建数据源</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border stripe max-height="580">
        <el-table-column prop="id" label="ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="code" label="编码" min-width="130" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="90" />
        <el-table-column prop="connector_type" label="连接器" min-width="110" show-overflow-tooltip />
        <el-table-column prop="endpoint" label="Endpoint" min-width="120" show-overflow-tooltip />
        <el-table-column prop="sync_mode" label="同步模式" width="100" />
        <el-table-column prop="sync_cron" label="同步计划" min-width="110" show-overflow-tooltip />
        <el-table-column label="凭据" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.has_credential ? 'success' : 'info'">
              {{ row.has_credential ? '已配置' : '无' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="created_at" label="创建时间" width="150" :formatter="(row: AiRecord) => formatDateTime(row.created_at)" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="testingId === row.id" @click="testSource(row)">测试</el-button>
            <el-button link type="primary" :loading="syncingId === row.id" @click="syncSource(row)">同步</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据源" :image-size="60" />
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

    <el-dialog v-model="createVisible" title="新建数据源" width="620px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="120px">
        <el-form-item label="编码" required>
          <el-input v-model="form.code" maxlength="64" placeholder="如 ds-file" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option label="FILE 文件" value="FILE" />
            <el-option label="DB 数据库" value="DB" />
            <el-option label="API 接口" value="API" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接器类型">
          <el-input v-model="form.connector_type" placeholder="LOCAL_FILE" />
        </el-form-item>
        <el-form-item label="Endpoint">
          <el-input v-model="form.endpoint" placeholder="选填，如本地目录或数据库地址" />
        </el-form-item>
        <el-form-item label="配置 JSON">
          <el-input
            v-model="form.configText"
            type="textarea"
            :rows="4"
            spellcheck="false"
            placeholder='选填，JSON 对象，如 {"root":"/data"}'
          />
        </el-form-item>
        <el-form-item label="同步模式">
          <el-select v-model="form.sync_mode" style="width: 100%">
            <el-option label="全量（FULL）" value="FULL" />
            <el-option label="增量（INCR）" value="INCR" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步计划">
          <el-input v-model="form.sync_cron" placeholder="选填，如 0 0 2 * * ?" />
        </el-form-item>
        <el-form-item label="凭据密钥">
          <el-input v-model="form.credential_secret" type="password" show-password placeholder="选填，仅写入不回显" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="probeVisible" title="连通性测试结果" width="560px">
      <template v-if="probeResult">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="数据源ID">{{ probeResult.data_source_id }}</el-descriptions-item>
          <el-descriptions-item label="连接器">{{ probeResult.connector_type }}</el-descriptions-item>
          <el-descriptions-item label="Endpoint">{{ probeResult.endpoint || '-' }}</el-descriptions-item>
          <el-descriptions-item label="可达">
            <el-tag size="small" :type="probeResult.reachable ? 'success' : 'danger'">
              {{ probeResult.reachable ? '可达' : '不可达' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时">{{ probeResult.latency_ms ?? '-' }} ms</el-descriptions-item>
          <el-descriptions-item label="详情">{{ probeResult.detail || '-' }}</el-descriptions-item>
          <el-descriptions-item label="测试时间">{{ formatDateTime(probeResult.tested_at) }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>
