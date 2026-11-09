<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { collabApi, type CollabNotification } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<CollabNotification[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ unreadOnly: false, page: 1, size: 10 })

const pageUnread = computed(() => rows.value.filter((item) => !item.read).length)

const gapItems = [
  '没有全部已读、删除、批量操作接口，只能逐条标记已读。',
  '通知内容为纯文本，没有跳转链接/业务对象字段。',
  '通知分页总数来自接口 total，未读数没有独立统计接口。',
]

function formatTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await collabApi.workbench.notifications({
      unreadOnly: query.unreadOnly || undefined,
      page: query.page,
      size: query.size,
    })
    rows.value = result.records
    total.value = result.total
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
    ElMessage.success('已标记为已读')
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function rowClassName({ row }: { row: CollabNotification; rowIndex: number }) {
  return row.read ? '' : 'unread-row'
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="通知中心" description="按分类查看系统通知；未读可逐条标记。">
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-switch v-model="query.unreadOnly" active-text="仅看未读" @change="onUnreadChange" />
        <span class="fl-muted">当前页未读 {{ pageUnread }} 条 · 共 {{ total }} 条</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe :row-class-name="rowClassName">
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
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.read" link type="primary" @click="markRead(row)">标记已读</el-button>
            <el-text v-else type="info">已读</el-text>
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
