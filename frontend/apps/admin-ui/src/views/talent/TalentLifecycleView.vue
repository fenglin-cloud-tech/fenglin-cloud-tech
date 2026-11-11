<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentOnboardingView,
  type TalentResignationView,
  type TalentTransferView,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const gapItems = [
  '入职办理需先有 offer_id 或 employee_id，本页不提供候选人/员工选择器，需手工填写 ID。',
  '调动生效与审批是两个独立动作，前端按顺序调用，不保证后端幂等。',
  '试用期评估接口只按试用记录 ID 操作，没有试用期列表可查。',
]

const onboardingForm = reactive({
  offer_id: '',
  employee_id: '',
  onboard_date: '',
  mentor_id: '',
})
const onboardingSaving = ref(false)
const onboardingResult = ref('')
const onboardingId = ref('')
const completeSaving = ref(false)

const probationForm = reactive({ id: '', evaluation: '', result: 'PASSED', extend_months: 0 })
const probationSaving = ref(false)
const probationResult = ref('')

const transferForm = reactive({
  employee_id: '',
  transfer_type: 'TRANSFER',
  org_id: '',
  position_id: '',
  grade_id: '',
  salary: '',
  effective_date: '',
  reason: '',
})
const transferSaving = ref(false)
const transferResult = ref('')
const transferId = ref('')
const transferActionSaving = ref(false)

const resignForm = reactive({
  employee_id: '',
  resign_type: 'VOLUNTARY',
  apply_date: '',
  last_work_date: '',
  reason_category: '',
})
const resignSaving = ref(false)
const resignResult = ref('')
const resignId = ref('')
const resignActionSaving = ref(false)

const transferTypeOptions = [
  { value: 'TRANSFER', label: '调动' },
  { value: 'PROMOTION', label: '晋升' },
  { value: 'ADJUST_SALARY', label: '调薪' },
  { value: 'SECONDMENT', label: '借调' },
]

const resignTypeOptions = [
  { value: 'VOLUNTARY', label: '主动离职' },
  { value: 'INVOLUNTARY', label: '被动离职' },
  { value: 'RETIRE', label: '退休' },
  { value: 'CONTRACT_END', label: '合同到期' },
]

const probationResultOptions = [
  { value: 'PASSED', label: '通过' },
  { value: 'EXTENDED', label: '延长试用' },
  { value: 'FAILED', label: '不通过' },
]

// ---------- 历史单据 ----------

const historyTab = ref<'onboarding' | 'transfer' | 'resignation'>('onboarding')
const historyLoading = ref(false)
const onboardingRows = ref<TalentOnboardingView[]>([])
const transferRows = ref<TalentTransferView[]>([])
const resignationRows = ref<TalentResignationView[]>([])
const onboardingQuery = reactive({ status: '', page: 1, size: 10 })
const transferQuery = reactive({ status: '', page: 1, size: 10 })
const resignationQuery = reactive({ status: '', page: 1, size: 10 })
const onboardingTotal = ref(0)
const transferTotal = ref(0)
const resignationTotal = ref(0)

const onboardingStatusOptions = [
  { value: 'PENDING', label: '待完成' },
  { value: 'DONE', label: '已完成' },
]
const transferStatusOptions = [
  { value: 'APPROVING', label: '审批中' },
  { value: 'EFFECTIVE', label: '已生效' },
  { value: 'CANCELED', label: '已取消' },
]
const resignationStatusOptions = [
  { value: 'APPROVING', label: '审批中' },
  { value: 'EFFECTIVE', label: '已生效' },
  { value: 'REVOKED', label: '已撤销' },
]

