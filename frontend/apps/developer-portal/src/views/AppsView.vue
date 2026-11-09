<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { openApiPortal, type PortalAppView } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const rows = ref<PortalAppView[]>([])
const loading = ref(false)
const error = ref('')

const canView = computed(() => auth.can('biz:openapi:portal:view'))
const totalCalls = computed(() => rows.value.reduce((sum, row) => sum + (row.call_today ?? 0), 0))
const activeCount = computed(() => rows.value.filter((row) => row.status === 'ACTIVE').length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await openApiPortal.apps()
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h2>我的应用</h2>
        <p>数据来自 GET /openapi/portal/apps（权限 biz:openapi:portal:view）。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="!canView"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号缺少 biz:openapi:portal:view"
      description="页面会调用真实接口并展示后端返回的 403 错误，不展示伪造数据。请联系租户管理员授权。"
      style="margin-bottom: 16px"
    />
    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 16px" />

    <div class="summary">
      <span>应用数：<strong>{{ rows.length }}</strong></span>
      <span>启用：<strong>{{ activeCount }}</strong></span>
      <span>今日调用：<strong>{{ totalCalls }}</strong></span>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe>
      <el-table-column prop="app_id" label="应用 ID" width="100" />
      <el-table-column prop="code" label="应用编码" min-width="180" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="call_today" label="今日调用" width="120" />
      <el-table-column label="日配额" width="140">
        <template #default="{ row }">{{ row.quota_daily ?? '未配置' }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.page-head h2 {
  margin: 0 0 4px;
  font-size: 20px;
}
.page-head p {
  margin: 0;
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
.summary {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid var(--fl-color-border);
  border-radius: var(--fl-radius-md);
  font-size: 13px;
  color: var(--fl-color-text-secondary);
}
.summary strong {
  color: var(--fl-color-text-primary);
  font-size: 16px;
}
</style>
