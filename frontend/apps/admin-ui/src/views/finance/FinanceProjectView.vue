<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  financeApi,
  type FinanceId,
  type FinanceProject,
  type FinanceProjectDetail,
  type FinanceProfitability,
  type FinanceTimesheet,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const projects = ref<FinanceProject[]>([])
const statusFilter = ref('')
const loading = ref(false)
const error = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<FinanceProjectDetail | null>(null)
const profitability = ref<FinanceProfitability | null>(null)
const detailTab = ref('members')

const createVisible = ref(false)
const createError = ref('')
const createSaving = ref(false)
const projectForm = reactive({
  project_name: '',
  project_type: 'CONTRACT',
  customer_name: '',
  contract_no: '',
  contract_amount: '',
  budget_amount: '',
  estimated_total_cost: '',
  start_date: '',
  end_date: '',
  currency_code: 'CNY',
  description: '',
})

const memberVisible = ref(false)
const memberError = ref('')
const memberForm = reactive({
  employee_id: '',
  employee_name: '',
  member_role: 'MEMBER',
  allocation_pct: '100',
  cost_rate: '',
  join_date: new Date().toISOString().slice(0, 10),
})

const milestoneVisible = ref(false)
const milestoneError = ref('')
const milestoneForm = reactive({ milestone_name: '', plan_date: '', deliverable: '', weight: '1' })

const timesheetVisible = ref(false)
const timesheetError = ref('')
const timesheetForm = reactive({
  employee_id: '',
  employee_name: '',
  work_date: new Date().toISOString().slice(0, 10),
  hours: '8',
  task_desc: '',
})

const costVisible = ref(false)
const costError = ref('')
const costForm = reactive({
  cost_type: 'LABOR',
  subject_code: '',
  amount: '',
  cost_date: new Date().toISOString().slice(0, 10),
  description: '',
})

const settlementVisible = ref(false)
const settlementError = ref('')
const settlementForm = reactive({ settlement_amount: '', currency_code: 'CNY', remark: '' })

const selectedTimesheets = ref<FinanceId[]>([])

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已立项',
  IN_PROGRESS: '执行中',
  COMPLETED: '已完成',
  SETTLED: '已结算',
  CLOSED: '已关闭',
}

const gapItems = [
  '项目缺少更新/关闭/删除接口，也没有成员移除、里程碑状态更新接口；页面只暴露后端已提供的能力。',
  '工时列表只能按项目+状态查询，没有“我的工时”聚合接口，员工端只能遍历项目在前端过滤。',
  '项目收入确认、结算审批与凭证联动均在服务端完成，页面只展示返回结果，无法部分调整。',
]

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

