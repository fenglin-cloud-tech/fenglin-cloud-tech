<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { rndApi, type RndTask } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const tasks = ref<RndTask[]>([])
const loading = ref(false)
const error = ref('')
const statusFilter = ref('')

const statusLabels: Record<string, string> = {
  todo: '待处理',
  doing: '进行中',
  done: '已完成',
  closed: '已关闭',
}

const transitions: Record<string, string[]> = {
  todo: ['doing', 'done'],
  doing: ['done', 'closed'],
  done: ['closed'],
}

const gapItems = [
  '缺“我的需求”接口：需求列表不支持按负责人过滤，员工端不提供需求页。',
]

const filteredTasks = computed(() =>
  statusFilter.value ? tasks.value.filter((item) => item.status === statusFilter.value) : tasks.value,
)

const openCount = computed(() => tasks.value.filter((item) => item.status !== 'done' && item.status !== 'closed').length)

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusType(value?: string) {
  if (value === 'done' || value === 'closed') return 'success'
  if (value === 'doing') return 'warning'
  return 'info'
}

async function loadTasks() {
  loading.value = true
  error.value = ''
  try {
    tasks.value = await rndApi.tasks.my({ page: 1, size: 200 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function transition(row: RndTask, target: string) {
  try {
    await rndApi.tasks.transition(row.id, target)
    ElMessage.success(`任务已流转至 ${statusLabel(target)}`)
    await loadTasks()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(loadTasks)
</script>

<template>
  <div>
    <PageHeader
      module="云研 R&D"
      title="我的任务"
      description="按当前登录用户汇总所指派的任务；数据来自 /api/rnd/v1/tasks/my。"
    >
      <template #actions>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="code" />
        </el-select>
        <el-button :loading="loading" @click="loadTasks">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-tag type="warning" effect="plain">未完成 {{ openCount }}</el-tag>
        <span class="fl-muted">共 {{ tasks.length }} 条任务</span>
      </div>
      <el-table :data="filteredTasks" v-loading="loading" border stripe max-height="560">
        <el-table-column prop="product_name" label="产品" width="160" />
        <el-table-column prop="task_no" label="任务编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="task_type" label="类型" width="100" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="estimate_hours" label="预估" width="90" />
        <el-table-column prop="due_date" label="截止" width="120" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-dropdown v-if="transitions[row.status]" trigger="click" @command="(target: string) => transition(row, target)">
              <el-button v-perm="'biz:devops:task:transition'" link type="primary">流转</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="target in transitions[row.status]" :key="target" :command="target">
                    {{ statusLabel(target) }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