async function loadHistory() {
  historyLoading.value = true
  try {
    const [onboardingPage, transferPage, resignationPage] = await Promise.all([
      talentApi.lifecycle.onboardings({
        status: onboardingQuery.status || undefined,
        page: onboardingQuery.page,
        size: onboardingQuery.size,
      }),
      talentApi.lifecycle.transfers({
        status: transferQuery.status || undefined,
        page: transferQuery.page,
        size: transferQuery.size,
      }),
      talentApi.lifecycle.resignations({
        status: resignationQuery.status || undefined,
        page: resignationQuery.page,
        size: resignationQuery.size,
      }),
    ])
    onboardingRows.value = onboardingPage.records
    onboardingTotal.value = onboardingPage.total
    transferRows.value = transferPage.records
    transferTotal.value = transferPage.total
    resignationRows.value = resignationPage.records
    resignationTotal.value = resignationPage.total
  } catch (err) {
    onboardingResult.value = describeError(err)
  } finally {
    historyLoading.value = false
  }
}

function searchHistory(tab: 'onboarding' | 'transfer' | 'resignation') {
  if (tab === 'onboarding') onboardingQuery.page = 1
  else if (tab === 'transfer') transferQuery.page = 1
  else resignationQuery.page = 1
  loadHistory()
}

function useOnboardingRow(row: TalentOnboardingView) {
  onboardingId.value = String(row.id)
  onboardingResult.value = `已选择入职单 ${row.id}（${row.employee_name ?? row.employee_id}），可直接点击「完成入职」`
}

function useTransferRow(row: TalentTransferView) {
  transferId.value = String(row.id)
  transferResult.value = `已选择调动单 ${row.id}（${row.employee_name ?? row.employee_id}），可审批或生效`
}

function useResignationRow(row: TalentResignationView) {
  resignId.value = String(row.id)
  resignResult.value = `已选择离职单 ${row.id}（${row.employee_name ?? row.employee_id}），可审批`
}

onMounted(loadHistory)

function statusTag(value?: string) {
  if (value === 'DONE' || value === 'EFFECTIVE') return 'success'
  if (value === 'CANCELED' || value === 'REVOKED') return 'danger'
  return 'warning'
}

function toId(text: string): string | undefined {
  return text.trim() ? text.trim() : undefined
}

async function submitOnboarding() {
  if (!onboardingForm.offer_id.trim() && !onboardingForm.employee_id.trim()) {
    onboardingResult.value = 'offer_id 与 employee_id 至少填写一个'
    return
  }
  onboardingSaving.value = true
  onboardingResult.value = ''
  try {
    const result = await talentApi.lifecycle.createOnboarding({
      offer_id: toId(onboardingForm.offer_id),
      employee_id: toId(onboardingForm.employee_id),
      onboard_date: onboardingForm.onboard_date || undefined,
      mentor_id: toId(onboardingForm.mentor_id),
    })
    onboardingId.value = String(result.id)
    onboardingResult.value = `入职单已创建，onboarding_id = ${result.id}，状态 ${result.ob_status}`
  } catch (err) {
    onboardingResult.value = describeError(err)
  } finally {
    onboardingSaving.value = false
  }
}

async function completeOnboarding() {
  if (!onboardingId.value.trim()) {
    onboardingResult.value = '请先创建入职单，或手工填写入职单 ID'
    return
  }
  completeSaving.value = true
  try {
    const result = await talentApi.lifecycle.completeOnboarding(onboardingId.value.trim())
    onboardingResult.value = `入职已完成，状态 ${result.ob_status}，员工状态 ${result.employee_status}`
  } catch (err) {
    onboardingResult.value = describeError(err)
  } finally {
    completeSaving.value = false
  }
}

async function submitProbation() {
  if (!probationForm.id.trim()) {
    probationResult.value = '试用记录 ID 为必填项'
    return
  }
  probationSaving.value = true
  probationResult.value = ''
  try {
    const result = await talentApi.lifecycle.evaluateProbation(probationForm.id.trim(), {
      evaluation: probationForm.evaluation.trim() || undefined,
      result: probationForm.result,
      extend_months: probationForm.result === 'EXTENDED' ? probationForm.extend_months : undefined,
    })
    probationResult.value = `评估已提交，状态 ${result.prob_status}`
  } catch (err) {
    probationResult.value = describeError(err)
  } finally {
    probationSaving.value = false
  }
}

