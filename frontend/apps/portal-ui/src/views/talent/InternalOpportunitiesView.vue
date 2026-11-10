<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentFlowApplication,
  type TalentMarketItem,
} from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const market = ref<TalentMarketItem[]>([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

const applyVisible = ref(false)
const applySaving = ref(false)
const applyError = ref('')
const applyTarget = ref<TalentMarketItem | null>(null)
const applyForm = reactive({ apply_type: 'APPLY', reason: '' })

const applications = ref<TalentFlowApplication[]>([])
const applicationLoading = ref(false)
const applicationError = ref('')
const canListApplications = auth.can('talent:flow:list')

const gapItems = [
  '内部机会来自活水市场（GET /flow/market，权限 biz:talent:flow:market）；申请提交 POST /flow/applications（权限 biz:talent:flow:apply）。',
  '同一员工存在进行中的活水申请时，后端会拒绝再次申请（FLOW_IN_PROGRESS），需等当前流程结束。',
  '“我的申请”列表接口 /flow/applications 需要 talent:flow:list（管理者权限）；员工账号无此权限，页面不发起该请求并如实提示。',
]

const applyTypeOptions = [
  { value: 'APPLY', label: '应聘' },
  { value: 'TRANSFER', label: '调动' },
  { value: 'SECONDMENT', label: '借调' },
]

const flowStatusLabels: Record<string, string> = {
  PENDING: '待审批',
  APPROVING: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
}

function applyTypeLabel(value?: string) {
  return applyTypeOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function flowStatusLabel(value?: string) {
  return flowStatusLabels[value ?? ''] ?? value ?? '-'
}

function skillsText(value: unknown) {
  if (!value) return '-'
  if (Array.isArray(value)) return value.join('、')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

async function loadMarket() {
  loading.value = true
  error.value = ''
  try {
    const page = await talentApi.flow.market({
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size,
    })
    market.value = page.records
    total.value = page.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadApplications() {
  if (!canListApplications || !auth.userId) return
  applicationLoading.value = true
  applicationError.value = ''
  try {
    const page = await talentApi.flow.applications({ employeeId: auth.userId, page: 1, size: 20 })
    applications.value = page.records
  } catch (err) {
    applicationError.value = describeError(err)
  } finally {
    applicationLoading.value = false
  }
}

function openApply(row: TalentMarketItem) {
  applyTarget.value = row
  Object.assign(applyForm, { apply_type: 'APPLY', reason: '' })
  applyError.value = ''
  applyVisible.value = true
}

async function submitApply() {
  if (!applyTarget.value) return
  applySaving.value = true
  applyError.value = ''
  try {
    const result = await talentApi.flow.apply({
      demand_id: applyTarget.value.demand_id,
      apply_type: applyForm.apply_type,
      reason: applyForm.reason.trim() || undefined,
    })
    ElMessage.success(`申请已提交（${result.flow_status}）`)
    applyVisible.value = false
    await loadApplications()
  } catch (err) {
    applyError.value = describeError(err)
  } finally {
    applySaving.value = false
  }
}

onMounted(() => {
  void loadMarket()
  void loadApplications()
})
</script>

<template>
  <div>
    <PageHeader module="云才人才" title="内部机会" description="浏览内部活水市场的岗位需求，提交应聘、调动或借调申请。">
      <template #actions>
        <el-button :loading="loading" @click="loadMarket">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="岗位/组织关键词"
          clearable
          style="width: 220px"
          @keyup.enter="query.page = 1; loadMarket()"
        />
        <el-button type="primary" plain @click="query.page = 1; loadMarket()">查询</el-button>
        <span class="fl-muted">共 {{ total }} 个内部机会</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="market" v-loading="loading" border stripe>
        <el-table-column prop="demand_no" label="需求编号" width="170" />
        <el-table-column prop="position_name" label="岗位" min-width="160" />
        <el-table-column prop="org_name" label="组织" min-width="160" />
        <el-table-column label="技能要求" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ skillsText(row.skills) }}</template>
        </el-table-column>
        <el-table-column prop="headcount" label="需求人数" width="100" />
        <el-table-column prop="expected_date" label="期望到岗" width="130" />
        <el-table-column label="匹配度" width="100">
          <template #default="{ row }">{{ row.match_score ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openApply(row)">申请</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @change="loadMarket"
      />
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>我的申请</template>
      <el-alert
        v-if="!canListApplications"
        type="info"
        :closable="false"
        show-icon
        title="当前账号没有 talent:flow:list 权限，无法查询本人活水申请记录；申请提交后可在审批中心跟踪。"
      />
      <template v-else>
        <el-alert
          v-if="applicationError"
          type="error"
          :closable="false"
          show-icon
          :title="applicationError"
          style="margin-bottom: 12px"
        />
        <el-table v-if="applications.length" :data="applications" v-loading="applicationLoading" border stripe size="small">
          <el-table-column prop="demand_no" label="需求编号" width="170" />
          <el-table-column prop="employee_name" label="申请人" width="120" />
          <el-table-column label="申请类型" width="100">
            <template #default="{ row }">{{ applyTypeLabel(row.apply_type) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">{{ flowStatusLabel(row.flow_status) }}</template>
          </el-table-column>
          <el-table-column prop="created_at" label="提交时间" width="180" />
        </el-table>
        <el-empty v-else-if="!applicationLoading" description="暂无活水申请" :image-size="64" />
      </template>
    </el-card>

    <el-dialog v-model="applyVisible" title="提交内部机会申请" width="500px">
      <el-alert v-if="applyError" type="error" :closable="false" show-icon :title="applyError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="岗位">
          <el-text>{{ applyTarget?.position_name }} · {{ applyTarget?.org_name }}</el-text>
        </el-form-item>
        <el-form-item label="申请类型" required>
          <el-select v-model="applyForm.apply_type" style="width: 100%">
            <el-option v-for="opt in applyTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请理由">
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applySaving" @click="submitApply">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
