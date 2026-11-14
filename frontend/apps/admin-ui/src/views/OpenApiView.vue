<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice, ID_COLUMN_WIDTH } from '@fenglin/ui-admin'
import { openApi, type OpenApiCallLogView, type OpenApiKeyResponse, type OpenAppCreateResponse, type PortalQuotaRequestView } from '@/api'
import { toIsoOrUndefined } from '@fenglin/shared'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const allowed = {
  review: 'platform:openapi:app:review',
  key: 'platform:openapi:key:manage',
  quota: 'platform:openapi:quota:manage',
  callLog: 'platform:openapi:calllog:view',
  approve: 'platform:openapi:quota:approve',
}

const appForm = reactive({ name: '', owner_type: 'TENANT', scopes: '', redirect_uris: '' })
const appCreating = ref(false)
const appError = ref('')
const created = ref<OpenAppCreateResponse | null>(null)

const reviewForm = reactive({ id: undefined as number | undefined, action: 'APPROVE', reason: '' })
const reviewLoading = ref(false)

const keyForm = reactive({ id: undefined as number | undefined, action: '', grace_hours: 24 })
const keyLoading = ref(false)
const keyError = ref('')
const keyResult = ref<OpenApiKeyResponse | null>(null)

const quotaAppId = ref<number | undefined>(undefined)
const quotaRows = ref<Array<{ api_path: string; qps_limit?: number; daily_limit?: number }>>([])
const quotaLoading = ref(false)
const quotaResult = ref<unknown>(null)
const quotaError = ref('')

const logs = ref<OpenApiCallLogView[]>([])
const logTotal = ref(0)
const logLoading = ref(false)
const logError = ref('')
const logQuery = reactive({ app_id: undefined as number | undefined, api_path: '', status: undefined as number | undefined, from: '', to: '', page: 1, size: 10 })

const quotaReqRows = ref<PortalQuotaRequestView[]>([])
const quotaReqTotal = ref(0)
const quotaReqLoading = ref(false)
const quotaReqError = ref('')
const quotaReqComment = ref('')
const quotaReqQuery = reactive({ status: undefined as string | undefined, page: 1, size: 10 })

async function loadQuotaRequests() {
  quotaReqLoading.value = true
  quotaReqError.value = ''
  try {
    const result = await openApi.quotaRequests({
      status: quotaReqQuery.status || undefined,
      page: quotaReqQuery.page,
      size: quotaReqQuery.size,
    })
    quotaReqRows.value = result.records
    quotaReqTotal.value = result.total
  } catch (err) {
    quotaReqRows.value = []
    quotaReqTotal.value = 0
    quotaReqError.value = describeError(err)
  } finally {
    quotaReqLoading.value = false
  }
}

async function reviewQuotaRequest(row: PortalQuotaRequestView, action: 'APPROVE' | 'REJECT') {
  if (action === 'REJECT' && !quotaReqComment.value.trim()) {
    ElMessage.warning('驳回时必须填写审批意见')
    return
  }
  quotaReqLoading.value = true
  try {
    await openApi.reviewQuotaRequest(row.id, {
      action,
      comment: quotaReqComment.value.trim() || undefined,
    })
    ElMessage.success(action === 'APPROVE' ? '已通过并写入配额' : '已驳回')
    quotaReqComment.value = ''
    await loadQuotaRequests()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    quotaReqLoading.value = false
  }
}

async function createApp() {
  if (!appForm.name) {
    appError.value = '应用名称不能为空'
    return
  }
  appCreating.value = true
  appError.value = ''
  created.value = null
  try {
    created.value = await openApi.createApp({
      name: appForm.name,
      owner_type: appForm.owner_type,
      scopes: appForm.scopes ? appForm.scopes.split(',').map((item) => item.trim()).filter(Boolean) : undefined,
      redirect_uris: appForm.redirect_uris ? appForm.redirect_uris.split(',').map((item) => item.trim()).filter(Boolean) : undefined,
    })
    ElMessage.success('应用已注册，等待审核')
  } catch (err) {
    appError.value = describeError(err)
  } finally {
    appCreating.value = false
  }
}