async function submitTransfer() {
  if (!transferForm.employee_id.trim()) {
    transferResult.value = '员工 ID 为必填项'
    return
  }
  transferSaving.value = true
  transferResult.value = ''
  try {
    const result = await talentApi.lifecycle.createTransfer({
      employee_id: transferForm.employee_id.trim(),
      transfer_type: transferForm.transfer_type,
      after: {
        org_id: toId(transferForm.org_id),
        position_id: toId(transferForm.position_id),
        grade_id: toId(transferForm.grade_id),
        salary: transferForm.salary ? Number(transferForm.salary) : undefined,
      },
      effective_date: transferForm.effective_date || undefined,
      reason: transferForm.reason.trim() || undefined,
    })
    transferId.value = String(result.id)
    transferResult.value = `调动单已创建，transfer_id = ${result.id}，状态 ${result.tf_status}`
  } catch (err) {
    transferResult.value = describeError(err)
  } finally {
    transferSaving.value = false
  }
}

async function transferAction(action: 'approve' | 'effective') {
  if (!transferId.value.trim()) {
    transferResult.value = '请先创建调动单，或手工填写调动单 ID'
    return
  }
  transferActionSaving.value = true
  try {
    const result =
      action === 'approve'
        ? await talentApi.lifecycle.approveTransfer(transferId.value.trim(), { approved: true, opinion: 'E2E 通过' })
        : await talentApi.lifecycle.effectiveTransfer(transferId.value.trim())
    transferResult.value = `${action === 'approve' ? '审批' : '生效'}完成，状态 ${result.tf_status}`
  } catch (err) {
    transferResult.value = describeError(err)
  } finally {
    transferActionSaving.value = false
  }
}

async function submitResign() {
  if (!resignForm.employee_id.trim()) {
    resignResult.value = '员工 ID 为必填项'
    return
  }
  resignSaving.value = true
  resignResult.value = ''
  try {
    const result = await talentApi.lifecycle.createResignation({
      employee_id: resignForm.employee_id.trim(),
      resign_type: resignForm.resign_type,
      apply_date: resignForm.apply_date || undefined,
      last_work_date: resignForm.last_work_date || undefined,
      reason_category: resignForm.reason_category.trim() || undefined,
    })
    resignId.value = String(result.id)
    resignResult.value = `离职申请已创建，resignation_id = ${result.id}，状态 ${result.rs_status}`
  } catch (err) {
    resignResult.value = describeError(err)
  } finally {
    resignSaving.value = false
  }
}

async function approveResign() {
  if (!resignId.value.trim()) {
    resignResult.value = '请先创建离职申请，或手工填写离职单 ID'
    return
  }
  resignActionSaving.value = true
  try {
    const result = await talentApi.lifecycle.approveResignation(resignId.value.trim(), {
      approved: true,
      opinion: 'E2E 通过',
    })
    resignResult.value = `离职审批完成，状态 ${result.rs_status}`
  } catch (err) {
    resignResult.value = describeError(err)
  } finally {
    resignActionSaving.value = false
  }
}
</script>

