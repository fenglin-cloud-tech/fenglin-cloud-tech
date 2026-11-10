<script setup lang="ts">
import { reactive, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { talentApi } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const gapItems = [
  '入职、调动、离职都只有创建与状态流转接口，没有列表查询接口，页面无法回显历史单据。',
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
  </div>
</template>
