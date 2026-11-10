<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentId,
  type TalentPerfCycle,
  type TalentPerfEvaluation,
  type TalentPerfGoal,
  type TalentPerfResult,
} from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const cycles = ref<TalentPerfCycle[]>([])
const selectedCycleId = ref<TalentId | ''>('')
const goals = ref<TalentPerfGoal[]>([])
const evaluations = ref<TalentPerfEvaluation[]>([])
const results = ref<TalentPerfResult[]>([])
const loading = ref(false)
const error = ref('')

const gapItems = [
  '后端暂无员工自助绩效接口（没有 /performance/my）：周期、目标、评估与结果均为管理端接口，需要 talent:admin:performance:list。',
  '页面按登录用户 ID 在目标/评估/结果列表中过滤本人数据；普通员工账号会返回真实 403，管理员账号可看到真实数据。',
  '目标权重合计、审批流转由管理端维护；员工端只读展示，不提供提交目标或自评入口。',
]

const cycleStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  GOAL_SETTING: '目标制定',
  EVALUATING: '评估中',
  CLOSED: '已关闭',
}

const cycleTypeLabels: Record<string, string> = {
  ANNUAL: '年度',
  HALF_YEAR: '半年',
  QUARTER: '季度',
  MONTH: '月度',
}

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审批',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CALCULATED: '已计算',
  PUBLISHED: '已发布',
}

const selectedCycle = computed(() => cycles.value.find((item) => String(item.id) === String(selectedCycleId.value)))

function label(map: Record<string, string>, value?: string) {
  return value ? map[value] ?? value : '-'
}

function statusTag(value?: string) {
  if (['APPROVED', 'PUBLISHED'].includes(value ?? '')) return 'success'
  if (['REJECTED'].includes(value ?? '')) return 'danger'
  if (['SUBMITTED', 'APPROVING', 'CALCULATED'].includes(value ?? '')) return 'warning'
  return 'info'
}

async function loadCycles() {
  if (!auth.userId) {
    error.value = '当前令牌未返回用户 ID，无法定位本人绩效'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const page = await talentApi.performance.cycles({ page: 1, size: 50 })
    cycles.value = page.records
    if (!selectedCycleId.value && page.records.length) {
      selectedCycleId.value = page.records[0].id
    }
    await loadDetail()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadDetail() {
  if (!selectedCycleId.value || !auth.userId) {
    goals.value = []
    evaluations.value = []
    results.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const cycleId = selectedCycleId.value
    const [goalPage, evaluationPage, resultPage] = await Promise.all([
      talentApi.performance.goals(cycleId, { page: 1, size: 200 }),
      talentApi.performance.evaluations(cycleId, { page: 1, size: 200 }),
      talentApi.performance.results(cycleId, { page: 1, size: 200 }),
    ])
    const mine = (employeeId: TalentId) => String(employeeId) === String(auth.userId)
    goals.value = goalPage.records.filter((item) => mine(item.employee_id))
    evaluations.value = evaluationPage.records.filter((item) => mine(item.employee_id))
    results.value = resultPage.records.filter((item) => mine(item.employee_id))
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(loadCycles)
</script>

<template>
  <div>
    <PageHeader module="云才人才" title="我的绩效" description="按绩效周期查看本人目标、评估与最终结果。">
      <template #actions>
        <el-select
          v-model="selectedCycleId"
          placeholder="选择绩效周期"
          style="width: 240px"
          @change="loadDetail"
        >
          <el-option
            v-for="cycle in cycles"
            :key="String(cycle.id)"
            :label="`${cycle.cycle_name}（${cycle.cycle_code}）`"
            :value="cycle.id"
          />
        </el-select>
        <el-button :loading="loading" @click="loadCycles">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card v-if="selectedCycle" shadow="never" style="margin-bottom: 16px">
      <div class="cycle-summary">
        <div>
          <div class="cycle-title">{{ selectedCycle.cycle_name }}</div>
          <div class="fl-muted">
            {{ label(cycleTypeLabels, selectedCycle.cycle_type) }} · {{ selectedCycle.start_date }} ~
            {{ selectedCycle.end_date }}
          </div>
        </div>
        <el-tag :type="statusTag(selectedCycle.cycle_status)" size="small">
          {{ label(cycleStatusLabels, selectedCycle.cycle_status) }}
        </el-tag>
      </div>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-tabs>
        <el-tab-pane :label="`我的目标（${goals.length}）`">
          <el-table :data="goals" border stripe size="small">
            <el-table-column prop="goal_name" label="目标" min-width="200" show-overflow-tooltip />
            <el-table-column label="权重" width="80">
              <template #default="{ row }">{{ row.weight ?? '-' }}%</template>
            </el-table-column>
            <el-table-column label="衡量方式" width="100">
              <template #default="{ row }">{{ row.measure_type === 'QUANT' ? '定量' : '定性' }}</template>
            </el-table-column>
            <el-table-column prop="target_value" label="目标值" width="130" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.goal_status)" size="small">{{ label(statusLabels, row.goal_status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approve_opinion" label="审批意见" min-width="140" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`我的评估（${evaluations.length}）`">
          <el-table :data="evaluations" border stripe size="small">
            <el-table-column label="评估类型" width="120">
              <template #default="{ row }">
                {{ row.review_type === 'SELF' ? '自评' : row.review_type === 'CROSS' ? '跨部门' : '上级评估' }}
              </template>
            </el-table-column>
            <el-table-column prop="score" label="分数" width="100" />
            <el-table-column prop="grade" label="等级" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.eval_status)" size="small">{{ label(statusLabels, row.eval_status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approve_opinion" label="审批意见" min-width="160" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`我的结果（${results.length}）`">
          <el-table :data="results" border stripe size="small">
            <el-table-column prop="rank_no" label="排名" width="90" />
            <el-table-column prop="final_score" label="最终得分" width="120" />
            <el-table-column prop="grade" label="等级" width="100" />
            <el-table-column prop="bonus_coefficient" label="奖金系数" width="120" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.result_status)" size="small">
                  {{ label(statusLabels, row.result_status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="published_at" label="发布时间" width="180" />
          </el-table>
          <el-empty v-if="!results.length" description="结果尚未计算或发布" :image-size="64" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.cycle-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.cycle-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}
</style>
