<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { StatCard } from '@fenglin/ui-admin'
import { crmApi } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const counts = reactive({
  leads: '-' as number | string,
  customers: '-' as number | string,
  opportunities: '-' as number | string,
  quotations: '-' as number | string,
  contracts: '-' as number | string,
  reminders: '-' as number | string,
})
const gaps = ref<string[]>([])

async function load() {
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    crmApi.leads.my({ page: 1, size: 1 }),
    crmApi.customers.my({ page: 1, size: 1 }),
    crmApi.opportunities.my({ page: 1, size: 1 }),
    crmApi.quotations.my({ page: 1, size: 1 }),
    crmApi.contracts.my({ page: 1, size: 1 }),
    crmApi.payments.myReminders({ upcoming_days: 30, page: 1, size: 1 }),
  ])
  const keys = ['leads', 'customers', 'opportunities', 'quotations', 'contracts', 'reminders'] as const
  const failures: string[] = []
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      counts[keys[index]] = result.value.total
    } else {
      counts[keys[index]] = '-'
      failures.push(`${keys[index]}：${describeError(result.reason)}`)
    }
  })
  if (failures.length) error.value = failures.join('；')
  gaps.value = failures
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div>
    <el-alert
      v-if="error"
      type="warning"
      :closable="false"
      show-icon
      title="部分统计加载失败（后端真实返回，未使用占位数据）"
      style="margin-bottom: 16px"
    >
      <div v-for="(item, i) in gaps" :key="i">{{ item }}</div>
    </el-alert>

    <el-card shadow="never" class="welcome">
      <div>
        <h2>你好，{{ auth.displayName }}</h2>
        <p>这里汇总你名下的线索、客户、商机、报价合同与近 30 天回款提醒，数据全部来自 CRM 服务实时接口。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </el-card>

    <div class="stat-grid">
      <StatCard label="我的线索" :value="counts.leads" hint="待跟进与已分配" :loading="loading" />
      <StatCard label="我的客户" :value="counts.customers" hint="负责人为当前账号" :loading="loading" />
      <StatCard label="我的商机" :value="counts.opportunities" hint="进行中与已赢单" :loading="loading" />
      <StatCard label="我的报价" :value="counts.quotations" hint="含审批中" :loading="loading" />
      <StatCard label="我的合同" :value="counts.contracts" hint="按合同负责人过滤" :loading="loading" />
      <StatCard label="回款提醒" :value="counts.reminders" hint="近 30 天到期或已逾期" tone="warning" :loading="loading" />
    </div>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><strong>当前登录上下文</strong></template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="账号">{{ auth.user?.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ auth.user?.real_name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户 ID">{{ auth.userId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="租户">{{ auth.user?.tenant_code || auth.user?.tenant_id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ (auth.user?.roles || []).join('，') || '-' }}</el-descriptions-item>
        <el-descriptions-item label="权限数">{{ auth.permissions.length }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.welcome {
  display: flex;
  margin-bottom: 16px;
}
.welcome :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
}
.welcome h2 {
  margin: 0 0 6px;
  font-size: 20px;
}
.welcome p {
  margin: 0;
  color: var(--fl-color-text-secondary);
  font-size: 13px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
</style>
