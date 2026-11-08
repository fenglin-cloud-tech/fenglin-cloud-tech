<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { PageHeader, GapNotice, StatCard } from '@fenglin/ui-admin'
import { gatewayApi } from '@/api'
import { describeError } from '@/utils/error'

interface RouteRow {
  service: string
  prefix: string
  target: string
}

const routes: RouteRow[] = [
  { service: 'auth', prefix: '/api/hub/v1/sso/**、/.well-known/**', target: '18081' },
  { service: 'iam', prefix: '/api/hub/v1/admin/users|roles|permissions|menus|dicts|params/**', target: '18082' },
  { service: 'tenant', prefix: '/api/hub/v1/admin/tenants/**', target: '18083' },
  { service: 'workflow', prefix: '/api/hub/v1/workflow/**、/api/hub/v1/admin/workflow/**', target: '18084' },
  { service: 'message', prefix: '/api/hub/v1/messages/**、/api/hub/v1/admin/message/**', target: '18085' },
  { service: 'file', prefix: '/api/hub/v1/files**、/api/hub/v1/admin/file/**', target: '18086' },
  { service: 'audit', prefix: '/api/hub/v1/admin/audits|audit/**、/api/hub/v1/audit/**', target: '18087' },
  { service: 'search', prefix: '/api/hub/v1/search/**、/api/hub/v1/admin/search/**', target: '18088' },
  { service: 'openapi', prefix: '/openapi/**、/api/hub/v1/admin/openapi/**', target: '18091' },
  { service: 'report', prefix: '/api/hub/v1/report/**、/api/hub/v1/report:export、/api/hub/v1/admin/metrics/**', target: '18092' },
  { service: 'lowcode', prefix: '/api/hub/v1/lowcode/**、/api/hub/v1/admin/lowcode/**', target: '18093' },
  { service: 'ai', prefix: '/api/hub/v1/ai/**、/api/hub/v1/admin/ai/**', target: '18094' },
]

const healthLoading = ref(false)
const healthError = ref('')
const health = ref<{ status: string; components?: Record<string, unknown> } | null>(null)
const latency = ref<number | null>(null)
const checkedAt = ref('')

const statusTag = computed(() => {
  if (healthError.value) return 'danger'
  if (!health.value) return 'info'
  return health.value.status === 'UP' ? 'success' : 'warning'
})

const statusText = computed(() => {
  if (healthLoading.value) return '检测中'
  if (healthError.value) return '不可达'
  return health.value?.status ?? '未检测'
})

async function checkHealth() {
  healthLoading.value = true
  healthError.value = ''
  const start = performance.now()
  try {
    health.value = await gatewayApi.health()
    latency.value = Math.round(performance.now() - start)
    checkedAt.value = new Date().toLocaleString()
  } catch (err) {
    health.value = null
    latency.value = null
    healthError.value = describeError(err)
  } finally {
    healthLoading.value = false
  }
}

onMounted(checkHealth)
</script>

<template>
  <div>
    <PageHeader module="网关治理 · 平台底座" title="网关治理" description="网关健康检查与路由前缀总览（18080）。">
      <template #actions>
        <el-button type="primary" :loading="healthLoading" @click="checkHealth">重新检测</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '后端网关仅暴露 actuator health/info/prometheus，未提供路由 CRUD、限流规则、灰度规则的管理 API；本页路由表为网关 application.yml 的静态映射展示，不可在线修改。',
        '灰度能力（GrayGlobalFilter）与限流（RateLimitGlobalFilter）只能在配置文件中调整，无管理接口。',
        '本次已补齐 admin/openapi、admin/lowcode、admin/ai、admin/metrics、report、report:export 六条缺失路由，否则对应页面无法经网关调通。',
      ]"
    />

    <div class="fl-grid">
      <StatCard label="网关状态" :value="statusText" :hint="checkedAt ? `检测于 ${checkedAt}` : '尚未完成检测'" />
      <StatCard label="健康检测耗时" :value="latency != null ? `${latency} ms` : '-'" hint="经 Vite 代理 /actuator/health" />
      <StatCard label="路由条目" :value="String(routes.length)" hint="来自网关 application.yml" />
      <StatCard label="监听端口" value="18080" hint="Vite dev 通过 /api 代理" />
    </div>

    <el-alert
      v-if="healthError"
      type="error"
      :closable="false"
      show-icon
      :title="`网关不可达：${healthError}`"
      description="请确认 fenglin-gateway 已启动（java -jar 或 mvn spring-boot:run），并监听 18080。"
      style="margin-bottom: 16px"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <h3>健康状态</h3>
        <el-tag :type="statusTag">{{ statusText }}</el-tag>
      </div>
      <pre v-if="health" class="fl-pre">{{ JSON.stringify(health, null, 2) }}</pre>
      <el-empty v-else description="暂无健康数据" :image-size="60" />
    </div>

    <div class="fl-card">
      <h3>下游路由前缀</h3>
      <el-table :data="routes" border stripe>
        <el-table-column prop="service" label="服务" width="140" />
        <el-table-column prop="prefix" label="网关前缀" min-width="380" />
        <el-table-column prop="target" label="本地端口" width="120" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
h3 {
  margin: 0 0 12px;
  font-size: 14px;
}
</style>
