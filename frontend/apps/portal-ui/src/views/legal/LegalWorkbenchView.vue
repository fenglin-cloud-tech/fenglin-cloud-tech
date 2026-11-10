<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const router = useRouter()

const cards = reactive([
  { key: 'contracts', title: '我的合同', desc: '仅本人负责的合同', path: '/my-contracts', value: 0 as number | string, error: '' },
  { key: 'sign', title: '待签署任务', desc: '仅指派给本人的签署记录', path: '/my-sign-tasks', value: 0 as number | string, error: '' },
  { key: 'seal', title: '我的用印申请', desc: '仅本人提交的用印申请', path: '/my-seal-applications', value: 0 as number | string, error: '' },
  { key: 'release', title: '我的放行申请', desc: '仅本人提交的数据放行', path: '/my-data-release', value: 0 as number | string, error: '' },
])

const loading = reactive({ value: true })

const gapItems = [
  '工作台按真实接口取本人数据：合同 /contracts、签署 /sign-tasks、用印 /seal-applications、放行 /dlp/releases 都由后端强制本人范围，前端不传他人 ID。',
  '任一接口缺少对应权限时，卡片显示“未授权”而不是 0，避免把权限不足误读为没有数据。',
]

async function load() {
  loading.value = true
  const [contracts, sign, seal, release] = await Promise.allSettled([
    legalApi.contracts.list({ page: 1, size: 1 }),
    legalApi.sign.tasks({ page: 1, size: 1 }),
    legalApi.sealApplications.list({ page: 1, size: 1 }),
    legalApi.dlp.releases({ page: 1, size: 1 }),
  ])
  const apply = (card: (typeof cards)[number], result: PromiseSettledResult<{ total: number }>) => {
    if (result.status === 'fulfilled') {
      card.value = result.value.total
      card.error = ''
    } else {
      card.value = '未授权'
      card.error = describeError(result.reason)
    }
  }
  apply(cards[0], contracts)
  apply(cards[1], sign)
  apply(cards[2], seal)
  apply(cards[3], release)
  loading.value = false
}

function go(path: string) {
  router.push(path)
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="法务工作台"
      description="汇总本人合同、签署、用印与放行申请；全部数据来自 /api/legal/v1 真实接口。"
    >
      <template #actions>
        <el-button :loading="loading.value" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-row :gutter="12">
      <el-col v-for="card in cards" :key="card.key" :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi" @click="go(card.path)">
          <div class="kpi__title">{{ card.title }}</div>
          <div class="kpi__value">{{ card.value }}</div>
          <div class="kpi__desc">{{ card.desc }}</div>
          <div v-if="card.error" class="kpi__error">{{ card.error }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>快捷入口</template>
      <el-space wrap>
        <el-button @click="go('/my-contracts')">我的合同</el-button>
        <el-button @click="go('/my-sign-tasks')">我的签署任务</el-button>
        <el-button @click="go('/my-seal-applications')">用印申请</el-button>
        <el-button @click="go('/my-data-release')">数据放行申请</el-button>
        <el-button @click="go('/my-evidence')">我的存证</el-button>
      </el-space>
    </el-card>
  </div>
</template>

<style scoped>
.kpi {
  cursor: pointer;
  margin-bottom: 12px;
}
.kpi__title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.kpi__value {
  margin: 8px 0 4px;
  font-size: 26px;
  font-weight: 600;
}
.kpi__desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.kpi__error {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-color-danger);
  word-break: break-all;
}
</style>
