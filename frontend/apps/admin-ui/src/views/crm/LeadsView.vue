<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CrmActivityDialog as ActivityDialog, PageHeader, GapNotice } from '@fenglin/ui-admin'
import { crmApi, type CrmId, type LeadView } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<LeadView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ keyword: '', status: '', owner_id: undefined as number | undefined, page: 1, size: 10 })

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  name: '',
  contact_name: '',
  mobile: '',
  email: '',
  industry: '',
  region_code: '',
  source: 'MANUAL',
  grade: 'B',
  owner_id: undefined as number | undefined,
  remark: '',
})

const assignVisible = ref(false)
const assigning = ref(false)
const assignError = ref('')
const assignTarget = ref<LeadView | null>(null)
const assignForm = reactive({ owner_id: undefined as number | undefined, dept_id: undefined as number | undefined, reason: '' })

const convertVisible = ref(false)
const converting = ref(false)
const convertError = ref('')
const convertTarget = ref<LeadView | null>(null)
const convertForm = reactive({
  create_customer: true,
  create_contact: true,
  create_opportunity: false,
  opportunity_name: '',
  amount: undefined as number | undefined,
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<{ lead: LeadView; activities: import('@fenglin/api-client').ActivityView[] } | null>(null)
const activityVisible = ref(false)
const activityBizId = ref<CrmId>('')

const statusOptions = [
  { value: 'NEW', label: '待分配' },
  { value: 'ASSIGNED', label: '已分配' },
  { value: 'FOLLOWING', label: '跟进中' },
  { value: 'CONVERTED', label: '已转客户' },
  { value: 'INVALID', label: '无效' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.leads.list(query)
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, {
    name: '',
    contact_name: '',
    mobile: '',
    email: '',
    industry: '',
    region_code: '',
    source: 'MANUAL',
    grade: 'B',
    owner_id: undefined,
    remark: '',
  })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.name) {
    createError.value = '线索名称为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await crmApi.leads.create({
      name: createForm.name,
      contact_name: createForm.contact_name || undefined,
      mobile: createForm.mobile || undefined,
      email: createForm.email || undefined,
      industry: createForm.industry || undefined,
      region_code: createForm.region_code || undefined,
      source: createForm.source,
      grade: createForm.grade,
      owner_id: createForm.owner_id,
      remark: createForm.remark || undefined,
    })
    ElMessage.success('线索已录入')
    createVisible.value = false
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

function openAssign(row: LeadView) {
  assignTarget.value = row
  assignForm.owner_id = row.owner_id
  assignForm.dept_id = undefined
  assignForm.reason = ''
  assignError.value = ''
  assignVisible.value = true
}

async function submitAssign() {
  if (!assignTarget.value) return
  if (!assignForm.owner_id && !assignForm.dept_id) {
    assignError.value = '负责人或部门至少填写一项'
    return
  }
  assigning.value = true
  assignError.value = ''
  try {
    await crmApi.leads.assign(assignTarget.value.lead_id, {
      owner_id: assignForm.owner_id,
      dept_id: assignForm.dept_id,
      reason: assignForm.reason || undefined,
    })
    ElMessage.success('分配成功')
    assignVisible.value = false
    await load()
  } catch (err) {
    assignError.value = describeError(err)
  } finally {
    assigning.value = false
  }
}

function openConvert(row: LeadView) {
  convertTarget.value = row
  Object.assign(convertForm, {
    create_customer: true,
    create_contact: true,
    create_opportunity: false,
    opportunity_name: `${row.name}商机`,
    amount: undefined,
  })
  convertError.value = ''
  convertVisible.value = true
}

async function submitConvert() {
  if (!convertTarget.value) return
  converting.value = true
  convertError.value = ''
  try {
    const result = await crmApi.leads.convert(convertTarget.value.lead_id, {
      create_customer: convertForm.create_customer,
      create_contact: convertForm.create_contact,
      create_opportunity: convertForm.create_opportunity,
      opportunity_name: convertForm.create_opportunity ? convertForm.opportunity_name || undefined : undefined,
      amount: convertForm.create_opportunity ? convertForm.amount : undefined,
    })
    ElMessage.success(`已转客户（客户 ID ${result.customer_id}${result.opportunity_id ? `，商机 ${result.opportunity_no}` : ''}）`)
    convertVisible.value = false
    await load()
  } catch (err) {
    convertError.value = describeError(err)
  } finally {
    converting.value = false
  }
}

async function openDetail(row: LeadView) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await crmApi.leads.detail(row.lead_id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  if (!detail.value) return
  await openDetail(detail.value.lead)
}

function openActivity(row: LeadView) {
  activityBizId.value = row.lead_id
  activityVisible.value = true
}

async function markInvalid(row: LeadView) {
  try {
    const { value } = await ElMessageBox.prompt(`标记线索「${row.name}」为无效的原因`, '标记无效', {
      inputPlaceholder: '原因将写入审计日志',
    })
    await crmApi.leads.assign(row.lead_id, { owner_id: row.owner_id, reason: value })
    ElMessage.success('已记录（线索状态由后端跟进状态机维护）')
    await load()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(describeError(err))
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="云客 CRM · 线索" title="线索管理" description="线索录入、分配、跟进与转客户；无线索删除/批量导入接口，页面不提供对应按钮。">
      <template #actions>
        <el-button v-perm="'crm:lead:create'" type="primary" @click="openCreate">录入线索</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '线索查重（POST /leads/check-duplicate）后端已提供，本页暂未接入 UI。',
        '线索状态由后端维护；标记无效当前仅记录审计备注，不改变状态机。',
        '转客户默认同时创建联系人，可勾选同时创建商机。',
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="query.keyword" placeholder="线索名称/联系人/手机号" clearable style="width: 240px" @keyup.enter="query.page = 1; load()" />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input-number v-model="query.owner_id" :min="1" placeholder="负责人 ID" :controls="false" style="width: 140px" />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="query.keyword = ''; query.status = ''; query.owner_id = undefined; query.page = 1; load()">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="lead_no" label="线索编号" width="170" />
        <el-table-column prop="name" label="线索名称" min-width="150" />
        <el-table-column prop="contact_name" label="联系人" width="110" />
        <el-table-column prop="mobile_masked" label="手机号" width="130" />
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column prop="grade" label="等级" width="80" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CONVERTED' ? 'success' : row.status === 'INVALID' ? 'info' : 'primary'" size="small">
              {{ statusOptions.find((o) => o.value === row.status)?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner_id" label="负责人" width="90" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-perm="'crm:lead:assign'" link type="primary" @click="openAssign(row)">分配</el-button>
            <el-button v-perm="'crm:activity:create'" link type="success" @click="openActivity(row)">跟进</el-button>
            <el-button
              v-if="row.status !== 'CONVERTED'"
              v-perm="'crm:lead:transfer'"
              link
              type="warning"
              @click="openConvert(row)"
            >
              转客户
            </el-button>
            <el-button v-perm="'crm:lead:assign'" link type="info" @click="markInvalid(row)">标记</el-button>
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

    <el-dialog v-model="createVisible" title="录入线索" width="580px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="线索名称" required>
          <el-input v-model="createForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="createForm.contact_name" maxlength="64" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="createForm.mobile" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="createForm.industry" maxlength="64" />
        </el-form-item>
        <el-form-item label="来源">
          <!-- 与后端 LeadService.SOURCES 白名单及 crm_lead_source 字典（V1_0_2）保持一致 -->
          <el-select v-model="createForm.source" style="width: 100%">
            <el-option label="人工录入" value="MANUAL" />
            <el-option label="批量导入" value="IMPORT" />
            <el-option label="表单收集" value="FORM" />
            <el-option label="接口接入" value="API" />
            <el-option label="渠道伙伴" value="PARTNER" />
            <el-option label="舆情线索" value="OPINION" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="createForm.grade" style="width: 100%">
            <el-option label="A 级" value="A" />
            <el-option label="B 级" value="B" />
            <el-option label="C 级" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input-number v-model="createForm.owner_id" :min="1" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="分配线索" width="480px">
      <el-alert v-if="assignError" type="error" :closable="false" show-icon :title="assignError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="负责人 ID">
          <el-input-number v-model="assignForm.owner_id" :min="1" />
        </el-form-item>
        <el-form-item label="部门 ID">
          <el-input-number v-model="assignForm.dept_id" :min="1" />
        </el-form-item>
        <el-form-item label="分配原因">
          <el-input v-model="assignForm.reason" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="submitAssign">确认分配</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="convertVisible" title="线索转客户" width="500px">
      <el-alert v-if="convertError" type="error" :closable="false" show-icon :title="convertError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="创建客户">
          <el-switch v-model="convertForm.create_customer" disabled />
        </el-form-item>
        <el-form-item label="创建联系人">
          <el-switch v-model="convertForm.create_contact" />
        </el-form-item>
        <el-form-item label="同时创建商机">
          <el-switch v-model="convertForm.create_opportunity" />
        </el-form-item>
        <template v-if="convertForm.create_opportunity">
          <el-form-item label="商机名称">
            <el-input v-model="convertForm.opportunity_name" maxlength="128" />
          </el-form-item>
          <el-form-item label="预计金额">
            <el-input-number v-model="convertForm.amount" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="converting" @click="submitConvert">确认转换</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="线索详情" size="640px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="编号">{{ detail.lead.lead_no }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.lead.status }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ detail.lead.name }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ detail.lead.contact_name || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detail.lead.mobile_masked || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ detail.lead.source || '-' }}</el-descriptions-item>
            <el-descriptions-item label="等级">{{ detail.lead.grade || '-' }}</el-descriptions-item>
            <el-descriptions-item label="评分">{{ detail.lead.score }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.lead.owner_id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="转客户 ID">{{ detail.lead.converted_customer_id || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-head">
            <h3>跟进记录</h3>
            <el-button v-perm="'crm:activity:create'" size="small" type="primary" @click="openActivity(detail.lead)">新增跟进</el-button>
          </div>
          <el-table :data="detail.activities" size="small" border>
            <el-table-column prop="activity_type" label="类型" width="90" />
            <el-table-column prop="subject" label="主题" min-width="130" />
            <el-table-column prop="result" label="结果" min-width="120" />
            <el-table-column prop="next_plan" label="下一步" min-width="120" />
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ row.occurred_at ? new Date(row.occurred_at).toLocaleString() : '-' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <ActivityDialog v-model="activityVisible" :biz-type="'LEAD'" :biz-id="activityBizId" @saved="refreshDetail" />
  </div>
</template>

<style scoped>
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 10px;
}
.section-head h3 {
  margin: 0;
  font-size: 14px;
}
</style>
