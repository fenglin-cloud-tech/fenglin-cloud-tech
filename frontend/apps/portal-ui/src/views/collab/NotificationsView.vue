<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { collabApi, type CollabNotification } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<CollabNotification[]>([])
const total = ref(0)
const unread = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ unreadOnly: false, page: 1, size: 10 })
const selected = ref<CollabNotification[]>([])

const pageUnread = computed(() => rows.value.filter((item) => !item.read).length)

const gapItems = [
  '支持全部已读、单条删除与批量删除（批量上限按接口 500 条）。',
  '通知携带 biz_type/biz_id，可按业务类型跳转到对应模块；未覆盖的类型只展示文本。',
  '未读数为独立统计接口，不再依赖当前页。',
]

const bizRoutes: Record<string, string> = {
  APPROVAL: '/approvals',
  COLLAB_APPROVAL: '/approvals',
  MEETING: '/meetings',
  COLLAB_MEETING: '/meetings',
  OKR: '/my-okr',
  COLLAB_OKR: '/my-okr',
  DOC: '/docs',
  COLLAB_DOC: '/docs',
}

function formatTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [result, count] = await Promise.all([
      collabApi.workbench.notifications({
        unreadOnly: query.unreadOnly || undefined,
        page: query.page,
        size: query.size,
      }),
      collabApi.workbench.unreadCount(),
    ])
    rows.value = result.records
    total.value = result.total
    unread.value = count.unread
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function onUnreadChange() {
  query.page = 1
  void load()
}

async function markRead(row: CollabNotification) {
  try {
    await collabApi.workbench.readNotification(row.notification_id)
    row.read = true
    unread.value = Math.max(0, unread.value - 1)
    ElMessage.success('已标记为已读')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function markAllRead() {
  try {
    const result = await collabApi.workbench.readAllNotifications()
    ElMessage.success(`已标记 ${result.updated} 条为已读`)
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function removeOne(row: CollabNotification) {
  try {
    await ElMessageBox.confirm(`确认删除通知「${row.title}」？`, '删除通知', { type: 'warning' })
  } catch {
    return
  }
  try {
    await collabApi.workbench.deleteNotification(row.notification_id)
    ElMessage.success('通知已删除')
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function removeSelected() {
  if (!selected.value.length) {
    ElMessage.warning('请先勾选通知')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selected.value.length} 条通知？`, '批量删除', { type: 'warning' })
  } catch {
    return
  }
  try {
    const result = await collabApi.workbench.batchDeleteNotifications(
      selected.value.map((item) => item.notification_id),
    )
    ElMessage.success(`已删除 ${result.deleted} 条`)
    selected.value = []
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openBiz(row: CollabNotification) {
  const route = row.biz_type ? bizRoutes[row.biz_type] : undefined
  if (!route) {
    ElMessage.info('该通知没有可跳转的业务页面')
    return
  }
  window.location.hash = route
}

function rowClassName({ row }: { row: CollabNotification; rowIndex: number }) {
  return row.read ? '' : 'unread-row'
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="通知中心" description="按分类查看系统通知；支持全部已读、删除与按业务跳转。">
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" plain @click="markAllRead">全部已读</el-button>
        <el-button type="danger" plain :disabled="!selected.length" @click="removeSelected">批量删除</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-switch v-model="query.unreadOnly" active-text="仅看未读" @change="onUnreadChange" />
        <span class="fl-muted">未读 {{ unread }} 条 · 当前页未读 {{ pageUnread }} 条 · 共 {{ total }} 条</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table
        :data="rows"
        v-loading="loading"
        border
        stripe
        :row-class-name="rowClassName"
        @selection-change="(value: CollabNotification[]) => (selected = value)"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'unread-text': !row.read }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.read ? 'info' : 'danger'" size="small">{{ row.read ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.read" link type="primary" @click="markRead(row)">标记已读</el-button>
            <el-button v-if="row.biz_type && bizRoutes[row.biz_type]" link @click="openBiz(row)">查看</el-button>
            <el-button link type="danger" @click="removeOne(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="load"
      />
    </el-card>
  </div>
</template>

<style scoped>
.unread-text {
  font-weight: 600;
}
:deep(.unread-row) {
  font-weight: 600;
}
</style>
