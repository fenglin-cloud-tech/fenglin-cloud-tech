<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { openApiPortal, type PortalAppView, type PortalQuotaView } from '@fenglin/api-client'
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
const canListApps = computed(() => auth.can('biz:openapi:portal:view'))
const selectedApp = computed(() => apps.value.find((app) => app.app_id === selectedAppId.value))
const warningCount = computed(() => rows.value.filter((row) => row.limit_status !== 'NORMAL').length)
const exceededCount = computed(() => rows.value.filter((row) => row.limit_status === 'EXCEEDED').length)
const hasQuota = computed(() => rows.value.length > 0)

const gapItems = [
  '配额配置属于管理端 1004（PUT /api/hub/v1/admin/openapi/quotas），开发者门户只读展示，不能自行放大限额。',
  '当前 QPS 来自 Redis 秒级计数，表示本次查询这一秒的计数，用于判断瞬时限流状态，不是历史峰值。',
  '日用量 used_today 由签名网关按应用+接口原子累加，并按 used_date 每日重置；≥80% 显示 WARNING，达到日限额或 QPS 上限显示 EXCEEDED。',
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

watch(selectedAppId, () => {
  void loadQuotas()
})

onMounted(async () => {
  await loadApps()
})

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
</style>