async function loadProjects() {
  loading.value = true
  error.value = ''
  try {
    projects.value = await financeApi.projects.list({ status: statusFilter.value || undefined, size: 100 })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: FinanceProject) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  profitability.value = null
  detailTab.value = 'members'
  try {
    detail.value = await financeApi.projects.detail(row.id)
    try {
      profitability.value = await financeApi.projects.profitability(row.id)
    } catch {
      profitability.value = null
    }
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  if (!detail.value) return
  await openDetail(detail.value.project)
}

async function approveProject(row: FinanceProject) {
  try {
    await ElMessageBox.confirm(`确认立项 ${row.project_name}？`, '项目立项', { type: 'warning' })
  } catch {
    return
  }
  try {
    const result = await financeApi.projects.approve(row.id)
    ElMessage.success(`项目 ${result.project_no} 状态 ${statusLabel(result.status)}`)
    await loadProjects()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitProject() {
  if (!projectForm.project_name.trim()) {
    createError.value = '项目名称不能为空'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    const created = await financeApi.projects.create({
      project_name: projectForm.project_name.trim(),
      project_type: projectForm.project_type,
      customer_name: projectForm.customer_name.trim() || undefined,
      contract_no: projectForm.contract_no.trim() || undefined,
      contract_amount: projectForm.contract_amount || undefined,
      budget_amount: projectForm.budget_amount || undefined,
      estimated_total_cost: projectForm.estimated_total_cost || undefined,
      start_date: projectForm.start_date || undefined,
      end_date: projectForm.end_date || undefined,
      currency_code: projectForm.currency_code,
      description: projectForm.description.trim() || undefined,
    })
    ElMessage.success(`项目 ${created.project_no} 已创建（${statusLabel(created.status)}）`)
    createVisible.value = false
    await loadProjects()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function submitMember() {
  if (!detail.value) return
  if (!memberForm.employee_id || !memberForm.employee_name.trim()) {
    memberError.value = '员工 ID 与姓名不能为空'
    return
  }
  try {
    await financeApi.projects.addMember(detail.value.project.id, {
      employee_id: memberForm.employee_id,
      employee_name: memberForm.employee_name.trim(),
      member_role: memberForm.member_role,
      allocation_pct: memberForm.allocation_pct,
      cost_rate: memberForm.cost_rate || undefined,
      join_date: memberForm.join_date,
    })
    ElMessage.success('项目成员已加入')
    memberVisible.value = false
    await refreshDetail()
  } catch (err) {
    memberError.value = describeError(err)
  }
}

async function submitMilestone() {
  if (!detail.value) return
  if (!milestoneForm.milestone_name.trim() || !milestoneForm.plan_date) {
    milestoneError.value = '里程碑名称与计划日期不能为空'
    return
  }
  try {
    await financeApi.projects.addMilestone(detail.value.project.id, {
      milestone_name: milestoneForm.milestone_name.trim(),
      plan_date: milestoneForm.plan_date,
      deliverable: milestoneForm.deliverable.trim() || undefined,
      weight: milestoneForm.weight || undefined,
    })
    ElMessage.success('里程碑已添加')
    milestoneVisible.value = false
    await refreshDetail()
  } catch (err) {
    milestoneError.value = describeError(err)
  }
}

async function submitTimesheet() {
  if (!detail.value) return
  if (!timesheetForm.employee_id || !timesheetForm.employee_name.trim() || !timesheetForm.hours) {
    timesheetError.value = '员工 ID、姓名与工时不能为空'
    return
  }
  try {
    await financeApi.projects.createTimesheet(detail.value.project.id, {
      employee_id: timesheetForm.employee_id,
      employee_name: timesheetForm.employee_name.trim(),
      work_date: timesheetForm.work_date,
      hours: timesheetForm.hours,
      task_desc: timesheetForm.task_desc.trim() || undefined,
    })
    ElMessage.success('工时已提交')
    timesheetVisible.value = false
    await refreshDetail()
  } catch (err) {
    timesheetError.value = describeError(err)
  }
}

async function approveTimesheets() {
  if (!selectedTimesheets.value.length) {
    ElMessage.warning('请先勾选工时')
    return
  }
  try {
    const result = await financeApi.projects.approveTimesheets(selectedTimesheets.value)
    ElMessage.success(`审批通过 ${result.approved_count} 条，驳回 ${result.rejected_count} 条，成本合计 ${money(result.cost_total)}`)
    selectedTimesheets.value = []
    await refreshDetail()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function rejectTimesheet(row: FinanceTimesheet) {
  try {
    await ElMessageBox.confirm(`驳回工时 ${row.timesheet_no}？`, '驳回工时', { type: 'warning' })
  } catch {
    return
  }
  try {
    await financeApi.projects.rejectTimesheet(row.id, '管理端驳回')
    ElMessage.success('工时已驳回')
    await refreshDetail()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitCost() {
  if (!detail.value) return
  if (!costForm.amount) {
    costError.value = '成本金额不能为空'
    return
  }
  try {
    await financeApi.projects.collectCost(detail.value.project.id, {
      cost_type: costForm.cost_type,
      subject_code: costForm.subject_code.trim() || undefined,
      amount: costForm.amount,
      cost_date: costForm.cost_date,
      description: costForm.description.trim() || undefined,
    })
    ElMessage.success('项目成本已归集')
    costVisible.value = false
    await refreshDetail()
  } catch (err) {
    costError.value = describeError(err)
  }
}

async function recognizeRevenue() {
  if (!detail.value) return
  try {
    const result = await financeApi.projects.recognizeRevenue(detail.value.project.id, {})
    ElMessage.success(`收入确认完成：${result.method}，累计确认 ${money(result.recognized_revenue)}（本次 ${money(result.delta)}）`)
    await refreshDetail()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function submitSettlement() {
  if (!detail.value) return
  if (!settlementForm.settlement_amount) {
    settlementError.value = '结算金额不能为空'
    return
  }
  try {
    await financeApi.projects.createSettlement(detail.value.project.id, {
      settlement_amount: settlementForm.settlement_amount,
      currency_code: settlementForm.currency_code,
      remark: settlementForm.remark.trim() || undefined,
    })
    ElMessage.success('结算单已创建')
    settlementVisible.value = false
    await refreshDetail()
  } catch (err) {
    settlementError.value = describeError(err)
  }
}

async function approveSettlement(id: number) {
  try {
    const result = await financeApi.projects.approveSettlement(id)
    ElMessage.success(`结算单 ${result.settlement_no} 状态 ${statusLabel(result.status)}`)
    await refreshDetail()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(loadProjects)
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="项目核算"
      description="项目立项、成员与里程碑、工时成本、成本归集、收入确认、结算与项目盈利分析。"
    >
      <template #actions>
        <el-select v-model="statusFilter" placeholder="项目状态" clearable style="width: 150px" @change="loadProjects">
          <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button v-perm="'biz:finance:project:create'" type="primary" @click="createVisible = true">新建项目</el-button>
        <el-button :loading="loading" @click="loadProjects">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="projects" v-loading="loading" border stripe>
        <el-table-column prop="project_no" label="项目编号" width="130" />
        <el-table-column prop="project_name" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="project_type" label="类型" width="90" />
        <el-table-column prop="customer_name" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column label="合同额" width="130" align="right"><template #default="{ row }">{{ money(row.contract_amount) }}</template></el-table-column>
        <el-table-column label="预算" width="130" align="right"><template #default="{ row }">{{ money(row.budget_amount) }}</template></el-table-column>
        <el-table-column label="实际成本" width="130" align="right"><template #default="{ row }">{{ money(row.actual_cost) }}</template></el-table-column>
        <el-table-column label="确认收入" width="130" align="right"><template #default="{ row }">{{ money(row.recognized_revenue) }}</template></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag size="small">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link @click="openDetail(row)">核算详情</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'PENDING_APPROVAL'" v-perm="'biz:finance:project:approve'" link type="primary" @click="approveProject(row)">立项</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="项目核算详情" size="860px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="项目编号">{{ detail.project.project_no }}</el-descriptions-item>
            <el-descriptions-item label="项目名称">{{ detail.project.project_name }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(detail.project.status) }}</el-descriptions-item>
            <el-descriptions-item label="合同额">{{ money(detail.project.contract_amount) }}</el-descriptions-item>
            <el-descriptions-item label="实际成本">{{ money(detail.project.actual_cost) }}</el-descriptions-item>
            <el-descriptions-item label="确认收入">{{ money(detail.project.recognized_revenue) }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions v-if="profitability" :column="4" border size="small" style="margin-top: 12px">
            <el-descriptions-item label="毛利">{{ money(profitability.gross_profit) }}</el-descriptions-item>
            <el-descriptions-item label="毛利率">{{ profitability.gross_margin }}</el-descriptions-item>
            <el-descriptions-item label="预算使用率">{{ profitability.cost_budget_usage }}</el-descriptions-item>
            <el-descriptions-item label="预警">
              <el-tag v-if="profitability.warnings.length" type="warning" size="small">{{ profitability.warnings.join('；') }}</el-tag>
              <span v-else>无</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="fl-toolbar" style="margin: 16px 0 8px">
            <el-button v-perm="'biz:finance:project:member:manage'" plain @click="memberVisible = true">加成员</el-button>
            <el-button v-perm="'biz:finance:project:approve'" plain @click="milestoneVisible = true">加里程碑</el-button>
            <el-button v-perm="'biz:finance:timesheet:submit'" plain @click="timesheetVisible = true">报工时</el-button>
            <el-button v-perm="'biz:finance:project:approve'" plain @click="costVisible = true">归集成本</el-button>
            <el-button v-perm="'biz:finance:project:approve'" plain @click="recognizeRevenue">确认收入</el-button>
            <el-button v-perm="'biz:finance:project:approve'" plain @click="settlementVisible = true">创建结算</el-button>
            <el-button @click="refreshDetail">刷新</el-button>
          </div>

          <el-tabs v-model="detailTab">
            <el-tab-pane :label="`成员 ${detail.members.length}`" name="members">
              <el-table :data="detail.members" border size="small">
                <el-table-column prop="employee_name" label="姓名" width="120" />
                <el-table-column prop="member_role" label="角色" width="110" />
                <el-table-column prop="allocation_pct" label="投入比例" width="110" />
                <el-table-column prop="cost_rate" label="成本费率" width="110" />
                <el-table-column prop="join_date" label="加入日期" width="120" />
                <el-table-column prop="status" label="状态" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`里程碑 ${detail.milestones.length}`" name="milestones">
              <el-table :data="detail.milestones" border size="small">
                <el-table-column prop="milestone_name" label="名称" min-width="160" />
                <el-table-column prop="plan_date" label="计划日期" width="120" />
                <el-table-column prop="actual_date" label="实际日期" width="120" />
                <el-table-column prop="weight" label="权重" width="90" />
                <el-table-column prop="status" label="状态" width="110" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`工时 ${detail.timesheets.length}`" name="timesheets">
              <div class="fl-toolbar">
                <el-button v-perm="'biz:finance:timesheet:approve'" type="primary" plain :disabled="!selectedTimesheets.length" @click="approveTimesheets">
                  审批选中
                </el-button>
              </div>
              <el-table :data="detail.timesheets" border size="small" @selection-change="(rows: FinanceTimesheet[]) => selectedTimesheets = rows.map((row) => row.id)">
                <el-table-column type="selection" width="42" />
                <el-table-column prop="timesheet_no" label="工时单号" width="160" />
                <el-table-column prop="employee_name" label="员工" width="110" />
                <el-table-column prop="work_date" label="日期" width="110" />
                <el-table-column prop="hours" label="工时" width="80" />
                <el-table-column prop="cost_amount" label="成本" width="110" />
                <el-table-column prop="status" label="状态" width="100" />
                <el-table-column label="操作" width="90">
                  <template #default="{ row }">
                    <el-button v-if="row.status === 'SUBMITTED'" v-perm="'biz:finance:timesheet:approve'" link type="danger" @click="rejectTimesheet(row)">驳回</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`成本 ${detail.costs.length}`" name="costs">
              <el-table :data="detail.costs" border size="small">
                <el-table-column prop="cost_no" label="成本单号" width="160" />
                <el-table-column prop="cost_type" label="类型" width="120" />
                <el-table-column prop="cost_date" label="日期" width="120" />
                <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
                <el-table-column prop="description" label="说明" min-width="160" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`结算 ${detail.settlements.length}`" name="settlements">
              <el-table :data="detail.settlements" border size="small">
                <el-table-column prop="settlement_no" label="结算单号" width="170" />
                <el-table-column label="结算金额" width="130" align="right"><template #default="{ row }">{{ money(row.settlement_amount) }}</template></el-table-column>
                <el-table-column label="已结算" width="130" align="right"><template #default="{ row }">{{ money(row.settled_amount) }}</template></el-table-column>
                <el-table-column prop="status" label="状态" width="110" />
                <el-table-column label="操作" width="110">
                  <template #default="{ row }">
                    <el-button v-if="row.status !== 'APPROVED'" v-perm="'biz:finance:project:approve'" link type="primary" @click="approveSettlement(row.id)">审批</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" title="新建项目" width="600px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="项目名称" required><el-input v-model="projectForm.project_name" maxlength="128" /></el-form-item>
        <el-form-item label="项目类型">
          <el-select v-model="projectForm.project_type" style="width: 100%">
            <el-option label="合同项目" value="CONTRACT" />
            <el-option label="研发项目" value="RND" />
            <el-option label="内部项目" value="INTERNAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户名称"><el-input v-model="projectForm.customer_name" maxlength="128" /></el-form-item>
        <el-form-item label="合同号"><el-input v-model="projectForm.contract_no" maxlength="64" /></el-form-item>
        <el-form-item label="合同额"><el-input v-model="projectForm.contract_amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="预算额"><el-input v-model="projectForm.budget_amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="预计总成本"><el-input v-model="projectForm.estimated_total_cost" placeholder="0.00" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="projectForm.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="projectForm.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="projectForm.description" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" @click="submitProject">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" title="加入项目成员" width="480px">
      <el-alert v-if="memberError" type="error" :closable="false" show-icon :title="memberError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="员工 ID" required><el-input v-model="memberForm.employee_id" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="memberForm.employee_name" maxlength="64" /></el-form-item>
        <el-form-item label="角色"><el-input v-model="memberForm.member_role" maxlength="32" /></el-form-item>
        <el-form-item label="投入比例"><el-input v-model="memberForm.allocation_pct" placeholder="100" /></el-form-item>
        <el-form-item label="成本费率"><el-input v-model="memberForm.cost_rate" placeholder="元/小时" /></el-form-item>
        <el-form-item label="加入日期">
          <el-date-picker v-model="memberForm.join_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMember">加入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="milestoneVisible" title="新增里程碑" width="480px">
      <el-alert v-if="milestoneError" type="error" :closable="false" show-icon :title="milestoneError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="名称" required><el-input v-model="milestoneForm.milestone_name" maxlength="128" /></el-form-item>
        <el-form-item label="计划日期" required>
          <el-date-picker v-model="milestoneForm.plan_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="交付物"><el-input v-model="milestoneForm.deliverable" maxlength="200" /></el-form-item>
        <el-form-item label="权重"><el-input v-model="milestoneForm.weight" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="milestoneVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMilestone">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="timesheetVisible" title="填报工时" width="480px">
      <el-alert v-if="timesheetError" type="error" :closable="false" show-icon :title="timesheetError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="员工 ID" required><el-input v-model="timesheetForm.employee_id" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="timesheetForm.employee_name" maxlength="64" /></el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="timesheetForm.work_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工时" required><el-input v-model="timesheetForm.hours" placeholder="8" /></el-form-item>
        <el-form-item label="工作内容"><el-input v-model="timesheetForm.task_desc" type="textarea" :rows="2" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="timesheetVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTimesheet">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="costVisible" title="归集项目成本" width="480px">
      <el-alert v-if="costError" type="error" :closable="false" show-icon :title="costError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="成本类型">
          <el-select v-model="costForm.cost_type" style="width: 100%">
            <el-option label="人工" value="LABOR" />
            <el-option label="外包" value="OUTSOURCE" />
            <el-option label="差旅" value="TRAVEL" />
            <el-option label="采购" value="PURCHASE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="科目编码"><el-input v-model="costForm.subject_code" maxlength="32" /></el-form-item>
        <el-form-item label="金额" required><el-input v-model="costForm.amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="发生日期">
          <el-date-picker v-model="costForm.cost_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="costForm.description" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="costVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCost">归集</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="settlementVisible" title="创建项目结算" width="480px">
      <el-alert v-if="settlementError" type="error" :closable="false" show-icon :title="settlementError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="结算金额" required><el-input v-model="settlementForm.settlement_amount" placeholder="0.00" /></el-form-item>
        <el-form-item label="币种"><el-input v-model="settlementForm.currency_code" maxlength="8" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="settlementForm.remark" maxlength="200" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settlementVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSettlement">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