async function submitReview() {
  if (!reviewForm.id) return
  reviewLoading.value = true
  try {
    await openApi.reviewApp(reviewForm.id, { action: reviewForm.action, reason: reviewForm.reason || undefined })
    ElMessage.success('审核已提交')
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    reviewLoading.value = false
  }
}

async function issueKey() {
  if (!keyForm.id) return
  keyLoading.value = true
  keyError.value = ''
  keyResult.value = null
  try {
    keyResult.value = await openApi.issueKey(keyForm.id, {
      action: keyForm.action || undefined,
      grace_hours: keyForm.grace_hours || undefined,
    })
    ElMessage.success('密钥已生成，AppSecret 仅本次显示')
  } catch (err) {
    keyError.value = describeError(err)
  } finally {
    keyLoading.value = false
  }
}

function addQuotaRow() {
  quotaRows.value.push({ api_path: '', qps_limit: 100, daily_limit: 100000 })
}

async function saveQuota() {
  if (!quotaAppId.value) return
  quotaLoading.value = true
  quotaError.value = ''
  try {
    quotaResult.value = await openApi.upsertQuota({
      app_id: quotaAppId.value,
      items: quotaRows.value.filter((item) => item.api_path),
    })
    ElMessage.success('配额已保存')
  } catch (err) {
    quotaError.value = describeError(err)
  } finally {
    quotaLoading.value = false
  }
}

async function loadLogs() {
  logLoading.value = true
  logError.value = ''
  try {
    const result = await openApi.callLogs({
      app_id: logQuery.app_id,
      api_path: logQuery.api_path,
      status: logQuery.status,
      from: toIsoOrUndefined(logQuery.from),
      to: toIsoOrUndefined(logQuery.to),
      page: logQuery.page,
      size: logQuery.size,
    })
    logs.value = result.records
    logTotal.value = result.total
  } catch (err) {
    logError.value = describeError(err)
  } finally {
    logLoading.value = false
  }
}

onMounted(() => {
  void loadLogs()
  void loadQuotaRequests()
})
</script>

