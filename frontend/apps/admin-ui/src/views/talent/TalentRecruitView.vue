<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentCandidate,
  type TalentMyInterview,
  type TalentRequisition,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const gapItems = [
  '应聘记录只有创建接口，没有查询接口，面试必须手工填写应聘返回的 application_id。',
  'Offer 创建/发送/接受接口存在，但缺少岗位与职级选择器，本页暂未接入 Offer 办理。',
  '候选人标签 tags 为自由 JSON，后端无标签字典接口，本页不维护标签。',
  '面试列表只提供「我的面试」，没有按候选人/职位查询的接口。',
]

const activeTab = ref('requisition')

const requisitions = ref<TalentRequisition[]>([])
const reqTotal = ref(0)
const reqLoading = ref(false)
const reqError = ref('')
const reqQuery = reactive({ reqStatus: '', page: 1, size: 10 })

const reqDialogVisible = ref(false)
const reqSaving = ref(false)
const reqFormError = ref('')
const reqForm = reactive({
  position_id: '',
  org_id: '',
  headcount: 1,
  salary_min: '',
  salary_max: '',
  urgency: 'MID',
  reason_type: 'NEW',
  owner_id: '',
  expect_date: '',
  description: '',
  requirements: '',
})

const candidates = ref<TalentCandidate[]>([])
const candTotal = ref(0)
const candLoading = ref(false)
const candError = ref('')
const candQuery = reactive({ keyword: '', candStatus: '', page: 1, size: 10 })

const candDialogVisible = ref(false)
const candSaving = ref(false)
const candFormError = ref('')
const candForm = reactive({
  name: '',
  phone: '',
  email: '',
  gender: 'UNKNOWN',
  source: 'OFFICIAL',
  experience_years: 0,
  current_company: '',
  expected_salary: '',
})

const interviews = ref<TalentMyInterview[]>([])
const interviewLoading = ref(false)
const interviewError = ref('')

const appForm = reactive({ candidate_id: '', requisition_id: '', stage: '' })
const appSaving = ref(false)
const appResult = ref('')

const interviewForm = reactive({
  application_id: '',
  round_no: 1,
  interviewer_id: '',
  scheduled_at: '',
  duration_min: 60,
  mode: 'VIDEO',
  location: '',
})
const interviewSaving = ref(false)
const interviewResult = ref('')

const evaluateForm = reactive({ id: '', score: 0, evaluation: '', result: 'PASS', version: 0 })
const evaluateSaving = ref(false)
const evaluateResult = ref('')

const reqStatusOptions = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVING', label: '审批中' },
  { value: 'RECRUITING', label: '招聘中' },
  { value: 'PAUSED', label: '暂停' },
]

const urgencyOptions = [
  { value: 'LOW', label: '低' },
  { value: 'MID', label: '中' },
  { value: 'HIGH', label: '高' },
]

const sourceOptions = [
  { value: 'OFFICIAL', label: '官网' },
  { value: 'REFERRAL', label: '内推' },
  { value: 'JOB_SITE', label: '招聘网站' },
  { value: 'HEADHUNTER', label: '猎头' },
]

const modeOptions = [
  { value: 'VIDEO', label: '视频' },
  { value: 'ONSITE', label: '现场' },
  { value: 'PHONE', label: '电话' },
]

const resultOptions = [
  { value: 'PASS', label: '通过' },
  { value: 'HOLD', label: '待定' },
  { value: 'REJECT', label: '淘汰' },
]

function toId(text: string): string | undefined {
  return text.trim() ? text.trim() : undefined
}

async function loadRequisitions() {
  reqLoading.value = true
  reqError.value = ''
  try {
    const result = await talentApi.recruit.requisitions({
      reqStatus: reqQuery.reqStatus || undefined,
      page: reqQuery.page,
      size: reqQuery.size,
    })
    requisitions.value = result.records
    reqTotal.value = result.total
  } catch (err) {
    reqError.value = describeError(err)
  } finally {
    reqLoading.value = false
  }
}