<template>
  <div>
    <PageHeader module="云才 Talent" title="入转调离" description="入职、试用期评估、调动与离职的单据提交与状态流转。">
      <template #actions>
        <el-button @click="$router.push('/talent/employees')">员工档案</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" header="入职办理" style="margin-bottom: 16px">
          <el-form label-width="110px">
            <el-form-item label="Offer ID">
              <el-input v-model="onboardingForm.offer_id" placeholder="与员工 ID 二选一" />
            </el-form-item>
            <el-form-item label="员工 ID">
              <el-input v-model="onboardingForm.employee_id" />
            </el-form-item>
            <el-form-item label="入职日期">
              <el-date-picker v-model="onboardingForm.onboard_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="导师 ID">
              <el-input v-model="onboardingForm.mentor_id" />
            </el-form-item>
            <el-form-item label="入职单 ID">
              <el-input v-model="onboardingId" placeholder="创建后自动回填，也可手工填写" />
            </el-form-item>
          </el-form>
          <el-alert v-if="onboardingResult" :closable="false" :title="onboardingResult" type="success" show-icon />
          <div style="margin-top: 12px; text-align: right">
            <el-button :loading="completeSaving" @click="completeOnboarding">完成入职</el-button>
            <el-button type="primary" :loading="onboardingSaving" @click="submitOnboarding">创建入职单</el-button>
          </div>
        </el-card>

        <el-card shadow="never" header="试用期评估">
          <el-form label-width="110px">
            <el-form-item label="试用记录 ID">
              <el-input v-model="probationForm.id" />
            </el-form-item>
            <el-form-item label="评估结论">
              <el-select v-model="probationForm.result" style="width: 100%">
                <el-option v-for="opt in probationResultOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="probationForm.result === 'EXTENDED'" label="延长月数">
              <el-input-number v-model="probationForm.extend_months" :min="1" :max="6" />
            </el-form-item>
            <el-form-item label="评估说明">
              <el-input v-model="probationForm.evaluation" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-form>
          <el-alert v-if="probationResult" :closable="false" :title="probationResult" type="success" show-icon />
          <div style="margin-top: 12px; text-align: right">
            <el-button type="primary" :loading="probationSaving" @click="submitProbation">提交评估</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" header="调动申请" style="margin-bottom: 16px">
          <el-form label-width="110px">
            <el-form-item label="员工 ID">
              <el-input v-model="transferForm.employee_id" />
            </el-form-item>
            <el-form-item label="调动类型">
              <el-select v-model="transferForm.transfer_type" style="width: 100%">
                <el-option v-for="opt in transferTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标组织 ID">
              <el-input v-model="transferForm.org_id" />
            </el-form-item>
            <el-form-item label="目标岗位 ID">
              <el-input v-model="transferForm.position_id" />
            </el-form-item>
            <el-form-item label="目标职级 ID">
              <el-input v-model="transferForm.grade_id" />
            </el-form-item>
            <el-form-item label="调整后薪资">
              <el-input v-model="transferForm.salary" placeholder="数字，可选" />
            </el-form-item>
            <el-form-item label="生效日期">
              <el-date-picker v-model="transferForm.effective_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="调动原因">
              <el-input v-model="transferForm.reason" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
            <el-form-item label="调动单 ID">
              <el-input v-model="transferId" placeholder="创建后自动回填，也可手工填写" />
            </el-form-item>
          </el-form>
          <el-alert v-if="transferResult" :closable="false" :title="transferResult" type="success" show-icon />
          <div style="margin-top: 12px; text-align: right">
            <el-button :loading="transferActionSaving" @click="transferAction('approve')">审批通过</el-button>
            <el-button :loading="transferActionSaving" @click="transferAction('effective')">生效</el-button>
            <el-button type="primary" :loading="transferSaving" @click="submitTransfer">创建调动单</el-button>
          </div>
        </el-card>

        <el-card shadow="never" header="离职申请">
          <el-form label-width="110px">
            <el-form-item label="员工 ID">
              <el-input v-model="resignForm.employee_id" />
            </el-form-item>
            <el-form-item label="离职类型">
              <el-select v-model="resignForm.resign_type" style="width: 100%">
                <el-option v-for="opt in resignTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请日期">
              <el-date-picker v-model="resignForm.apply_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="最后工作日">
              <el-date-picker v-model="resignForm.last_work_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
            <el-form-item label="原因分类">
              <el-input v-model="resignForm.reason_category" maxlength="64" />
            </el-form-item>
            <el-form-item label="离职单 ID">
              <el-input v-model="resignId" placeholder="创建后自动回填，也可手工填写" />
            </el-form-item>
          </el-form>
          <el-alert v-if="resignResult" :closable="false" :title="resignResult" type="success" show-icon />
          <div style="margin-top: 12px; text-align: right">
            <el-button :loading="resignActionSaving" @click="approveResign">审批通过</el-button>
            <el-button type="primary" :loading="resignSaving" @click="submitResign">提交离职</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="历史单据" style="margin-top: 16px">
      <el-tabs v-model="historyTab" v-loading="historyLoading">
        <el-tab-pane label="入职单" name="onboarding">
          <div class="history-toolbar">
            <el-select v-model="onboardingQuery.status" placeholder="全部状态" clearable style="width: 160px">
              <el-option v-for="opt in onboardingStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button type="primary" @click="searchHistory('onboarding')">查询</el-button>
            <span class="fl-muted">点击行可将单据带入上方「入职单 ID」</span>
          </div>
          <el-table :data="onboardingRows" border stripe size="small" @row-click="useOnboardingRow">
            <el-table-column prop="id" label="入职单 ID" min-width="170" show-overflow-tooltip />
            <el-table-column label="员工" min-width="140">
              <template #default="{ row }">{{ row.employee_name || row.employee_id }}</template>
            </el-table-column>
            <el-table-column prop="onboard_date" label="入职日期" width="120" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.ob_status)" size="small">{{ row.ob_status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="offer_id" label="Offer ID" min-width="150" show-overflow-tooltip />
          </el-table>
          <el-pagination
            v-model:current-page="onboardingQuery.page"
            :page-size="onboardingQuery.size"
            :total="onboardingTotal"
            layout="total, prev, pager, next"
            style="margin-top: 8px; justify-content: flex-end"
            @current-change="loadHistory"
          />
        </el-tab-pane>

        <el-tab-pane label="调动单" name="transfer">
          <div class="history-toolbar">
            <el-select v-model="transferQuery.status" placeholder="全部状态" clearable style="width: 160px">
              <el-option v-for="opt in transferStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button type="primary" @click="searchHistory('transfer')">查询</el-button>
            <span class="fl-muted">点击行可将单据带入上方「调动单 ID」</span>
          </div>
          <el-table :data="transferRows" border stripe size="small" @row-click="useTransferRow">
            <el-table-column prop="id" label="调动单 ID" min-width="170" show-overflow-tooltip />
            <el-table-column label="员工" min-width="140">
              <template #default="{ row }">{{ row.employee_name || row.employee_id }}</template>
            </el-table-column>
            <el-table-column prop="transfer_type" label="类型" width="120" />
            <el-table-column prop="effective_date" label="生效日期" width="120" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.tf_status)" size="small">{{ row.tf_status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
          </el-table>
          <el-pagination
            v-model:current-page="transferQuery.page"
            :page-size="transferQuery.size"
            :total="transferTotal"
            layout="total, prev, pager, next"
            style="margin-top: 8px; justify-content: flex-end"
            @current-change="loadHistory"
          />
        </el-tab-pane>

        <el-tab-pane label="离职单" name="resignation">
          <div class="history-toolbar">
            <el-select v-model="resignationQuery.status" placeholder="全部状态" clearable style="width: 160px">
              <el-option
                v-for="opt in resignationStatusOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-button type="primary" @click="searchHistory('resignation')">查询</el-button>
            <span class="fl-muted">点击行可将单据带入上方「离职单 ID」</span>
          </div>
          <el-table :data="resignationRows" border stripe size="small" @row-click="useResignationRow">
            <el-table-column prop="id" label="离职单 ID" min-width="170" show-overflow-tooltip />
            <el-table-column label="员工" min-width="140">
              <template #default="{ row }">{{ row.employee_name || row.employee_id }}</template>
            </el-table-column>
            <el-table-column prop="resign_type" label="类型" width="120" />
            <el-table-column prop="apply_date" label="申请日期" width="120" />
            <el-table-column prop="last_work_date" label="最后工作日" width="120" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.rs_status)" size="small">{{ row.rs_status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="resignationQuery.page"
            :page-size="resignationQuery.size"
            :total="resignationTotal"
            layout="total, prev, pager, next"
            style="margin-top: 8px; justify-content: flex-end"
            @current-change="loadHistory"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.history-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
