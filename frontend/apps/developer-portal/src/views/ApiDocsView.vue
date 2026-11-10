<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  openApiPortal,
  type PortalApiDocView,
  type PortalAppView,
  type SandboxCallResult,
} from '@fenglin/api-client'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()

const apps = ref<PortalAppView[]>([])
const selectedAppId = ref('')
const docs = ref<PortalApiDocView[]>([])
const loading = ref(false)
const error = ref('')

const sandboxVisible = ref(false)
const sandboxDoc = ref<PortalApiDocView | null>(null)
const sandboxBody = ref('')
const sandboxCalling = ref(false)
const sandboxResult = ref<SandboxCallResult | null>(null)
const sandboxError = ref('')

const canView = computed(() => auth.can('biz:openapi:portal:view'))
const canSandbox = computed(() => auth.can('biz:openapi:sandbox:call'))
const selectedApp = computed(
  () => apps.value.find((app) => String(app.app_id) === selectedAppId.value) ?? null,
)
const appReady = computed(
  () => selectedApp.value?.review_status === 'APPROVED' && selectedApp.value?.status === 'ENABLED',
)

const gapItems = [
  '接口文档来自平台已发布清单（hub_api_doc）；当前平台仅发布验签探针 /openapi/ping/v1/ping，业务域开放接口发布后会自动出现在本页。',
  '沙箱由服务端使用应用自身密钥签名后真实调用，AppSecret 不下发浏览器；调用会消耗真实配额并写入调用日志。',
  'HMAC 签名串、错误码、重试与限流策略的面向开发者接入规范仍待补齐，详见「接入指南」。',
]

