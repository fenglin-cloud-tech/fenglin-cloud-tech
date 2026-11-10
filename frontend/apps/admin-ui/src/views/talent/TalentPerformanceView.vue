<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentEmployeeListItem,
  type TalentId,
  type TalentPerfCycle,
  type TalentPerfEvaluation,
  type TalentPerfGoal,
  type TalentPerfResult,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const loading = ref(false)
const error = ref('')
const cycles = ref<TalentPerfCycle[]>([])
const selectedCycleId = ref<TalentId | ''>('')
const activeTab = ref<'goals' | 'evaluations' | 'results'>('goals')

const employees = ref<TalentEmployeeListItem[]>([])

const goals = ref<TalentPerfGoal[]>([])
const evaluations = ref<TalentPerfEvaluation[]>([])
const results = ref<TalentPerfResult[]>([])
const tabLoading = ref(false)
const tabError = ref('')

const cycleVisible = ref(false)
const cycleSaving = ref(false)
const cycleError = ref('')
const cycleForm = reactive({
  cycle_code: '',
  cycle_name: '',
  cycle_type: 'QUARTER',
  start_date: '',
  end_date: '',
})

const goalVisible = ref(false)
const goalSaving = ref(false)
const goalError = ref('')
const goalForm = reactive({
  employee_id: '' as TalentId | '',
  goal_name: '',
  goal_desc: '',
  weight: 30,
  measure_type: 'QUANT',
  target_value: '',
})

const evalVisible = ref(false)
const evalSaving = ref(false)
const evalError = ref('')
const evalForm = reactive({
  employee_id: '' as TalentId | '',
  review_type: 'MANAGER',
  score: 80,
  summary: '',
})

const approvalVisible = ref(false)
const approvalSaving = ref(false)
const approvalError = ref('')
const approvalTarget = ref<{ kind: 'goal' | 'evaluation'; id: TalentId; label: string } | null>(null)
const approvalForm = reactive({ approved: true, opinion: '' })

const cycleTypeOptions = [
  { value: 'ANNUAL', label: '年度' },
  { value: 'HALF_YEAR', label: '半年' },
  { value: 'QUARTER', label: '季度' },
  { value: 'MONTH', label: '月度' },
]
const measureTypeOptions = [
  { value: 'QUANT', label: '定量' },
  { value: 'QUAL', label: '定性' },
]
const reviewTypeOptions = [
  { value: 'SELF', label: '自评' },
  { value: 'MANAGER', label: '上级评估' },
  { value: 'CROSS', label: '跨部门评估' },
]

const selectedCycle = computed(() => cycles.value.find((item) => String(item.id) === String(selectedCycleId.value)))

const cycleStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  GOAL_SETTING: '目标制定',
  EVALUATING: '评估中',
  CLOSED: '已关闭',
}

