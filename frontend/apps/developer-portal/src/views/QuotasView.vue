<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  openApiPortal,
  type PortalAppView,
  type PortalQuotaRequestView,
  type PortalQuotaView,
} from '@fenglin/api-client'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const route = useRoute()
const auth = useAuthStore()

const apps = ref<PortalAppView[]>([])
const selectedAppId = ref<string | null>(null)
const rows = ref<PortalQuotaView[]>([])
const loading = ref(false)
const error = ref('')

const canView = computed(() => auth.can('biz:openapi:quota:view'))
const canApply = computed(() => auth.can('biz:openapi:quota:apply'))
const canListApps = computed(() => auth.can('biz:openapi:portal:view'))
const selectedApp = computed(() => apps.value.find((app) => app.app_id === selectedAppId.value))
const warningCount = computed(() => rows.value.filter((row) => row.limit_status !== 'NORMAL').length)
const exceededCount = computed(() => rows.value.filter((row) => row.limit_status === 'EXCEEDED').length)
const hasQuota = computed(() => rows.value.length > 0)

const gapItems = [
  '配额配置属于管理端 1004（PUT /api/hub/v1/admin/openapi/quotas），开发者不能直接改配置；需调整时在页面提交申请，由平台审批通过后自动写入。',
  '当前 QPS 来自 Redis 秒级计数，表示本次查询这一秒的计数，用于判断瞬时限流状态，不是历史峰值。',
  '日用量 used_today 由签名网关按应用+接口原子累加，并按 used_date 每日重置；≥80% 显示 WARNING，达到日限额或 QPS 上限显示 EXCEEDED。',
]

const applyVisible = ref(false)
const applySaving = ref(false)
const applyError = ref('')
const applyForm = reactive({
  api_path: '',
  qps_limit: undefined as number | undefined,
  daily_limit: undefined as number | undefined,
  reason: '',
})
const requestRows = ref<PortalQuotaRequestView[]>([])
const requestTotal = ref(0)
const requestPage = ref(1)
const requestSize = ref(10)
const requestLoading = ref(false)