async function loadApps() {
  if (!canView.value) {
    error.value = '当前账号缺少 biz:openapi:portal:view，无法查看接口文档。'
    return
  }
  try {
    apps.value = await openApiPortal.apps()
    selectedAppId.value = apps.value[0] ? String(apps.value[0].app_id) : ''
    await loadDocs()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadDocs() {
  if (!canView.value) return
  loading.value = true
  error.value = ''
  try {
    docs.value = await openApiPortal.apiDocs(
      selectedAppId.value ? { app_id: selectedAppId.value } : {},
    )
  } catch (err) {
    docs.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openSandbox(doc: PortalApiDocView) {
  if (!doc.sandbox_enabled) {
    ElMessage.warning('该接口未开放联调沙箱')
    return
  }
  if (!selectedApp.value) {
    ElMessage.warning('请先在「我的应用」创建应用并等待审核通过')
    return
  }
  if (!appReady.value) {
    ElMessage.warning('应用审核通过并启用后才能联调')
    return
  }
  sandboxDoc.value = doc
  sandboxBody.value = ''
  sandboxResult.value = null
  sandboxError.value = ''
  sandboxVisible.value = true
}

async function runSandbox() {
  if (!sandboxDoc.value || !selectedAppId.value) return
  sandboxCalling.value = true
  sandboxError.value = ''
  sandboxResult.value = null
  try {
    sandboxResult.value = await openApiPortal.sandbox(selectedAppId.value, {
      api_code: sandboxDoc.value.code,
      body: sandboxDoc.value.method === 'GET' ? undefined : sandboxBody.value,
    })
  } catch (err) {
    sandboxError.value = describeError(err)
  } finally {
    sandboxCalling.value = false
  }
}

function prettyJson(value?: string | null) {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function methodTag(method: string) {
  if (method === 'GET') return 'success'
  if (method === 'POST') return 'primary'
  if (method === 'DELETE') return 'danger'
  return 'warning'
}

function statusTag(status: number) {
  if (status < 300) return 'success'
  if (status < 500) return 'warning'
  return 'danger'
}

onMounted(loadApps)
</script>

<template>
  <div v-loading="loading">
    <PageHeader
      module="开放平台"
      title="接口文档与沙箱"
      description="平台已发布的开放接口文档与在线联调；签名由服务端完成，AppSecret 不出服务端。"
    >
      <template #actions>
        <el-button :loading="loading" @click="loadDocs">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice title="接口文档与联调沙箱现状" :items="gapItems" />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-head">
          <strong>联调应用</strong>
          <span class="muted">文档中的订阅状态与沙箱调用均以该应用为维度</span>
        </div>
      </template>
      <el-select
        v-model="selectedAppId"
        placeholder="选择应用（可选）"
        clearable
        style="width: 360px"
        @change="loadDocs"
      >
        <el-option
          v-for="app in apps"
          :key="app.app_id"
          :label="`${app.name}（${app.code} / ${app.review_status}）`"
          :value="String(app.app_id)"
        />
      </el-select>
      <p v-if="!apps.length" class="muted">暂无应用，请先在「我的应用」创建并等待审核通过。</p>
      <p v-else-if="selectedApp && !appReady" class="muted">
        当前应用未审核通过或未启用，可以查看文档，但不能发起沙箱调用。
      </p>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-head">
          <strong>接口清单</strong>
          <span class="muted">共 {{ docs.length }} 个已发布接口</span>
        </div>
      </template>
      <el-table :data="docs" border row-key="code">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="doc-detail">
              <p class="desc">{{ row.description || row.summary || '-' }}</p>
              <div class="schema-grid">
                <div>
                  <strong>请求结构</strong>
                  <pre class="code">{{ prettyJson(row.request_schema) }}</pre>
                </div>
                <div>
                  <strong>响应结构</strong>
                  <pre class="code">{{ prettyJson(row.response_schema) }}</pre>
                </div>
              </div>
              <div class="schema-grid">
                <div>
                  <strong>请求示例</strong>
                  <pre class="code">{{ prettyJson(row.sample_request) }}</pre>
                </div>
                <div>
                  <strong>响应示例</strong>
                  <pre class="code">{{ prettyJson(row.sample_response) }}</pre>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="方法" width="90">
          <template #default="{ row }">
            <el-tag :type="methodTag(row.method)" size="small">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="260" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="domain" label="业务域" width="110" />
        <el-table-column label="所需权限" min-width="220">
          <template #default="{ row }">
            <template v-if="row.required_scope">
              <el-tag :type="row.subscribed ? 'success' : 'warning'" size="small">
                {{ row.subscribed ? '已订阅' : '未订阅' }}
              </el-tag>
              <span class="scope-code">{{ row.required_scope }}</span>
            </template>
            <span v-else class="muted">无需订阅</span>
          </template>
        </el-table-column>
        <el-table-column label="沙箱" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canSandbox"
              link
              type="primary"
              :disabled="!row.sandbox_enabled || !appReady"
              @click="openSandbox(row)"
            >
              试调用
            </el-button>
            <span v-else class="muted">无权限</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无已发布接口" /></template>
      </el-table>
      <p v-if="!canSandbox" class="muted">缺少 biz:openapi:sandbox:call，只能查看文档，不能发起联调。</p>
    </el-card>

    <el-dialog
      v-model="sandboxVisible"
      :title="sandboxDoc ? `联调：${sandboxDoc.name}` : '联调'"
      width="760px"
      :close-on-click-modal="false"
    >
      <template v-if="sandboxDoc">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="请求">
            <el-tag :type="methodTag(sandboxDoc.method)" size="small">{{ sandboxDoc.method }}</el-tag>
            <span class="mono">{{ sandboxDoc.path }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="应用">{{ selectedApp?.name ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="field">
          <span class="label">请求体（JSON）</span>
          <el-input
            v-model="sandboxBody"
            type="textarea"
            :rows="6"
            :disabled="sandboxDoc.method === 'GET'"
            placeholder='{"key":"value"}'
          />
          <span v-if="sandboxDoc.method === 'GET'" class="muted">GET 请求无请求体，签名基于空 body 计算。</span>
        </div>
        <el-alert
          v-if="sandboxError"
          type="error"
          :closable="false"
          show-icon
          :title="sandboxError"
          style="margin-bottom: 12px"
        />
        <template v-if="sandboxResult">
          <el-descriptions :column="2" border class="result-meta">
            <el-descriptions-item label="HTTP 状态">
              <el-tag :type="statusTag(sandboxResult.http_status)" size="small">
                {{ sandboxResult.http_status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="耗时">{{ sandboxResult.latency_ms }} ms</el-descriptions-item>
            <el-descriptions-item label="AppKey">{{ sandboxResult.app_key }}</el-descriptions-item>
            <el-descriptions-item label="Request-Id">{{ sandboxResult.request_id }}</el-descriptions-item>
            <el-descriptions-item label="时间戳">{{ sandboxResult.timestamp }}</el-descriptions-item>
            <el-descriptions-item label="Nonce">{{ sandboxResult.nonce }}</el-descriptions-item>
            <el-descriptions-item label="签名" :span="2">
              <span class="mono">{{ sandboxResult.signature }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <div class="field">
            <span class="label">
              响应体
              <el-tag v-if="sandboxResult.body_truncated" type="warning" size="small">已截断</el-tag>
            </span>
            <pre class="code">{{ prettyJson(sandboxResult.response_body) }}</pre>
          </div>
        </template>
      </template>
      <template #footer>
        <el-button @click="sandboxVisible = false">关闭</el-button>
        <el-button type="primary" :loading="sandboxCalling" @click="runSandbox">发送请求</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.section {
  margin-bottom: 12px;
}
.card-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.muted {
  margin: 8px 0 0;
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
.scope-code {
  margin-left: 8px;
  font-size: 12px;
  color: var(--fl-color-text-secondary);
}
.doc-detail {
  padding: 4px 16px 12px;
}
.desc {
  margin: 0 0 12px;
  color: var(--fl-color-text-secondary);
  font-size: 13px;
}
.schema-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.code {
  margin: 8px 0 0;
  padding: 12px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: var(--fl-radius-md);
  font-size: 12px;
  line-height: 1.7;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.field {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.label {
  font-size: 13px;
  font-weight: 600;
}
.result-meta {
  margin-top: 12px;
}
.mono {
  margin-left: 8px;
  font-family: var(--fl-font-mono, monospace);
  word-break: break-all;
}
</style>