function cycleTypeLabel(value?: string) {
  return cycleTypeOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function cycleStatusLabel(value?: string) {
  return cycleStatusLabels[value ?? ''] ?? value ?? '-'
}

function statusTag(value?: string) {
  if (['APPROVED', 'PUBLISHED', 'CLOSED'].includes(value ?? '')) return 'success'
  if (['REJECTED', 'INVALID'].includes(value ?? '')) return 'danger'
  if (['SUBMITTED', 'APPROVING', 'EVALUATING'].includes(value ?? '')) return 'warning'
  return 'info'
}

function measureLabel(value?: string) {
  return measureTypeOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function reviewLabel(value?: string) {
  return reviewTypeOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

async function loadCycles() {
  loading.value = true
  error.value = ''
  try {
    const page = await talentApi.performance.cycles({ page: 1, size: 50 })
    cycles.value = page.records
    if (!selectedCycleId.value && page.records.length) {
      selectedCycleId.value = page.records[0].id
    }
    await loadTab()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadEmployees() {
  try {
    const page = await talentApi.employees.list({ page: 1, size: 200 })
    employees.value = page.records
  } catch {
    employees.value = []
  }
}

async function loadTab() {
  if (!selectedCycleId.value) {
    goals.value = []
    evaluations.value = []
    results.value = []
    return
  }
  tabLoading.value = true
  tabError.value = ''
  try {
    const cycleId = selectedCycleId.value
    if (activeTab.value === 'goals') {
      goals.value = (await talentApi.performance.goals(cycleId, { page: 1, size: 100 })).records
    } else if (activeTab.value === 'evaluations') {
      evaluations.value = (await talentApi.performance.evaluations(cycleId, { page: 1, size: 100 })).records
    } else {
      results.value = (await talentApi.performance.results(cycleId, { page: 1, size: 100 })).records
    }
  } catch (err) {
    tabError.value = describeError(err)
  } finally {
    tabLoading.value = false
  }
}

function selectCycle(id: TalentId) {
  selectedCycleId.value = id
  void loadTab()
}

function openCycle() {
  Object.assign(cycleForm, {
    cycle_code: '',
    cycle_name: '',
    cycle_type: 'QUARTER',
    start_date: '',
    end_date: '',
  })
  cycleError.value = ''
  cycleVisible.value = true
}

async function submitCycle() {
  if (!cycleForm.cycle_code.trim() || !cycleForm.cycle_name.trim()) {
    cycleError.value = '周期编码与名称不能为空'
    return
  }
  if (!cycleForm.start_date || !cycleForm.end_date) {
    cycleError.value = '请选择起止日期'
    return
  }
  cycleSaving.value = true
  cycleError.value = ''
  try {
    const created = await talentApi.performance.createCycle({
      cycle_code: cycleForm.cycle_code.trim(),
      cycle_name: cycleForm.cycle_name.trim(),
      cycle_type: cycleForm.cycle_type,
      start_date: cycleForm.start_date,
      end_date: cycleForm.end_date,
    })
    ElMessage.success('绩效周期已创建')
    cycleVisible.value = false
    selectedCycleId.value = created.id
    await loadCycles()
  } catch (err) {
    cycleError.value = describeError(err)
  } finally {
    cycleSaving.value = false
  }
}

async function runCycleAction(action: 'open-goals' | 'start-evaluation' | 'close') {
  if (!selectedCycle.value) return
  const id = selectedCycle.value.id
  try {
    if (action === 'open-goals') {
      await talentApi.performance.openGoals(id)
      ElMessage.success('已开启目标制定')
    } else if (action === 'start-evaluation') {
      await talentApi.performance.startEvaluation(id)
      ElMessage.success('已开启评估')
    } else {
      await talentApi.performance.closeCycle(id)
      ElMessage.success('周期已关闭')
    }
    await loadCycles()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openGoal() {
  if (!selectedCycle.value) {
    ElMessage.warning('请先选择绩效周期')
    return
  }
  Object.assign(goalForm, {
    employee_id: '',
    goal_name: '',
    goal_desc: '',
    weight: 30,
    measure_type: 'QUANT',
    target_value: '',
  })
  goalError.value = ''
  goalVisible.value = true
}

async function submitGoal() {
  if (!selectedCycle.value) return
  if (!goalForm.employee_id) {
    goalError.value = '请选择员工'
    return
  }
  if (!goalForm.goal_name.trim()) {
    goalError.value = '目标名称不能为空'
    return
  }
  goalSaving.value = true
  goalError.value = ''
  try {
    await talentApi.performance.createGoal({
      cycle_id: selectedCycle.value.id,
      employee_id: goalForm.employee_id,
      goal_name: goalForm.goal_name.trim(),
      goal_desc: goalForm.goal_desc.trim() || undefined,
      weight: Number(goalForm.weight),
      measure_type: goalForm.measure_type,
      target_value: goalForm.target_value.trim() || undefined,
    })
    ElMessage.success('绩效目标已创建')
    goalVisible.value = false
    await loadTab()
  } catch (err) {
    goalError.value = describeError(err)
  } finally {
    goalSaving.value = false
  }
}

async function submitGoalFlow(id: TalentId) {
  try {
    await talentApi.performance.submitGoal(id)
    ElMessage.success('目标已提交审批')
    await loadTab()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openEvaluation() {
  if (!selectedCycle.value) {
    ElMessage.warning('请先选择绩效周期')
    return
  }
  Object.assign(evalForm, { employee_id: '', review_type: 'MANAGER', score: 80, summary: '' })
  evalError.value = ''
  evalVisible.value = true
}

async function submitEvaluation() {
  if (!selectedCycle.value) return
  if (!evalForm.employee_id) {
    evalError.value = '请选择员工'
    return
  }
  const score = Number(evalForm.score)
  if (!Number.isFinite(score) || score < 0 || score > 100) {
    evalError.value = '评估分数需在 0-100 之间'
    return
  }
  evalSaving.value = true
  evalError.value = ''
  try {
    await talentApi.performance.createEvaluation({
      cycle_id: selectedCycle.value.id,
      employee_id: evalForm.employee_id,
      review_type: evalForm.review_type,
      score,
      summary: evalForm.summary.trim() || undefined,
    })
    ElMessage.success('评估已录入')
    evalVisible.value = false
    await loadTab()
  } catch (err) {
    evalError.value = describeError(err)
  } finally {
    evalSaving.value = false
  }
}

async function submitEvaluationFlow(id: TalentId) {
  try {
    await talentApi.performance.submitEvaluation(id)
    ElMessage.success('评估已提交审批')
    await loadTab()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function openApproval(kind: 'goal' | 'evaluation', id: TalentId, label: string) {
  approvalTarget.value = { kind, id, label }
  Object.assign(approvalForm, { approved: true, opinion: '' })
  approvalError.value = ''
  approvalVisible.value = true
}

async function submitApproval() {
  if (!approvalTarget.value) return
  approvalSaving.value = true
  approvalError.value = ''
  const body = { approved: approvalForm.approved, opinion: approvalForm.opinion.trim() || undefined }
  try {
    if (approvalTarget.value.kind === 'goal') {
      await talentApi.performance.approveGoal(approvalTarget.value.id, body)
    } else {
      await talentApi.performance.approveEvaluation(approvalTarget.value.id, body)
    }
    ElMessage.success(approvalForm.approved ? '已审批通过' : '已驳回')
    approvalVisible.value = false
    await loadTab()
  } catch (err) {
    approvalError.value = describeError(err)
  } finally {
    approvalSaving.value = false
  }
}

async function calculateResults() {
  if (!selectedCycle.value) return
  try {
    await talentApi.performance.calculateResults(selectedCycle.value.id, { page: 1, size: 100 })
    ElMessage.success('绩效结果已计算')
    activeTab.value = 'results'
    await loadTab()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function publishResults() {
  if (!selectedCycle.value) return
  try {
    await talentApi.performance.publishResults(selectedCycle.value.id, { page: 1, size: 100 })
    ElMessage.success('绩效结果已发布')
    await loadTab()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(() => {
  void loadEmployees()
  void loadCycles()
})
</script>

<template>
  <div>
    <PageHeader
      module="云才 Talent"
      title="绩效管理"
      description="周期 → 目标制定与审批 → 评估提交审批 → 结果计算与发布；审批通过后结果才可发布，全部发布后才可关闭周期。"
    >
      <template #actions>
        <el-button v-perm="'talent:admin:performance:manage'" type="primary" @click="openCycle">新建周期</el-button>
        <el-button :loading="loading" @click="loadCycles">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-table :data="cycles" v-loading="loading" border stripe highlight-current-row>
        <el-table-column prop="cycle_code" label="周期编码" width="150" />
        <el-table-column prop="cycle_name" label="周期名称" min-width="170" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ cycleTypeLabel(row.cycle_type) }}</template>
        </el-table-column>
        <el-table-column label="起止日期" width="200">
          <template #default="{ row }">{{ row.start_date }} ~ {{ row.end_date }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.cycle_status)" size="small">{{ cycleStatusLabel(row.cycle_status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="evaluation_count" label="评估数" width="90" />
        <el-table-column label="平均分" width="90">
          <template #default="{ row }">{{ row.avg_score ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="selectCycle(row.id)">查看明细</el-button>
            <el-button
              v-if="row.cycle_status === 'DRAFT'"
              v-perm="'talent:admin:performance:manage'"
              link
              type="warning"
              @click="selectCycle(row.id); runCycleAction('open-goals')"
            >
              开启目标
            </el-button>
            <el-button
              v-if="row.cycle_status === 'GOAL_SETTING'"
              v-perm="'talent:admin:performance:manage'"
              link
              type="warning"
              @click="selectCycle(row.id); runCycleAction('start-evaluation')"
            >
              开启评估
            </el-button>
            <el-button
              v-if="row.cycle_status === 'EVALUATING'"
              v-perm="'talent:admin:performance:approve'"
              link
              type="success"
              @click="selectCycle(row.id); runCycleAction('close')"
            >
              关闭周期
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select v-model="selectedCycleId" placeholder="选择绩效周期" style="width: 260px" @change="loadTab">
          <el-option
            v-for="cycle in cycles"
            :key="String(cycle.id)"
            :label="`${cycle.cycle_name}（${cycle.cycle_code}）`"
            :value="cycle.id"
          />
        </el-select>
        <span v-if="selectedCycle" class="fl-muted">
          当前状态：{{ cycleStatusLabel(selectedCycle.cycle_status) }}
        </span>
      </div>

      <el-alert
        v-if="tabError"
        type="error"
        :closable="false"
        show-icon
        :title="tabError"
        style="margin-bottom: 12px"
      />

      <el-tabs v-model="activeTab" @tab-change="loadTab">
        <el-tab-pane label="目标" name="goals">
          <div class="fl-toolbar">
            <el-button v-perm="'talent:admin:performance:manage'" type="primary" plain @click="openGoal">
              新增目标
            </el-button>
            <span class="fl-muted">共 {{ goals.length }} 条</span>
          </div>
          <el-table :data="goals" v-loading="tabLoading" border stripe>
            <el-table-column prop="employee_name" label="员工" width="120" />
            <el-table-column prop="goal_name" label="目标" min-width="180" show-overflow-tooltip />
            <el-table-column label="权重" width="80">
              <template #default="{ row }">{{ row.weight ?? '-' }}%</template>
            </el-table-column>
            <el-table-column label="衡量方式" width="100">
              <template #default="{ row }">{{ measureLabel(row.measure_type) }}</template>
            </el-table-column>
            <el-table-column prop="target_value" label="目标值" width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.goal_status)" size="small">{{ row.goal_status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.goal_status === 'DRAFT'"
                  v-perm="'talent:admin:performance:manage'"
                  link
                  type="primary"
                  @click="submitGoalFlow(row.id)"
                >
                  提交
                </el-button>
                <el-button
                  v-if="row.goal_status === 'SUBMITTED'"
                  v-perm="'talent:admin:performance:approve'"
                  link
                  type="success"
                  @click="openApproval('goal', row.id, row.employee_name + ' / ' + row.goal_name)"
                >
                  审批
                </el-button>
                <span v-if="row.approve_opinion" class="fl-muted">{{ row.approve_opinion }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="评估" name="evaluations">
          <div class="fl-toolbar">
            <el-button v-perm="'talent:admin:performance:manage'" type="primary" plain @click="openEvaluation">
              录入评估
            </el-button>
            <span class="fl-muted">共 {{ evaluations.length }} 条</span>
          </div>
          <el-table :data="evaluations" v-loading="tabLoading" border stripe>
            <el-table-column prop="employee_name" label="员工" width="120" />
            <el-table-column label="评估类型" width="110">
              <template #default="{ row }">{{ reviewLabel(row.review_type) }}</template>
            </el-table-column>
            <el-table-column prop="score" label="分数" width="90" />
            <el-table-column prop="grade" label="等级" width="80" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.eval_status)" size="small">{{ row.eval_status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.eval_status === 'DRAFT'"
                  v-perm="'talent:admin:performance:manage'"
                  link
                  type="primary"
                  @click="submitEvaluationFlow(row.id)"
                >
                  提交审批
                </el-button>
                <el-button
                  v-if="row.eval_status === 'APPROVING'"
                  v-perm="'talent:admin:performance:approve'"
                  link
                  type="success"
                  @click="openApproval('evaluation', row.id, row.employee_name + ' / ' + row.grade)"
                >
                  审批
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="结果" name="results">
          <div class="fl-toolbar">
            <el-button v-perm="'talent:admin:performance:manage'" type="primary" plain @click="calculateResults">
              计算结果
            </el-button>
            <el-button v-perm="'talent:admin:performance:approve'" type="success" plain @click="publishResults">
              发布结果
            </el-button>
            <span class="fl-muted">共 {{ results.length }} 条</span>
          </div>
          <el-table :data="results" v-loading="tabLoading" border stripe>
            <el-table-column prop="rank_no" label="排名" width="80" />
            <el-table-column prop="employee_name" label="员工" width="130" />
            <el-table-column prop="final_score" label="最终得分" width="110" />
            <el-table-column prop="grade" label="等级" width="90" />
            <el-table-column prop="bonus_coefficient" label="奖金系数" width="110" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.result_status)" size="small">{{ row.result_status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="published_at" label="发布时间" width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="cycleVisible" title="新建绩效周期" width="540px">
      <el-alert v-if="cycleError" type="error" :closable="false" show-icon :title="cycleError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="周期编码" required>
          <el-input v-model="cycleForm.cycle_code" placeholder="如 2026Q1" maxlength="32" />
        </el-form-item>
        <el-form-item label="周期名称" required>
          <el-input v-model="cycleForm.cycle_name" placeholder="如 2026 年第一季度" maxlength="64" />
        </el-form-item>
        <el-form-item label="周期类型">
          <el-select v-model="cycleForm.cycle_type" style="width: 100%">
            <el-option v-for="opt in cycleTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="cycleForm.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="cycleForm.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cycleVisible = false">取消</el-button>
        <el-button type="primary" :loading="cycleSaving" @click="submitCycle">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="goalVisible" title="新增绩效目标" width="560px">
      <el-alert v-if="goalError" type="error" :closable="false" show-icon :title="goalError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="员工" required>
          <el-select v-model="goalForm.employee_id" filterable placeholder="选择员工" style="width: 100%">
            <el-option
              v-for="emp in employees"
              :key="String(emp.id)"
              :label="`${emp.name}（${emp.emp_no}）`"
              :value="emp.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标名称" required>
          <el-input v-model="goalForm.goal_name" maxlength="128" />
        </el-form-item>
        <el-form-item label="目标描述">
          <el-input v-model="goalForm.goal_desc" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item label="权重(%)">
          <el-input-number v-model="goalForm.weight" :min="0" :max="100" :step="5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="衡量方式">
          <el-select v-model="goalForm.measure_type" style="width: 100%">
            <el-option v-for="opt in measureTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标值">
          <el-input v-model="goalForm.target_value" placeholder="如 完成 120%" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="goalVisible = false">取消</el-button>
        <el-button type="primary" :loading="goalSaving" @click="submitGoal">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evalVisible" title="录入绩效评估" width="540px">
      <el-alert v-if="evalError" type="error" :closable="false" show-icon :title="evalError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="员工" required>
          <el-select v-model="evalForm.employee_id" filterable placeholder="选择员工" style="width: 100%">
            <el-option
              v-for="emp in employees"
              :key="String(emp.id)"
              :label="`${emp.name}（${emp.emp_no}）`"
              :value="emp.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="评估类型">
          <el-select v-model="evalForm.review_type" style="width: 100%">
            <el-option v-for="opt in reviewTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="评估分数" required>
          <el-input-number v-model="evalForm.score" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="评估说明">
          <el-input v-model="evalForm.summary" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="evalVisible = false">取消</el-button>
        <el-button type="primary" :loading="evalSaving" @click="submitEvaluation">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="approvalVisible" title="审批办理" width="480px">
      <el-alert
        v-if="approvalError"
        type="error"
        :closable="false"
        show-icon
        :title="approvalError"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px">
        <el-form-item label="审批对象">
          <el-text>{{ approvalTarget?.label }}</el-text>
        </el-form-item>
        <el-form-item label="审批结论" required>
          <el-radio-group v-model="approvalForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="approvalForm.opinion" type="textarea" :rows="3" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approvalVisible = false">取消</el-button>
        <el-button type="primary" :loading="approvalSaving" @click="submitApproval">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
