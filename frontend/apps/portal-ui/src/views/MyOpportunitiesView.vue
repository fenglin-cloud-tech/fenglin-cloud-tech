<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { crmApi, type OpportunityView, type StageLogView } from '@fenglin/api-client'
import { formatMoney } from '@fenglin/ui-admin'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const rows = ref<OpportunityView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ stage: '', status: '', page: 1, size: 10 })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<OpportunityView | null>(null)
const logs = ref<StageLogView[]>([])

const stageVisible = ref(false)
const stageSaving = ref(false)
const stageError = ref('')
const stageTarget = ref<OpportunityView | null>(null)
const stageForm = reactive({ to_stage: 'S2', amount: undefined as number | undefined, expected_close_date: '', lost_reason: '', next_step: '', opinion: '' })

const stageOptions = [
  { value: 'S1', label: 'S1 初步接触' },
  { value: 'S2', label: 'S2 需求确认' },
  { value: 'S3', label: 'S3 方案报价' },
  { value: 'S4', label: 'S4 商务谈判' },
  { value: 'WON', label: 'WON 赢单' },
  { value: 'LOST', label: 'LOST 输单' },
]

function stageLabel(value?: string) {
  return stageOptions.find((item) => item.value === value)?.label ?? value ?? '-'
}

function stageTag(value?: string) {
  if (value === 'WON') return 'success'
  if (value === 'LOST') return 'danger'
  return 'info'
}

async function load() {
  if (!auth.userId) {
    error.value = '登录上下文缺少用户 ID，无法过滤“我的商机”'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.opportunities.my({
      stage: query.stage || undefined,
      status: query.status || undefined,
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

async function openDetail(row: OpportunityView) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  logs.value = []
  try {
    detail.value = await crmApi.opportunities.myDetail(row.opportunity_id)
    logs.value = await crmApi.opportunities.myStageLogs(row.opportunity_id)
  } catch (err) {
    error.value = describeError(err)
  } finally {
    detailLoading.value = false
  }
}

function openStage(row: OpportunityView) {
  stageTarget.value = row
  Object.assign(stageForm, {
    to_stage: row.stage,
    amount: row.amount,
    expected_close_date: row.expected_close_date || '',
    lost_reason: '',
    next_step: row.next_step || '',
    opinion: '',
  })
  stageError.value = ''
  stageVisible.value = true
}

async function submitStage() {
  if (!stageTarget.value) return
  if (stageForm.to_stage === stageTarget.value.stage) {
    stageError.value = '目标阶段与当前阶段相同'
    return
  }
  stageSaving.value = true
  stageError.value = ''
  try {
    await crmApi.opportunities.changeStage(stageTarget.value.opportunity_id, {
      to_stage: stageForm.to_stage,
      amount: stageForm.amount,
      expected_close_date: stageForm.expected_close_date || undefined,
      lost_reason: stageForm.lost_reason || undefined,
      next_step: stageForm.next_step || undefined,
      opinion: stageForm.opinion || undefined,
    })
    ElMessage.success('商机阶段已更新')
    stageVisible.value = false
    await load()
  } catch (err) {
    stageError.value = describeError(err)
  } finally {
    stageSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select v-model="query.stage" placeholder="商机阶段" clearable style="width: 150px" @change="query.page = 1; load()">
          <el-option v-for="item in stageOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="商机状态" clearable style="width: 130px" @change="query.page = 1; load()">
          <el-option label="进行中" value="OPEN" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <span class="fl-muted">负责人：{{ auth.displayName }} · 共 {{ total }} 条</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="opportunity_no" label="商机编号" width="160" />
        <el-table-column prop="name" label="商机名称" min-width="170" />
        <el-table-column label="阶段" width="110">
          <template #default="{ row }">
            <el-tag :type="stageTag(row.stage)" size="small">{{ stageLabel(row.stage) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="weighted_amount" label="加权金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.weighted_amount) }}</template>
        </el-table-column>
        <el-table-column prop="win_rate" label="赢率(%)" width="100" />
        <el-table-column prop="expected_close_date" label="预计成交" width="120" />
        <el-table-column prop="next_step" label="下一步" min-width="150" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-perm="'crm:opportunity:manage'" link type="warning" @click="openStage(row)">推进阶段</el-button>
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

    <el-drawer v-model="detailVisible" title="商机详情" size="500px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="1" border size="small">
          <el-descriptions-item label="商机编号">{{ detail.opportunity_no }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="客户 ID">{{ detail.customer_id }}</el-descriptions-item>
          <el-descriptions-item label="阶段">{{ stageLabel(detail.stage) }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ detail.amount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="加权金额">{{ detail.weighted_amount ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="预计成交">{{ detail.expected_close_date || '-' }}</el-descriptions-item>
          <el-descriptions-item label="输单原因">{{ detail.lost_reason || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin: 16px 0 8px">阶段流转</h4>
        <el-empty v-if="!logs.length" description="暂无阶段变更记录" :image-size="60" />
        <el-timeline v-else>
          <el-timeline-item v-for="(item, index) in logs" :key="index" :timestamp="item.changed_at">
            {{ item.from_stage || '-' }} → {{ item.to_stage }}
            <div class="fl-muted">{{ item.opinion || '-' }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>

    <el-dialog v-model="stageVisible" title="推进商机阶段" width="480px">
      <el-alert v-if="stageError" type="error" :closable="false" show-icon :title="stageError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="目标阶段">
          <el-select v-model="stageForm.to_stage" style="width: 100%">
            <el-option v-for="item in stageOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="stageForm.amount" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预计成交">
          <el-date-picker v-model="stageForm.expected_close_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="下一步">
          <el-input v-model="stageForm.next_step" maxlength="200" />
        </el-form-item>
        <el-form-item v-if="stageForm.to_stage === 'LOST'" label="输单原因">
          <el-input v-model="stageForm.lost_reason" maxlength="200" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="stageForm.opinion" type="textarea" :rows="2" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageVisible = false">取消</el-button>
        <el-button type="primary" :loading="stageSaving" @click="submitStage">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
