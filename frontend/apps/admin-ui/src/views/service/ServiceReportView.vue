<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH } from '@fenglin/ui-admin'
import { serviceApi, type ServiceRecord } from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const error = ref('')
const loading = ref(false)

const ticketReport = ref<ServiceRecord | null>(null)
const slaReport = ref<ServiceRecord | null>(null)
const workload = ref<ServiceRecord[]>([])
const csatReport = ref<ServiceRecord | null>(null)
const rpaReport = ref<ServiceRecord | null>(null)
const utilization = ref<ServiceRecord[]>([])

const canServiceReport = computed(() => auth.can('service:report:view'))
const canCsat = computed(() => auth.can('service:csat:view'))
const canRpa = computed(() => auth.can('service:rpa:report:view'))

const gapItems = [
  '报表接口均为租户级聚合值，没有时间区间参数，本页不能按日期筛选。',
  '满意度只提供汇总（总分/均分/好评/差评），没有明细列表接口。',
  '坐席负荷按 assignee_id 聚合，前端不解析姓名，避免伪造人员数据。',
]

const slaRate = computed(() => {
  if (!slaReport.value) return '-'
  const met = Number(slaReport.value.met ?? 0)
  const breached = Number(slaReport.value.breached ?? 0)
  const base = met + breached
  if (base === 0) return '-'
  return `${((met / base) * 100).toFixed(1)}%`
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (canServiceReport.value) {
      const [tickets, sla, load] = await Promise.all([
        serviceApi.reports.tickets(),
        serviceApi.reports.sla(),
        serviceApi.reports.workload(),
      ])
      ticketReport.value = tickets
      slaReport.value = sla
      workload.value = load
    }
    if (canCsat.value) csatReport.value = await serviceApi.reports.csat()
    if (canRpa.value) {
      const [summary, robotLoad] = await Promise.all([
        serviceApi.reports.rpa(),
        serviceApi.reports.utilization(),
      ])
      rpaReport.value = summary
      utilization.value = robotLoad
    }
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云服 Service"
      title="服务运营报表"
      description="工单、SLA、坐席负荷、满意度与 RPA 汇总；数据来自 /api/service/v1/reports。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />
    <el-alert
      v-if="!canServiceReport && !canCsat && !canRpa"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号没有报表类权限点，页面不展示数据。"
    />

    <el-row :gutter="12">
      <el-col v-if="canServiceReport" :span="12">
        <el-card shadow="never">
          <template #header><span>工单概览</span></template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="工单总量">{{ ticketReport?.total ?? '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider content-position="left">按状态</el-divider>
          <el-table :data="(ticketReport?.by_status as ServiceRecord[]) ?? []" size="small" border max-height="200">
            <el-table-column prop="status" label="状态" min-width="120" />
            <el-table-column prop="cnt" label="数量" width="90" />
          </el-table>
          <el-divider content-position="left">按优先级</el-divider>
          <el-table :data="(ticketReport?.by_priority as ServiceRecord[]) ?? []" size="small" border max-height="200">
            <el-table-column prop="priority" label="优先级" min-width="120" />
            <el-table-column prop="cnt" label="数量" width="90" />
          </el-table>
          <el-divider content-position="left">按类型</el-divider>
          <el-table :data="(ticketReport?.by_type as ServiceRecord[]) ?? []" size="small" border max-height="200">
            <el-table-column prop="ticket_type" label="类型" min-width="120" />
            <el-table-column prop="cnt" label="数量" width="90" />
          </el-table>
        </el-card>
      </el-col>

      <el-col v-if="canServiceReport" :span="12">
        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header><span>SLA 达成</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="工单总量">{{ slaReport?.total ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="达成率">{{ slaRate }}</el-descriptions-item>
            <el-descriptions-item label="达成">{{ slaReport?.met ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="超时">{{ slaReport?.breached ?? '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never">
          <template #header><span>坐席负荷</span></template>
          <el-table :data="workload" border size="small" max-height="360">
            <el-table-column prop="assignee_id" label="处理人ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
            <el-table-column prop="total" label="总量" width="90" />
            <el-table-column prop="resolved" label="已解决" width="90" />
            <el-table-column prop="breached" label="超时" width="90" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col v-if="canCsat" :span="12">
        <el-card shadow="never">
          <template #header><span>满意度汇总</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="回收数">{{ csatReport?.total ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="平均分">{{ csatReport?.avg_score ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="好评(≥4)">{{ csatReport?.positive ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="差评(≤2)">{{ csatReport?.negative ?? '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col v-if="canRpa" :span="12">
        <el-card shadow="never">
          <template #header><span>RPA 任务汇总</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="任务总量">{{ rpaReport?.total ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="平均耗时(s)">{{ rpaReport?.avg_duration_sec ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="成功">{{ rpaReport?.succeeded ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="失败">{{ rpaReport?.failed ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="需人工">{{ rpaReport?.manual_required ?? '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="canRpa" shadow="never" style="margin-top: 12px">
      <template #header><span>机器人利用率</span></template>
      <el-table :data="utilization" border size="small">
        <el-table-column prop="robot_code" label="机器人编码" width="150" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="task_count" label="任务数" width="100" />
        <el-table-column prop="succeeded" label="成功数" width="100" />
        <el-table-column prop="busy_seconds" label="占用秒数" width="110" />
      </el-table>
    </el-card>
  </div>
</template>
