<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { PortalShell } from '@fenglin/ui-portal'
import { gatewayApi } from '@fenglin/api-client'
import { APP_TITLE, formatDateTime } from '@fenglin/shared'

const status = ref('检测中')
const checkedAt = ref('')

async function check() {
  try {
    const health = await gatewayApi.health()
    status.value = health.status === 'UP' ? '网关正常' : `网关状态 ${health.status}`
  } catch {
    status.value = '网关不可达（18080）'
  }
  checkedAt.value = formatDateTime(new Date().toISOString())
}

onMounted(check)
</script>

<template>
  <PortalShell :title="APP_TITLE" subtitle="统一用户门户（占位骨架）">
    <template #nav>
      <span class="fl-portal-badge">{{ status }}</span>
    </template>

    <div class="fl-portal-card">
      <h2>统一用户门户</h2>
      <p>
        本应用为 pnpm workspace 中的门户端骨架，端口 5174，已复用设计令牌、门户外壳与统一请求层。
        业务门户页面（工作台、我的应用、消息、待办）尚未纳入本期 13 个中台管理页面范围。
      </p>
      <p v-if="checkedAt">网关健康检测时间：{{ checkedAt }}</p>
      <a class="fl-portal-link" href="http://localhost:5173" target="_blank" rel="noopener">进入中台管理台（5173）</a>
    </div>

    <div class="fl-portal-card">
      <h2>已实现能力</h2>
      <p>设计令牌（UIUX §1 色板与字号）、PortalShell 布局、统一 API 客户端与网关健康检查。</p>
      <p>中台 13 个管理页面位于 admin-ui：运营概览、租户、IAM、流程、消息、文件、审计、搜索、开放平台、报表、低代码、AI、网关治理。</p>
    </div>
  </PortalShell>
</template>