async function loadApps() {
  if (!canListApps.value) {
    error.value = '当前账号缺少 biz:openapi:portal:view，无法加载应用列表。'
    return
  }
  try {
    apps.value = await openApiPortal.apps()
    const queryAppId = String(route.query.app_id ?? '')
    const matched = apps.value.find((app) => app.app_id === queryAppId)
    selectedAppId.value = matched?.app_id ?? apps.value[0]?.app_id ?? null
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadQuotas() {
  if (!canView.value || !selectedAppId.value) {
    rows.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    rows.value = await openApiPortal.quotas(selectedAppId.value)
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadRequests() {
  if (!canApply.value || !selectedAppId.value) {
    requestRows.value = []
    requestTotal.value = 0
    return
  }
  requestLoading.value = true
  try {
    const result = await openApiPortal.quotaRequests({ page: requestPage.value, size: requestSize.value })
    requestRows.value = result.records.filter((row) => row.app_id === selectedAppId.value)
    requestTotal.value = result.total
  } catch (err) {
    requestRows.value = []
    requestTotal.value = 0
    error.value = describeError(err)
  } finally {
    requestLoading.value = false
  }
}

function openApply() {
  Object.assign(applyForm, { api_path: '', qps_limit: undefined, daily_limit: undefined, reason: '' })
  applyError.value = ''
  applyVisible.value = true
}

async function submitApply() {
  if (!selectedAppId.value) {
    applyError.value = '请先选择应用'
    return
  }
  if (!applyForm.reason.trim()) {
    applyError.value = '请填写申请理由'
    return
  }
  if (applyForm.qps_limit == null && applyForm.daily_limit == null) {
    applyError.value = 'QPS 上限与日配额至少填写一项'
    return
  }
  applySaving.value = true
  applyError.value = ''
  try {
    await openApiPortal.applyQuota({
      app_id: selectedAppId.value,
      api_path: applyForm.api_path.trim(),
      qps_limit: applyForm.qps_limit,
      daily_limit: applyForm.daily_limit,
      reason: applyForm.reason.trim(),
    })
    ElMessage.success('申请已提交，待平台审批')
    applyVisible.value = false
    await loadRequests()
  } catch (err) {
    applyError.value = describeError(err)
  } finally {
    applySaving.value = false
  }
}

watch(selectedAppId, () => {
  void loadQuotas()
  void loadRequests()
})

onMounted(async () => {
  await loadApps()
  await loadRequests()
})

function requestStatusTag(status: PortalQuotaRequestView['status']) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

function statusTag(status: PortalQuotaView['limit_status']) {
  if (status === 'EXCEEDED') return 'danger'
  if (status === 'WARNING') return 'warning'
  return 'success'
}
</script>

<template>
  <div>
    <PageHeader module="开放平台" title="配额与用量" description="查看本人应用的日配额、当日用量与限流状态；配置由管理端维护。">
      <template #actions>
        <el-button :loading="loading" @click="loadQuotas">刷新</el-button>
        <el-button v-if="canApply" type="primary" :disabled="!selectedAppId" @click="openApply">申请配额</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-alert
      v-if="!canView"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号缺少 biz:openapi:quota:view，无法查看配额与用量。"
      style="margin-bottom: 12px"
    />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="selectedAppId" placeholder="选择应用" style="width: 300px" :disabled="!apps.length">
          <el-option
            v-for="app in apps"
            :key="app.app_id"
            :label="`${app.name}（${app.code}）`"
            :value="app.app_id"
          />
        </el-select>
        <span class="muted" v-if="selectedApp">
          今日调用 {{ selectedApp.call_today }} / 日配额 {{ selectedApp.quota_daily ?? '未配置' }}
        </span>
      </div>

      <div v-if="hasQuota" class="summary">
        <span>配额项：<strong>{{ rows.length }}</strong></span>
        <span>预警/超限：<strong>{{ warningCount }}</strong></span>
        <span>已超限：<strong>{{ exceededCount }}</strong></span>
      </div>

      <el-empty v-if="!apps.length" description="暂无应用，请先在「我的应用」创建并等待审核。" />
      <el-table v-else :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="api_path" label="接口路径" min-width="240" show-overflow-tooltip />
        <el-table-column label="QPS 上限" width="110">
          <template #default="{ row }">{{ row.qps_limit ?? '未限制' }}</template>
        </el-table-column>
        <el-table-column label="日配额" width="120">
          <template #default="{ row }">{{ row.daily_limit ?? '未限制' }}</template>
        </el-table-column>
        <el-table-column prop="used_today" label="今日已用" width="110" />
        <el-table-column label="用量占比" width="110">
          <template #default="{ row }">
            {{ row.daily_limit ? `${Math.round((row.used_today / row.daily_limit) * 100)}%` : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="当前 QPS" width="110">
          <template #default="{ row }">{{ row.current_qps ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="限流状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.limit_status)" size="small">{{ row.limit_status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="used_date" label="统计日期" width="120" />
        <template #empty><el-empty description="该应用暂无配额配置" /></template>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>我的配额申请</span>
          <span class="muted">共 {{ requestTotal }} 条</span>
        </div>
      </template>
      <el-alert
        v-if="!canApply"
        type="info"
        :closable="false"
        show-icon
        title="当前账号缺少 biz:openapi:quota:apply，无法提交或查看配额申请。"
        style="margin-bottom: 12px"
      />
      <el-table v-else :data="requestRows" v-loading="requestLoading" border stripe size="small">
        <el-table-column prop="api_path" label="接口路径" min-width="220" show-overflow-tooltip />
        <el-table-column label="申请 QPS" width="100">
          <template #default="{ row }">{{ row.requested_qps ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="申请日配额" width="120">
          <template #default="{ row }">{{ row.requested_daily ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="requestStatusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批意见" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.decision_comment || '-' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ row.created_at || '-' }}</template>
        </el-table-column>
        <template #empty><el-empty description="暂无申请记录" /></template>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="requestPage"
          v-model:page-size="requestSize"
          :total="requestTotal"
          :page-sizes="[10, 20, 50]"
          layout="sizes, prev, pager, next"
          @change="loadRequests"
        />
      </div>
    </el-card>

    <el-dialog v-model="applyVisible" title="申请调整配额" width="520px">
      <el-alert v-if="applyError" type="error" :closable="false" show-icon :title="applyError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="接口路径" required>
          <el-input v-model="applyForm.api_path" placeholder="如 /openapi/v1/crm/leads" />
        </el-form-item>
        <el-form-item label="QPS 上限">
          <el-input-number v-model="applyForm.qps_limit" :min="1" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="日配额">
          <el-input-number v-model="applyForm.daily_limit" :min="0" :step="1000" controls-position="right" />
        </el-form-item>
        <el-form-item label="申请理由" required>
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" maxlength="512" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applySaving" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.summary {
  display: flex;
  gap: 24px;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: var(--fl-color-bg);
  border: 1px solid var(--fl-color-border);
  border-radius: var(--fl-radius-md);
  font-size: 13px;
  color: var(--fl-color-text-secondary);
}
.summary strong {
  color: var(--fl-color-text-primary);
  font-size: 15px;
}
.muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