<template>
  <div>
    <PageHeader module="开放能力 · A-23" title="开放平台" description="开放应用注册、审核、密钥、配额与调用日志。">
      <template #actions>
        <el-button @click="loadLogs">刷新调用日志</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '后端未提供开放应用列表/详情查询接口（仅注册、审核、密钥、配额、日志），本页所有应用操作需手工输入 app_id。',
        'AppSecret 仅在生成/轮换时返回一次，前端不持久化；关闭弹窗后不可再次查看。',
        '网关原配置未转发 /api/hub/v1/admin/openapi/**，本轮已补充网关路由（否则经网关调用 404）。',
        '当前权限：审核=' + (auth.can(allowed.review) ? '具备' : '缺失') + '，密钥=' + (auth.can(allowed.key) ? '具备' : '缺失') + '，配额=' + (auth.can(allowed.quota) ? '具备' : '缺失') + '，日志=' + (auth.can(allowed.callLog) ? '具备' : '缺失') + '，配额审批=' + (auth.can(allowed.approve) ? '具备' : '缺失') + '。',
        '配额申请审批（OPEN-03）已接入：开发者门户提交后进入本页，批准即写入 hub_api_quota，驳回必须填写意见。',
      ]"
    />

    <div class="fl-grid fl-grid--2" style="margin-bottom: 16px">
      <div class="fl-card">
        <div class="fl-section-title"><span>应用注册</span><span class="fl-muted">HUB-API-1001</span></div>
        <el-alert v-if="appError" type="error" :closable="false" show-icon :title="appError" style="margin-bottom: 12px" />
        <el-alert v-if="created" type="success" :closable="false" show-icon style="margin-bottom: 12px" title="应用已创建">
          <div>app_id：{{ created.app_id }}，code：{{ created.code }}，审核状态：{{ created.review_status }}</div>
        </el-alert>
        <el-form label-width="110px">
          <el-form-item label="应用名称" required><el-input v-model="appForm.name" /></el-form-item>
          <el-form-item label="归属类型">
            <el-select v-model="appForm.owner_type" style="width: 100%">
              <el-option label="TENANT" value="TENANT" />
              <el-option label="PLATFORM" value="PLATFORM" />
            </el-select>
          </el-form-item>
          <el-form-item label="scopes"><el-input v-model="appForm.scopes" placeholder="逗号分隔" /></el-form-item>
          <el-form-item label="回调地址"><el-input v-model="appForm.redirect_uris" placeholder="逗号分隔" /></el-form-item>
          <el-form-item>
            <el-button v-perm="'platform:openapi:app:review'" type="primary" :loading="appCreating" @click="createApp">注册应用</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="fl-card">
        <div class="fl-section-title"><span>审核 · 密钥 · 配额</span><span class="fl-muted">HUB-API-1002~1004</span></div>
        <h4>应用审核</h4>
        <div class="fl-toolbar">
          <el-input-number v-model="reviewForm.id" :min="1" placeholder="app_id" />
          <el-select v-model="reviewForm.action" style="width: 130px">
            <el-option label="APPROVE" value="APPROVE" />
            <el-option label="REJECT" value="REJECT" />
          </el-select>
          <el-input v-model="reviewForm.reason" placeholder="审核意见" style="width: 180px" />
          <el-button v-perm="'platform:openapi:app:review'" :loading="reviewLoading" @click="submitReview">提交审核</el-button>
        </div>
        <h4>密钥生成 / 轮换</h4>
        <el-alert v-if="keyError" type="error" :closable="false" show-icon :title="keyError" style="margin-bottom: 8px" />
        <div class="fl-toolbar">
          <el-input-number v-model="keyForm.id" :min="1" placeholder="app_id" />
          <el-input v-model="keyForm.action" placeholder="动作（可空，如 ROTATE）" style="width: 180px" />
          <el-input-number v-model="keyForm.grace_hours" :min="0" :max="168" />
          <el-button v-perm="'platform:openapi:key:manage'" :loading="keyLoading" @click="issueKey">生成密钥</el-button>
        </div>
        <el-alert v-if="keyResult" type="warning" :closable="false" show-icon title="AppSecret 仅本次显示" style="margin-bottom: 8px">
          <div>app_key：<span class="fl-mono">{{ keyResult.app_key }}</span></div>
          <div>app_secret：<strong class="fl-mono">{{ keyResult.app_secret }}</strong></div>
          <div>fingerprint：{{ keyResult.secret_fingerprint || '-' }}</div>
        </el-alert>
        <h4>配额</h4>
        <el-alert v-if="quotaError" type="error" :closable="false" show-icon :title="quotaError" style="margin-bottom: 8px" />
        <div class="fl-toolbar">
          <el-input-number v-model="quotaAppId" :min="1" placeholder="app_id" />
          <el-button @click="addQuotaRow">添加配额项</el-button>
          <el-button v-perm="'platform:openapi:quota:manage'" type="primary" :loading="quotaLoading" @click="saveQuota">保存配额</el-button>
        </div>
        <el-table v-if="quotaRows.length" :data="quotaRows" size="small" border>
          <el-table-column label="API 路径" min-width="200">
            <template #default="{ row }"><el-input v-model="row.api_path" placeholder="/api/hub/v1/..." /></template>
          </el-table-column>
          <el-table-column label="QPS" width="120">
            <template #default="{ row }"><el-input-number v-model="row.qps_limit" :min="1" size="small" /></template>
          </el-table-column>
          <el-table-column label="日限额" width="150">
            <template #default="{ row }"><el-input-number v-model="row.daily_limit" :min="1" size="small" /></template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div class="fl-card" style="margin-bottom: 16px">
      <div class="fl-section-title"><span>配额申请审批</span><span class="fl-muted">OPEN-03</span></div>
      <div class="fl-toolbar">
        <el-select v-model="quotaReqQuery.status" clearable placeholder="状态" style="width: 140px">
          <el-option label="PENDING" value="PENDING" />
          <el-option label="APPROVED" value="APPROVED" />
          <el-option label="REJECTED" value="REJECTED" />
        </el-select>
        <el-input v-model="quotaReqComment" placeholder="审批意见（驳回必填）" clearable style="width: 260px" />
        <el-button type="primary" plain @click="quotaReqQuery.page = 1; loadQuotaRequests()">查询</el-button>
      </div>
      <el-alert v-if="quotaReqError" type="error" :closable="false" show-icon :title="quotaReqError" style="margin-bottom: 12px" />
      <el-table :data="quotaReqRows" v-loading="quotaReqLoading" border stripe size="small">
        <el-table-column prop="id" label="申请 ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="app_id" label="应用" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="api_path" label="接口路径" min-width="160" show-overflow-tooltip />
        <el-table-column label="申请 QPS" width="80">
          <template #default="{ row }">{{ row.requested_qps ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="申请日配额" width="100">
          <template #default="{ row }">{{ row.requested_daily ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="申请理由" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applied_by" label="申请人" width="90" />
        <el-table-column label="审批意见" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.decision_comment || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button v-perm="'platform:openapi:quota:approve'" link type="primary" @click="reviewQuotaRequest(row, 'APPROVE')">通过</el-button>
              <el-button v-perm="'platform:openapi:quota:approve'" link type="danger" @click="reviewQuotaRequest(row, 'REJECT')">驳回</el-button>
            </template>
            <span v-else class="fl-muted">已处理</span>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无配额申请" /></template>
      </el-table>
      <el-pagination
        v-model:current-page="quotaReqQuery.page"
        v-model:page-size="quotaReqQuery.size"
        :total="quotaReqTotal"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @change="loadQuotaRequests"
      />
    </div>

    <div class="fl-card">
      <div class="fl-section-title"><span>调用日志</span><span class="fl-muted">HUB-API-1005</span></div>
      <div class="fl-toolbar">
        <el-input-number v-model="logQuery.app_id" :min="1" placeholder="app_id" />
        <el-input v-model="logQuery.api_path" placeholder="API 路径" clearable style="width: 220px" />
        <el-input-number v-model="logQuery.status" :min="100" :max="599" placeholder="HTTP 状态" />
        <el-date-picker v-model="logQuery.from" type="datetime" placeholder="开始" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 180px" />
        <el-date-picker v-model="logQuery.to" type="datetime" placeholder="结束" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 180px" />
        <el-button type="primary" plain @click="logQuery.page = 1; loadLogs()">查询</el-button>
      </div>
      <el-alert v-if="logError" type="error" :closable="false" show-icon :title="logError" style="margin-bottom: 12px" />
      <el-table :data="logs" v-loading="logLoading" border stripe>
        <el-table-column prop="id" label="ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="app_id" label="应用" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="app_key" label="app_key" width="170" />
        <el-table-column prop="method" label="方法" width="90" />
        <el-table-column prop="api_path" label="路径" min-width="180" />
        <el-table-column prop="http_status" label="状态" width="90" />
        <el-table-column prop="biz_code" label="业务码" width="110" />
        <el-table-column prop="latency_ms" label="耗时(ms)" width="90" />
        <el-table-column label="调用时间" width="150">
          <template #default="{ row }">{{ row.called_at ? new Date(row.called_at).toLocaleString() : '-' }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="logQuery.page"
        v-model:page-size="logQuery.size"
        :total="logTotal"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="loadLogs"
      />
    </div>
  </div>
</template>

<style scoped>
h4 {
  margin: 16px 0 8px;
  font-size: 13px;
  color: var(--fl-color-text-secondary);
}
</style>
