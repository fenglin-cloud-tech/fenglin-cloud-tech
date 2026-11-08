<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { PageHeader, GapNotice, StatCard } from '@fenglin/ui-admin'
import { aiApi, auditApi, iamApi, lowcodeApi, messageApi, openApi, reportApi, tenantApi, workflowApi } from '@/api'
import { describeError } from '@/utils/error'
import { adminMenu } from '@/config/menu'
import { useAuthStore } from '@/stores/auth'

interface MetricCard {
  key: string
  label: string
  value: string
  hint: string
  tone: 'default' | 'success' | 'warning' | 'danger'
}

const auth = useAuthStore()
const loading = ref(false)
const cards = reactive<MetricCard[]>([
  { key: 'tenants', label: '租户总数', value: '—', hint: 'GET /admin/tenants', tone: 'default' },
  { key: 'users', label: '用户总数', value: '—', hint: 'GET /admin/users', tone: 'default' },
  { key: 'audits', label: '审计事件', value: '—', hint: 'GET /admin/audits', tone: 'default' },
  { key: 'unread', label: '未读消息', value: '—', hint: 'GET /messages', tone: 'warning' },
  { key: 'workflow', label: '运行中流程', value: '—', hint: 'GET /admin/workflow/monitor/stats', tone: 'default' },
  { key: 'lowcode', label: '低代码应用', value: '—', hint: 'GET /admin/lowcode/apps', tone: 'default' },
  { key: 'metrics', label: '指标定义', value: '—', hint: 'GET /admin/metrics', tone: 'default' },
  { key: 'aiCalls', label: 'AI 调用记录', value: '—', hint: 'GET /admin/ai/metering', tone: 'default' },
])

function find(key: string) {
  return cards.find((item) => item.key === key)!
}

function markFailed(key: string, error: unknown) {
  const card = find(key)
  card.value = '不可用'
  card.hint = describeError(error)
  card.tone = 'danger'
}

async function refresh() {
  loading.value = true
  const jobs: Array<Promise<void>> = [
    tenantApi.list({ page: 1, size: 1 }).then((r) => {
      find('tenants').value = String(r.total)
    }),
    iamApi.listUsers({ page: 1, size: 1 }).then((r) => {
      find('users').value = String(r.total)
    }),
    auditApi.list({ page: 1, size: 1 }).then((r) => {
      find('audits').value = String(r.total)
    }),
    messageApi.list({ page: 1, size: 1 }).then((r) => {
      find('unread').value = String(r.unread_count ?? 0)
    }),
    workflowApi.stats().then((r) => {
      find('workflow').value = String(r.running)
      find('workflow').hint = `完成 ${r.completed} · 超时 ${r.timeout} · 平均 ${r.avg_duration_minutes.toFixed(1)} 分钟`
    }),
    lowcodeApi.list({ page: 1, size: 1 }).then((r) => {
      find('lowcode').value = String(r.total)
    }),
    reportApi.listMetrics({ page: 1, size: 1 }).then((r) => {
      find('metrics').value = String(r.total)
    }),
    aiApi.metering({}).then((r) => {
      find('aiCalls').value = String(r.records?.length ?? 0)
    }),
    openApi.callLogs({ page: 1, size: 1 }).then((r) => {
      find('aiCalls').hint = `开放平台调用日志 ${r.total} 条`
    }),
  ]
  const results = await Promise.allSettled(jobs)
  results.forEach((result, index) => {
    if (result.status === 'rejected') {
      const keys = ['tenants', 'users', 'audits', 'unread', 'workflow', 'lowcode', 'metrics', 'aiCalls']
      const key = keys[index]
      if (key) markFailed(key, result.reason)
    }
  })
  loading.value = false
}

onMounted(refresh)
</script>

<template>
  <div>
    <PageHeader
      module="报表中心 · A-02"
      title="运营概览"
      description="按当前登录令牌租户聚合各中台真实接口数据；无权限或接口缺失的指标明确标记为不可用。"
    >
      <template #actions>
        <el-button :loading="loading" @click="refresh">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '后端未提供统一运营概览聚合接口，本页通过租户、IAM、审计、消息、流程、低代码、报表、AI、开放平台 9 个真实接口聚合。',
        '任一指标接口 403/404 时显示“不可用”，不使用模拟数据；租户套餐、账单、计量等运营接口当前后端未实现。',
        '当前账号权限码：' + (auth.permissions.join('，') || '空') + '。',
      ]"
    />

    <div class="fl-grid fl-grid--4 overview-cards">
      <StatCard
        v-for="card in cards"
        :key="card.key"
        :label="card.label"
        :value="card.value"
        :hint="card.hint"
        :tone="card.tone"
        :loading="loading"
      />
    </div>

    <div class="fl-card module-nav">
      <div class="fl-section-title">
        <span>13 个中台管理页面</span>
        <span class="fl-muted">按 UIUX 规范与 P3 范围收敛</span>
      </div>
      <div class="module-grid">
        <router-link v-for="item in adminMenu" :key="item.path" :to="item.path" class="module-card">
          <div class="module-card__head">
            <strong>{{ item.title }}</strong>
            <el-tag size="small" effect="plain">{{ item.module }}</el-tag>
          </div>
          <span class="fl-mono">{{ item.path }}</span>
          <span v-if="item.requiredPermission" class="module-card__perm">
            权限码：{{ item.requiredPermission }}
          </span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overview-cards {
  margin-bottom: var(--fl-space-lg);
}
.module-nav {
  margin-bottom: var(--fl-space-lg);
}
.module-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.module-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--fl-color-border);
  border-radius: var(--fl-radius-md);
  color: var(--fl-color-text-primary);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.module-card:hover {
  border-color: var(--fl-color-primary);
  box-shadow: var(--fl-shadow-md);
}
.module-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.module-card__perm {
  font-size: 11px;
  color: var(--fl-color-text-secondary);
}
@media (max-width: 1100px) {
  .module-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>