function openCreateReq() {
  Object.assign(reqForm, {
    position_id: '',
    org_id: '',
    headcount: 1,
    salary_min: '',
    salary_max: '',
    urgency: 'MID',
    reason_type: 'NEW',
    owner_id: '',
    expect_date: '',
    description: '',
    requirements: '',
  })
  reqFormError.value = ''
  reqDialogVisible.value = true
}

async function submitReq() {
  reqSaving.value = true
  reqFormError.value = ''
  try {
    const result = await talentApi.recruit.createRequisition({
      position_id: toId(reqForm.position_id),
      org_id: toId(reqForm.org_id),
      headcount: reqForm.headcount,
      salary_min: reqForm.salary_min ? Number(reqForm.salary_min) : undefined,
      salary_max: reqForm.salary_max ? Number(reqForm.salary_max) : undefined,
      urgency: reqForm.urgency,
      reason_type: reqForm.reason_type,
      owner_id: toId(reqForm.owner_id),
      expect_date: reqForm.expect_date || undefined,
      description: reqForm.description.trim() || undefined,
      requirements: reqForm.requirements.trim() || undefined,
    })
    ElMessage.success(`招聘需求已创建（${result.req_no}）`)
    reqDialogVisible.value = false
    await loadRequisitions()
  } catch (err) {
    reqFormError.value = describeError(err)
  } finally {
    reqSaving.value = false
  }
}

async function submitRequisition(row: TalentRequisition) {
  try {
    await talentApi.recruit.submitRequisition(row.id)
    ElMessage.success('招聘需求已提交审批')
    await loadRequisitions()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function loadCandidates() {
  candLoading.value = true
  candError.value = ''
  try {
    const result = await talentApi.recruit.candidates({
      keyword: candQuery.keyword.trim() || undefined,
      candStatus: candQuery.candStatus.trim() || undefined,
      page: candQuery.page,
      size: candQuery.size,
    })
    candidates.value = result.records
    candTotal.value = result.total
  } catch (err) {
    candError.value = describeError(err)
  } finally {
    candLoading.value = false
  }
}

function openCreateCandidate() {
  Object.assign(candForm, {
    name: '',
    phone: '',
    email: '',
    gender: 'UNKNOWN',
    source: 'OFFICIAL',
    experience_years: 0,
    current_company: '',
    expected_salary: '',
  })
  candFormError.value = ''
  candDialogVisible.value = true
}

async function submitCandidate() {
  if (!candForm.name.trim()) {
    candFormError.value = '候选人姓名为必填项'
    return
  }
  candSaving.value = true
  candFormError.value = ''
  try {
    const result = await talentApi.recruit.createCandidate({
      name: candForm.name.trim(),
      phone: candForm.phone.trim() || undefined,
      email: candForm.email.trim() || undefined,
      gender: candForm.gender,
      source: candForm.source,
      experience_years: candForm.experience_years,
      current_company: candForm.current_company.trim() || undefined,
      expected_salary: candForm.expected_salary ? Number(candForm.expected_salary) : undefined,
    })
    ElMessage.success(`候选人已创建（状态 ${result.cand_status}）`)
    candDialogVisible.value = false
    await loadCandidates()
  } catch (err) {
    candFormError.value = describeError(err)
  } finally {
    candSaving.value = false
  }
}

async function loadInterviews() {
  interviewLoading.value = true
  interviewError.value = ''
  try {
    interviews.value = await talentApi.recruit.myInterviews()
  } catch (err) {
    interviewError.value = describeError(err)
  } finally {
    interviewLoading.value = false
  }
}

async function submitApplication() {
  if (!appForm.candidate_id.trim() || !appForm.requisition_id.trim()) {
    appResult.value = '候选人 ID 与招聘需求 ID 为必填项'
    return
  }
  appSaving.value = true
  appResult.value = ''
  try {
    const result = await talentApi.recruit.createApplication({
      candidate_id: appForm.candidate_id.trim(),
      requisition_id: appForm.requisition_id.trim(),
      stage: appForm.stage.trim() || undefined,
    })
    appResult.value = `应聘已创建，application_id = ${result.id}，状态 ${result.app_status}`
  } catch (err) {
    appResult.value = describeError(err)
  } finally {
    appSaving.value = false
  }
}

async function submitInterview() {
  if (!interviewForm.application_id.trim()) {
    interviewResult.value = '应聘 ID 为必填项'
    return
  }
  interviewSaving.value = true
  interviewResult.value = ''
  try {
    const result = await talentApi.recruit.createInterview({
      application_id: interviewForm.application_id.trim(),
      round_no: interviewForm.round_no,
      interviewer_id: toId(interviewForm.interviewer_id),
      scheduled_at: interviewForm.scheduled_at ? new Date(interviewForm.scheduled_at).toISOString() : undefined,
      duration_min: interviewForm.duration_min,
      mode: interviewForm.mode,
      location: interviewForm.location.trim() || undefined,
    })
    interviewResult.value = `面试已创建，interview_id = ${result.id}，状态 ${result.int_status}`
    await loadInterviews()
  } catch (err) {
    interviewResult.value = describeError(err)
  } finally {
    interviewSaving.value = false
  }
}

async function submitEvaluate() {
  if (!evaluateForm.id.trim()) {
    evaluateResult.value = '面试 ID 为必填项'
    return
  }
  evaluateSaving.value = true
  evaluateResult.value = ''
  try {
    const result = await talentApi.recruit.evaluateInterview(evaluateForm.id.trim(), {
      score: evaluateForm.score,
      evaluation: evaluateForm.evaluation.trim() || undefined,
      result: evaluateForm.result,
      version: evaluateForm.version,
    })
    evaluateResult.value = `评价已提交，状态 ${result.int_status}，结果 ${result.result}`
    await loadInterviews()
  } catch (err) {
    evaluateResult.value = describeError(err)
  } finally {
    evaluateSaving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadRequisitions(), loadCandidates(), loadInterviews()])
})
</script>

