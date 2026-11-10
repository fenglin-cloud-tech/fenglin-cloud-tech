<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { intelligenceApi, type AiRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const router = useRouter()

const canChat = auth.can('ai:chat:use')
const canBi = auth.can('ai:bi:view')

const conversations = ref<AiRecord[]>([])
const conversationTotal = ref(0)
const dashboards = ref<AiRecord[]>([])
const dashboardTotal = ref(0)
const tasks = ref<AiRecord[]>([])
const taskTotal = ref(0)
const loading = ref(false)
const error = ref('')

const gapItems = [
  '工作台是聚合视图，只读取会话、看板与洞察任务，不产生新数据。',
  '平台未提供个性化推荐 / 待办排序接口，三块内容分别取各自模块最新数据。',
  '各区块按权限独立请求：无对应权限时不会发起请求，并显示权限提示。',
]

async function loadConversations(): Promise<string | null> {
  if (!canChat) return null
  try {
    const result = await intelligenceApi.copilot.conversations({ page: 1, size: 5 })
    conversations.value = result.records
    conversationTotal.value = result.total
    return null
  } catch (err) {
    return describeError(err)
  }
}

async function loadDashboards(): Promise<string | null> {
  if (!canBi) return null
  try {
    const result = await intelligenceApi.dashboards.list({ page: 1, size: 6 })
    dashboards.value = result.records
    dashboardTotal.value = result.total
    return null
  } catch (err) {
    return describeError(err)
  }
}

async function loadTasks(): Promise<string | null> {
  if (!canBi) return null
  try {
    const result = await intelligenceApi.insight.tasks({ page: 1, size: 5 })
    tasks.value = result.records
    taskTotal.value = result.total
    return null
  } catch (err) {
    return describeError(err)
  }
}

async function load() {
  loading.value = true
  error.value = ''
  const failures = await Promise.all([loadConversations(), loadDashboards(), loadTasks()])
  error.value = failures.filter(Boolean).join('；')
  loading.value = false
}

function goTo(path: string) {
  router.push(path)
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="丰麟云智"
      title="智能工作台"
      description="聚合最近会话、数据看板与洞察任务；数据来自 /api/intelligence/v1 下的真实接口。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never" class="workbench-card">
          <template #header>
            <div class="card-head">
              <span>最近会话</span>
              <div class="card-head__right">
                <span class="fl-muted">共 {{ conversationTotal }} 条</span>
                <el-button link type="primary" @click="goTo('/ai-copilot')">进入 Copilot</el-button>
              </div>
            </div>
          </template>
          <el-alert
            v-if="!canChat"
            type="info"
            :closable="false"
            show-icon
            title="缺少权限 ai:chat:use，未请求会话数据。"
          />
          <template v-else>
            <div v-loading="loading">
              <div v-for="item in conversations" :key="String(item.id)" class="row-item">
                <div class="row-item__main">
                  <span class="row-item__title">{{ item.title || '未命名会话' }}</span>
                  <span class="fl-muted">{{ item.type || '-' }} · {{ item.message_count ?? 0 }} 条消息</span>
                </div>
                <span class="fl-muted">{{ item.updated_at || item.created_at || '-' }}</span>
              </div>
              <el-empty v-if="conversations.length === 0" description="暂无会话" :image-size="60" />
            </div>
          </template>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" class="workbench-card">
          <template #header>
            <div class="card-head">
              <span>数据看板</span>
              <div class="card-head__right">
                <span class="fl-muted">共 {{ dashboardTotal }} 个</span>
                <el-button link type="primary" @click="goTo('/ai-dashboards')">查看看板</el-button>
              </div>
            </div>
          </template>
          <el-alert
            v-if="!canBi"
            type="info"
            :closable="false"
            show-icon
            title="缺少权限 ai:bi:view，未请求看板数据。"
          />
          <template v-else>
            <div v-loading="loading">
              <div v-for="item in dashboards" :key="String(item.id)" class="row-item">
                <div class="row-item__main">
                  <span class="row-item__title">{{ item.name || '未命名看板' }}</span>
                  <span class="fl-muted">{{ item.code || '-' }} · {{ item.status || '-' }}</span>
                </div>
                <span class="fl-muted">{{ item.created_at || '-' }}</span>
              </div>
              <el-empty v-if="dashboards.length === 0" description="暂无看板" :image-size="60" />
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="workbench-card">
      <template #header>
        <div class="card-head">
          <span>洞察任务</span>
          <div class="card-head__right">
            <span class="fl-muted">共 {{ taskTotal }} 个</span>
            <el-button link type="primary" @click="goTo('/ai-insights')">查看洞察</el-button>
          </div>
        </div>
      </template>
      <el-alert
        v-if="!canBi"
        type="info"
        :closable="false"
        show-icon
        title="缺少权限 ai:bi:view，未请求洞察任务。"
      />
      <template v-else>
        <el-table v-loading="loading" :data="tasks" border stripe size="small">
          <el-table-column prop="name" label="任务名称" min-width="180" />
          <el-table-column prop="target_type" label="对象" width="110" />
          <el-table-column prop="target_id" label="对象ID" width="140" />
          <el-table-column prop="trigger_type" label="触发方式" width="120" />
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="last_run_at" label="最近运行" width="170">
            <template #default="{ row }">{{ row.last_run_at || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="tasks.length === 0" description="暂无洞察任务" :image-size="60" />
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.workbench-card {
  margin-bottom: 12px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-head__right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.row-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--fl-color-border);
}
.row-item:last-child {
  border-bottom: none;
}
.row-item__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.row-item__title {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fl-muted {
  color: var(--fl-color-text-secondary);
  font-size: 12px;
}
</style>
