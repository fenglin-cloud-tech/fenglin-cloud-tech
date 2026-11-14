<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { PageHeader, GapNotice, ID_COLUMN_WIDTH, formatMoney } from '@fenglin/ui-admin'
import { crmApi, type OpportunityView, type PipelineView, type StageLogView } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<OpportunityView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const pipeline = ref<PipelineView | null>(null)
const query = reactive({
  stage: '',
  status: '',
  customer_id: undefined as string | undefined,
  owner_id: undefined as number | undefined,
  page: 1,
  size: 10,
})

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  name: '',
  customer_id: undefined as string | number | undefined,
  amount: undefined as number | undefined,
  stage: 'S1',
  expected_close_date: '',
  next_step: '',
  source: '',
  owner_id: undefined as number | undefined,
})

const stageVisible = ref(false)
const stageSaving = ref(false)
const stageError = ref('')
const stageTarget = ref<OpportunityView | null>(null)
const stageForm = reactive({
  to_stage: 'S2',
  opinion: '',
  amount: undefined as number | undefined,
  expected_close_date: '',
  lost_reason: '',
  next_step: '',
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailLogs = ref<StageLogView[]>([])
const detailRow = ref<OpportunityView | null>(null)

const stageOptions = [
  { value: 'S1', label: 'S1 初步接洽' },
  { value: 'S2', label: 'S2 需求确认' },
  { value: 'S3', label: 'S3 方案报价' },
  { value: 'S4', label: 'S4 商务谈判' },
  { value: 'WON', label: 'WON 赢单' },
  { value: 'LOST', label: 'LOST 输单' },
]
const statusOptions = [
  { value: 'OPEN', label: '进行中' },
  { value: 'WON', label: '已赢单' },
  { value: 'LOST', label: '已输单' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [result, pipe] = await Promise.all([
      crmApi.opportunities.list(query),
      crmApi.opportunities.pipeline().catch(() => null),
    ])
    rows.value = result.records
    total.value = result.total
    pipeline.value = pipe
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, {
    name: '',
    customer_id: undefined,
    amount: undefined,
    stage: 'S1',
    expected_close_date: '',
    next_step: '',
    source: '',
    owner_id: undefined,
  })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.name || !createForm.customer_id) {
    createError.value = '商机名称与客户 ID 为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await crmApi.opportunities.create({
      name: createForm.name,
      customer_id: createForm.customer_id,
      amount: createForm.amount,
      stage: createForm.stage,
      expected_close_date: createForm.expected_close_date || undefined,
      next_step: createForm.next_step || undefined,
      source: createForm.source || undefined,
      owner_id: createForm.owner_id,
    })
    ElMessage.success('商机已创建')
    createVisible.value = false
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

function openStage(row: OpportunityView) {
  stageTarget.value = row
  Object.assign(stageForm, {
    to_stage: row.stage,
    opinion: '',
    amount: row.amount,
    expected_close_date: row.expected_close_date || '',
    lost_reason: '',
    next_step: row.next_step || '',
  })
  stageError.value = ''
  stageVisible.value = true
}

async function submitStage() {
  if (!stageTarget.value) return
  stageSaving.value = true
  stageError.value = ''
  try {
    await crmApi.opportunities.changeStage(stageTarget.value.opportunity_id, {
      to_stage: stageForm.to_stage,
      opinion: stageForm.opinion || undefined,
      amount: stageForm.amount,
      expected_close_date: stageForm.expected_close_date || undefined,
      lost_reason: stageForm.lost_reason || undefined,
      next_step: stageForm.next_step || undefined,
    })
    ElMessage.success('阶段已更新')
    stageVisible.value = false
    await load()
  } catch (err) {
    stageError.value = describeError(err)
  } finally {
    stageSaving.value = false
  }
}

async function openDetail(row: OpportunityView) {
  detailVisible.value = true
  detailLoading.value = true
  detailRow.value = row
  detailLogs.value = []
  try {
    detailLogs.value = await crmApi.opportunities.stageLogs(row.opportunity_id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="云客 CRM · 商机" title="商机管理" description="商机新建、阶段推进与阶段日志；阶段规则由后端校验（S3 需关键决策人，S4 需审批中/已批准报价）。">
      <template #actions>
        <el-button v-perm="'crm:opportunity:manage'" type="primary" @click="openCreate">新建商机</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '进入 S3 需同时具备金额、预计成交日期与客户关键决策人（联系人 is_key=true）。',
        '进入 S4 需该商机存在审批中或已批准的报价单。',
        'WON 由后端按阶段规则处理；输单必须填写输单原因。',
      ]"
    />

    <div v-if="pipeline" class="pipeline">
      <div v-for="item in pipeline.stages" :key="item.stage" class="pipe-item">
        <span>{{ item.stage }}</span>
        <strong>{{ item.count }}</strong>
        <small>{{ item.amount ?? 0 }}</small>
      </div>
      <div class="pipe-item total">
        <span>加权总额</span>
        <strong>{{ pipeline.total_weighted_amount ?? 0 }}</strong>
        <small>进行中 {{ pipeline.open_count }}</small>
      </div>
    </div>

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-select v-model="query.stage" placeholder="全部阶段" clearable style="width: 150px">
          <el-option v-for="opt in stageOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input v-model="query.customer_id" placeholder="客户 ID" clearable style="width: 160px" />
        <el-input-number v-model="query.owner_id" :min="1" placeholder="负责人 ID" :controls="false" style="width: 140px" />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="query.stage = ''; query.status = ''; query.customer_id = undefined; query.owner_id = undefined; query.page = 1; load()">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="opportunity_no" label="商机编号" width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="商机名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="customer_id" label="客户 ID" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="stage" label="阶段" width="80" />
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="win_rate" label="赢率" width="80" />
        <el-table-column prop="expected_close_date" label="预计成交" width="110" />
        <el-table-column prop="owner_id" label="负责人" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">阶段日志</el-button>
            <el-button v-perm="'crm:opportunity:manage'" link type="warning" @click="openStage(row)">推进</el-button>
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
    </div>

    <el-dialog v-model="createVisible" title="新建商机" width="560px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="商机名称" required>
          <el-input v-model="createForm.name" maxlength="160" />
        </el-form-item>
        <el-form-item label="客户 ID" required>
          <el-input v-model="createForm.customer_id" placeholder="雪花 ID，按文本透传" clearable />
        </el-form-item>
        <el-form-item label="预计金额">
          <el-input-number v-model="createForm.amount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="初始阶段">
          <el-select v-model="createForm.stage" style="width: 100%">
            <el-option v-for="opt in stageOptions.filter((s) => ['S1', 'S2', 'S3', 'S4'].includes(s.value))" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计成交日">
          <el-date-picker v-model="createForm.expected_close_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="下一步">
          <el-input v-model="createForm.next_step" maxlength="200" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="createForm.source" maxlength="64" />
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input-number v-model="createForm.owner_id" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stageVisible" title="推进商机阶段" width="540px">
      <el-alert v-if="stageError" type="error" :closable="false" show-icon :title="stageError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="当前阶段">
          <el-tag>{{ stageTarget?.stage }}</el-tag>
        </el-form-item>
        <el-form-item label="目标阶段">
          <el-select v-model="stageForm.to_stage" style="width: 100%">
            <el-option v-for="opt in stageOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计金额">
          <el-input-number v-model="stageForm.amount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预计成交日">
          <el-date-picker v-model="stageForm.expected_close_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="下一步">
          <el-input v-model="stageForm.next_step" maxlength="200" />
        </el-form-item>
        <el-form-item label="输单原因">
          <el-input v-model="stageForm.lost_reason" maxlength="200" placeholder="目标阶段为 LOST 时必填" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="stageForm.opinion" type="textarea" :rows="2" maxlength="300" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stageVisible = false">取消</el-button>
        <el-button type="primary" :loading="stageSaving" @click="submitStage">确认推进</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="阶段变更日志" size="620px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detailRow" :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="商机">{{ detailRow.opportunity_no }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detailRow.name }}</el-descriptions-item>
          <el-descriptions-item label="阶段">{{ detailRow.stage }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ detailRow.amount ?? '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detailLogs" size="small" border>
          <el-table-column prop="from_stage" label="原阶段" width="90" />
          <el-table-column prop="to_stage" label="新阶段" width="90" />
          <el-table-column prop="amount_snapshot" label="金额快照" width="120" />
          <el-table-column prop="opinion" label="说明" min-width="140" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ row.changed_at ? new Date(row.changed_at).toLocaleString() : '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.pipeline {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}
.pipe-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 120px;
  padding: 10px 14px;
  background: var(--fl-color-surface);
  border: 1px solid var(--fl-color-border);
  border-radius: var(--fl-radius-md);
}
.pipe-item span {
  font-size: 12px;
  color: var(--fl-color-text-secondary);
}
.pipe-item strong {
  font-size: 18px;
}
.pipe-item small {
  color: var(--fl-color-text-secondary);
}
.pipe-item.total {
  background: var(--fl-color-primary-soft);
}
</style>