<template>
  <div>
    <PageHeader module="云才 Talent" title="招聘管理" description="招聘需求、候选人与面试评价；需求提交后进入工作流审批。">
      <template #actions>
        <el-button :loading="reqLoading" @click="loadRequisitions">刷新需求</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="activeTab" class="fl-card" style="padding: 8px 16px">
      <el-tab-pane label="招聘需求" name="requisition">
        <div class="fl-toolbar" style="margin-bottom: 12px; display: flex; gap: 8px">
          <el-button v-perm="'talent:admin:recruit:create'" type="primary" @click="openCreateReq">新建需求</el-button>
          <el-select v-model="reqQuery.reqStatus" clearable placeholder="需求状态" style="width: 160px" @change="loadRequisitions">
            <el-option v-for="opt in reqStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
        <el-alert v-if="reqError" type="error" :closable="false" show-icon :title="reqError" style="margin-bottom: 12px" />
        <el-table :data="requisitions" v-loading="reqLoading" border stripe>
          <el-table-column prop="req_no" label="需求编号" width="170" />
          <el-table-column prop="position_name" label="岗位" min-width="150" />
          <el-table-column prop="org_name" label="组织" min-width="150" />
          <el-table-column prop="headcount" label="人数" width="80" />
          <el-table-column prop="req_status" label="状态" width="110" />
          <el-table-column label="负责人" width="120">
            <template #default="{ row }">{{ row.owner?.name ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button
                v-perm="'talent:admin:recruit:submit'"
                link
                type="primary"
                :disabled="row.req_status !== 'DRAFT'"
                @click="submitRequisition(row)"
              >
                提交审批
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="reqQuery.page"
          v-model:page-size="reqQuery.size"
          :total="reqTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 16px; justify-content: flex-end"
          @change="loadRequisitions"
        />
      </el-tab-pane>

      <el-tab-pane label="候选人" name="candidate">
        <div class="fl-toolbar" style="margin-bottom: 12px; display: flex; gap: 8px">
          <el-button v-perm="'talent:admin:candidate:create'" type="primary" @click="openCreateCandidate">新建候选人</el-button>
          <el-input v-model="candQuery.keyword" placeholder="姓名关键字" clearable style="width: 200px" @keyup.enter="loadCandidates" />
          <el-input v-model="candQuery.candStatus" placeholder="候选人状态（枚举以后端为准）" clearable style="width: 230px" />
          <el-button @click="loadCandidates">查询</el-button>
        </div>
        <el-alert v-if="candError" type="error" :closable="false" show-icon :title="candError" style="margin-bottom: 12px" />
        <el-table :data="candidates" v-loading="candLoading" border stripe>
          <el-table-column prop="name" label="姓名" width="120" />
          <el-table-column prop="source" label="来源" width="120" />
          <el-table-column prop="cand_status" label="状态" width="120" />
          <el-table-column prop="experience_years" label="工作年限" width="110">
            <template #default="{ row }">{{ row.experience_years ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="current_company" label="当前公司" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.current_company ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="masked_phone" label="手机号" width="140" />
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="candQuery.page"
          v-model:page-size="candQuery.size"
          :total="candTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 16px; justify-content: flex-end"
          @change="loadCandidates"
        />
      </el-tab-pane>

      <el-tab-pane label="面试与评价" name="interview">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="应聘 → 面试 → 评价需按顺序创建，ID 由上一步返回值提供"
          style="margin-bottom: 12px"
        />
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card shadow="never" header="创建应聘">
              <el-form label-width="120px">
                <el-form-item label="候选人 ID" required>
                  <el-input v-model="appForm.candidate_id" />
                </el-form-item>
                <el-form-item label="招聘需求 ID" required>
                  <el-input v-model="appForm.requisition_id" />
                </el-form-item>
                <el-form-item label="阶段">
                  <el-input v-model="appForm.stage" placeholder="选填" />
                </el-form-item>
              </el-form>
              <el-alert v-if="appResult" :closable="false" :title="appResult" type="success" show-icon />
              <div style="margin-top: 12px; text-align: right">
                <el-button type="primary" :loading="appSaving" @click="submitApplication">创建应聘</el-button>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never" header="创建面试">
              <el-form label-width="120px">
                <el-form-item label="应聘 ID" required>
                  <el-input v-model="interviewForm.application_id" />
                </el-form-item>
                <el-form-item label="轮次">
                  <el-input-number v-model="interviewForm.round_no" :min="1" />
                </el-form-item>
                <el-form-item label="面试官 ID">
                  <el-input v-model="interviewForm.interviewer_id" />
                </el-form-item>
                <el-form-item label="面试时间">
                  <el-date-picker
                    v-model="interviewForm.scheduled_at"
                    type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ss"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-form-item label="时长（分钟）">
                  <el-input-number v-model="interviewForm.duration_min" :min="15" :step="15" />
                </el-form-item>
                <el-form-item label="形式">
                  <el-select v-model="interviewForm.mode" style="width: 100%">
                    <el-option v-for="opt in modeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="地点">
                  <el-input v-model="interviewForm.location" placeholder="视频会议链接或现场地址" />
                </el-form-item>
              </el-form>
              <el-alert v-if="interviewResult" :closable="false" :title="interviewResult" type="success" show-icon />
              <div style="margin-top: 12px; text-align: right">
                <el-button type="primary" :loading="interviewSaving" @click="submitInterview">创建面试</el-button>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" header="面试评价" style="margin-top: 16px">
          <el-form label-width="120px" inline>
            <el-form-item label="面试 ID" required>
              <el-input v-model="evaluateForm.id" style="width: 220px" />
            </el-form-item>
            <el-form-item label="评分">
              <el-input-number v-model="evaluateForm.score" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="结论">
              <el-select v-model="evaluateForm.result" style="width: 140px">
                <el-option v-for="opt in resultOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="版本号">
              <el-input-number v-model="evaluateForm.version" :min="0" />
            </el-form-item>
            <el-form-item label="评语">
              <el-input v-model="evaluateForm.evaluation" style="width: 320px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="evaluateSaving" @click="submitEvaluate">提交评价</el-button>
            </el-form-item>
          </el-form>
          <el-alert v-if="evaluateResult" :closable="false" :title="evaluateResult" type="success" show-icon />
        </el-card>

        <el-divider content-position="left">我的面试</el-divider>
        <el-alert v-if="interviewError" type="error" :closable="false" show-icon :title="interviewError" style="margin-bottom: 12px" />
        <el-table :data="interviews" v-loading="interviewLoading" border stripe>
          <el-table-column prop="id" label="面试 ID" width="190" show-overflow-tooltip />
          <el-table-column prop="candidate_name" label="候选人" width="130" />
          <el-table-column prop="position_name" label="岗位" min-width="150" />
          <el-table-column prop="round_no" label="轮次" width="80" />
          <el-table-column label="面试时间" width="180">
            <template #default="{ row }">{{ row.scheduled_at ? new Date(row.scheduled_at).toLocaleString() : '-' }}</template>
          </el-table-column>
          <el-table-column prop="mode" label="形式" width="100" />
          <el-table-column prop="int_status" label="状态" width="110" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="reqDialogVisible" title="新建招聘需求" width="620px">
      <el-alert v-if="reqFormError" type="error" :closable="false" show-icon :title="reqFormError" style="margin-bottom: 12px" />
      <el-form label-width="120px">
        <el-form-item label="岗位 ID">
          <el-input v-model="reqForm.position_id" />
        </el-form-item>
        <el-form-item label="组织 ID">
          <el-input v-model="reqForm.org_id" />
        </el-form-item>
        <el-form-item label="招聘人数">
          <el-input-number v-model="reqForm.headcount" :min="1" />
        </el-form-item>
        <el-form-item label="薪资下限">
          <el-input v-model="reqForm.salary_min" placeholder="数字，可选" />
        </el-form-item>
        <el-form-item label="薪资上限">
          <el-input v-model="reqForm.salary_max" placeholder="数字，可选" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-select v-model="reqForm.urgency" style="width: 100%">
            <el-option v-for="opt in urgencyOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求原因">
          <el-select v-model="reqForm.reason_type" style="width: 100%">
            <el-option label="新增" value="NEW" />
            <el-option label="替补" value="REPLACE" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input v-model="reqForm.owner_id" placeholder="选填" />
        </el-form-item>
        <el-form-item label="期望到岗日期">
          <el-date-picker v-model="reqForm.expect_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="职位描述">
          <el-input v-model="reqForm.description" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <el-form-item label="任职要求">
          <el-input v-model="reqForm.requirements" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reqDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reqSaving" @click="submitReq">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="candDialogVisible" title="新建候选人" width="560px">
      <el-alert v-if="candFormError" type="error" :closable="false" show-icon :title="candFormError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="姓名" required>
          <el-input v-model="candForm.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="candForm.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="candForm.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="candForm.gender" style="width: 100%">
            <el-option label="男" value="M" />
            <el-option label="女" value="F" />
            <el-option label="未知" value="UNKNOWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="candForm.source" style="width: 100%">
            <el-option v-for="opt in sourceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作年限">
          <el-input-number v-model="candForm.experience_years" :min="0" :max="60" />
        </el-form-item>
        <el-form-item label="当前公司">
          <el-input v-model="candForm.current_company" maxlength="128" />
        </el-form-item>
        <el-form-item label="期望薪资">
          <el-input v-model="candForm.expected_salary" placeholder="数字，可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="candDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="candSaving" @click="submitCandidate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
