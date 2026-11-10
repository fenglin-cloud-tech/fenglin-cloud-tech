<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { openApiPortal, type OpenApiCallLogView, type PortalAppView } from '@fenglin/api-client'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const route = useRoute()
const auth = useAuthStore()

const apps = ref<PortalAppView[]>([])
const selectedAppId = ref<string | null>(null)
const rows = ref<OpenApiCallLogView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const apiPath = ref('')
const status = ref<number | undefined>(undefined)
const loading = ref(false)
const error = ref('')

const canView = computed(() => auth.can('biz:openapi:calllog:view'))
const canListApps = computed(() => auth.can('biz:openapi:portal:view'))
const selectedApp = computed(() => apps.value.find((app) => app.app_id === selectedAppId.value))

const gapItems = [
  '日志接口为 GET /openapi/portal/apps/{app_id}/call-logs；app_id 在路径中必填，后端先校验应用归属当前用户，再按租户+应用查询，前端无法通过改参数查看他人应用。',
  '仅记录经开放 API 签名网关（/openapi/**，门户路径除外）的调用；门户自身请求不会写入调用日志。',
  '日志保留周期与归档策略由平台配置；当前未提供日志导出接口。',
]

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

async function loadLogs() {
  if (!canView.value || !selectedAppId.value) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await openApiPortal.callLogs(selectedAppId.value, {
      api_path: apiPath.value.trim() || undefined,
      status: status.value ?? undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    rows.value = []
    total.value = 0
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void loadLogs()
}

function reset() {
  apiPath.value = ''
  status.value = undefined
  search()
}

watch(selectedAppId, () => {
  page.value = 1
  void loadLogs()
})

watch([() => page.value, () => size.value], () => {
  void loadLogs()
})

onMounted(async () => {
  await loadApps()
})

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString() : '-'
}

function statusTag(code: number) {
  if (code < 300) return 'success'
  if (code === 429) return 'warning'
  return 'danger'
}
</script>

<template>
  <div>
    <PageHeader module="开放平台" title="调用日志" description="按应用维度查询开放 API 调用记录，服务端强制校验应用归属。">
      <template #actions>
        <el-button :loading="loading" @click="loadLogs">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-alert
      v-if="!canView"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号缺少 biz:openapi:calllog:view，无法查看调用日志。"
      style="margin-bottom: 12px"
    />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never">
      <div class="toolbar">
        <el-select
          v-model="selectedAppId"
          placeholder="选择应用"
          style="width: 280px"
          :disabled="!apps.length"
        >
          <el-option
            v-for="app in apps"
            :key="app.app_id"
            :label="`${app.name}（${app.code}）`"
            :value="app.app_id"
          />
        </el-select>
        <el-input v-model="apiPath" placeholder="接口路径，如 /openapi/ping/v1/ping" clearable style="width: 280px" />
        <el-select v-model="status" placeholder="HTTP 状态" clearable style="width: 140px">
          <el-option v-for="code in [200, 401, 403, 404, 429, 500]" :key="code" :label="String(code)" :value="code" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <span class="muted" v-if="selectedApp">今日调用 {{ selectedApp.call_today }}</span>
      </div>

      <el-empty v-if="!apps.length" description="暂无应用，请先在「我的应用」创建并等待审核。" />
      <template v-else>
        <el-table :data="rows" v-loading="loading" border stripe size="small">
          <el-table-column label="调用时间" width="180">
            <template #default="{ row }">{{ formatTime(row.called_at) }}</template>
          </el-table-column>
          <el-table-column prop="method" label="方法" width="80" />
          <el-table-column prop="api_path" label="接口路径" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.http_status)" size="small">{{ row.http_status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="biz_code" label="业务码" width="120" />
          <el-table-column prop="latency_ms" label="耗时(ms)" width="100" />
          <el-table-column prop="app_key" label="AppKey" min-width="180" show-overflow-tooltip />
          <el-table-column prop="request_id" label="Request ID" min-width="200" show-overflow-tooltip />
          <template #empty><el-empty description="暂无调用记录" /></template>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            background
          />
        </div>
      </template>
    </el-card>
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
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
