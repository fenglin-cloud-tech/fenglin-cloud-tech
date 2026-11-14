<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CrmActivityDialog as ActivityDialog, PageHeader, GapNotice } from '@fenglin/ui-admin'
import { crmApi, type ContractView, type CustomerDetailView, type CustomerView } from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const rows = ref<CustomerView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({
  keyword: '',
  segment: '',
  status: '',
  owner_id: undefined as number | undefined,
  page: 1,
  size: 10,
})

const createVisible = ref(false)
const creating = ref(false)
const createError = ref('')
const createForm = reactive({
  name: '',
  short_name: '',
  industry: '',
  scale: '',
  region_code: '',
  grade: 'B',
  segment: 'B',
  owner_id: undefined as number | undefined,
  mobile: '',
  annual_revenue: undefined as number | undefined,
  arr: undefined as number | undefined,
  remark: '',
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<CustomerDetailView | null>(null)
const contracts = ref<ContractView[]>([])
const activeTab = ref('contacts')

const contactVisible = ref(false)
const contactSaving = ref(false)
const contactError = ref('')
const contactForm = reactive({
  name: '',
  position: '',
  role_type: 'DECISION',
  mobile: '',
  email: '',
  influence: 3,
  is_key: true,
})

const ownerVisible = ref(false)
const ownerSaving = ref(false)
const ownerError = ref('')
const ownerForm = reactive({ owner_id: undefined as string | number | undefined, reason: '' })

const activityVisible = ref(false)

const segmentOptions = [
  { value: 'S', label: 'S 类' },
  { value: 'A', label: 'A 类' },
  { value: 'B', label: 'B 类' },
  { value: 'C', label: 'C 类' },
]
const statusOptions = [
  { value: 'ACTIVE', label: '活跃' },
  { value: 'POTENTIAL', label: '潜在' },
  { value: 'CHURNED', label: '流失' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.customers.list(query)
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
    short_name: '',
    industry: '',
    scale: '',
    region_code: '',
    grade: 'B',
    segment: 'B',
    owner_id: undefined,
    mobile: '',
    annual_revenue: undefined,
    arr: undefined,
    remark: '',
  })
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.name) {
    createError.value = '客户名称为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    await crmApi.customers.create({
      name: createForm.name,
      short_name: createForm.short_name || undefined,
      industry: createForm.industry || undefined,
      scale: createForm.scale || undefined,
      region_code: createForm.region_code || undefined,
      grade: createForm.grade || undefined,
      segment: createForm.segment || undefined,
      owner_id: createForm.owner_id,
      mobile: createForm.mobile || undefined,
      annual_revenue: createForm.annual_revenue,
      arr: createForm.arr,
      remark: createForm.remark || undefined,
    })
    ElMessage.success('客户已创建')
    createVisible.value = false
    await load()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    creating.value = false
  }
}

async function openDetail(row: CustomerView) {
  detailVisible.value = true
  detailLoading.value = true
  activeTab.value = 'contacts'
  detail.value = null
  contracts.value = []
  try {
    detail.value = await crmApi.customers.detail(row.customer_id)
    contracts.value = await crmApi.customers.contracts(row.customer_id)
  } catch (err) {
    ElMessage.warning(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function refreshDetail() {
  if (!detail.value) return
  await openDetail(detail.value.customer)
}

function openContact() {
  Object.assign(contactForm, {
    name: '',
    position: '',
    role_type: 'DECISION',
    mobile: '',
    email: '',
    influence: 3,
    is_key: true,
  })
  contactError.value = ''
  contactVisible.value = true
}

async function submitContact() {
  if (!detail.value) return
  if (!contactForm.name) {
    contactError.value = '联系人姓名为必填项'
    return
  }
  contactSaving.value = true
  contactError.value = ''
  try {
    await crmApi.customers.createContact({
      customer_id: detail.value.customer.customer_id,
      name: contactForm.name,
      position: contactForm.position || undefined,
      role_type: contactForm.role_type,
      mobile: contactForm.mobile || undefined,
      email: contactForm.email || undefined,
      influence: contactForm.influence,
      is_key: contactForm.is_key,
    })
    ElMessage.success('联系人已添加')
    contactVisible.value = false
    await refreshDetail()
  } catch (err) {
    contactError.value = describeError(err)
  } finally {
    contactSaving.value = false
  }
}

function openOwner() {
  ownerForm.owner_id = detail.value?.customer.owner_id
  ownerForm.reason = ''
  ownerError.value = ''
  ownerVisible.value = true
}

async function submitOwner() {
  if (!detail.value) return
  if (!ownerForm.owner_id) {
    ownerError.value = '请填写新负责人 ID'
    return
  }
  ownerSaving.value = true
  ownerError.value = ''
  try {
    await crmApi.customers.changeOwner(detail.value.customer.customer_id, {
      owner_id: ownerForm.owner_id,
      reason: ownerForm.reason || undefined,
    })
    ElMessage.success('负责人已变更')
    ownerVisible.value = false
    await refreshDetail()
  } catch (err) {
    ownerError.value = describeError(err)
  } finally {
    ownerSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader module="云客 CRM · 客户" title="客户管理" description="客户档案、联系人、归属变更与客户下商机/合同；客户查重接口后端已提供，UI 暂未接入。">
      <template #actions>
        <el-button v-perm="'crm:customer:create'" type="primary" @click="openCreate">新建客户</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice
      :items="[
        '客户查重（POST /customers/check-duplicate）后端已提供，本页暂未接入 UI。',
        '客户状态仅由后端维护，本页不提供状态变更按钮。',
        '商机进入 S3 需要关键决策人：请在联系人页签勾选“关键决策人”。',
      ]"
    />

    <div class="fl-card">
      <div class="fl-toolbar">
        <el-input v-model="query.keyword" placeholder="客户名称/编号" clearable style="width: 220px" @keyup.enter="query.page = 1; load()" />
        <el-select v-model="query.segment" placeholder="全部分层" clearable style="width: 130px">
          <el-option v-for="opt in segmentOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-input-number v-model="query.owner_id" :min="1" placeholder="负责人 ID" :controls="false" style="width: 140px" />
        <el-button type="primary" plain @click="query.page = 1; load()">查询</el-button>
        <el-button @click="query.keyword = ''; query.segment = ''; query.status = ''; query.owner_id = undefined; query.page = 1; load()">重置</el-button>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="customer_no" label="客户编号" width="170" />
        <el-table-column prop="name" label="客户名称" min-width="170" />
        <el-table-column prop="industry" label="行业" width="120" />
        <el-table-column prop="segment" label="分层" width="80" />
        <el-table-column prop="grade" label="等级" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'CHURNED' ? 'info' : 'warning'" size="small">
              {{ statusOptions.find((o) => o.value === row.status)?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner_id" label="负责人" width="90" show-overflow-tooltip />
        <el-table-column prop="arr" label="ARR" width="120" />
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ row.created_at ? new Date(row.created_at).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-perm="'crm:customer:manage'" link type="warning" @click="openDetail(row).then(() => openOwner())">变更归属</el-button>
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

    <el-dialog v-model="createVisible" title="新建客户" width="600px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="客户名称" required>
          <el-input v-model="createForm.name" maxlength="200" />
        </el-form-item>
        <el-form-item label="简称">
          <el-input v-model="createForm.short_name" maxlength="64" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="createForm.industry" maxlength="64" />
        </el-form-item>
        <el-form-item label="规模">
          <el-input v-model="createForm.scale" maxlength="32" placeholder="如 200-500 人" />
        </el-form-item>
        <el-form-item label="分层">
          <el-select v-model="createForm.segment" style="width: 100%">
            <el-option v-for="opt in segmentOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="createForm.grade" style="width: 100%">
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
            <el-option label="D" value="D" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人 ID">
          <el-input-number v-model="createForm.owner_id" :min="1" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="createForm.mobile" maxlength="20" />
        </el-form-item>
        <el-form-item label="年营收">
          <el-input-number v-model="createForm.annual_revenue" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="ARR">
          <el-input-number v-model="createForm.arr" :min="0" :precision="2" style="width: 100%" />
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

    <el-drawer v-model="detailVisible" title="客户详情" size="760px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="客户编号">{{ detail.customer.customer_no }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ detail.customer.name }}</el-descriptions-item>
            <el-descriptions-item label="分层/等级">{{ detail.customer.segment || '-' }} / {{ detail.customer.grade || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.customer.status }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.customer.owner_id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="ARR">{{ detail.customer.arr ?? '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="actions">
            <el-button v-perm="'crm:contact:manage'" size="small" type="primary" @click="openContact">新增联系人</el-button>
            <el-button v-perm="'crm:customer:manage'" size="small" @click="openOwner">变更负责人</el-button>
            <el-button v-perm="'crm:activity:create'" size="small" @click="activityVisible = true">新增跟进</el-button>
          </div>

          <el-tabs v-model="activeTab">
            <el-tab-pane label="联系人" name="contacts">
              <el-table :data="detail.contacts" size="small" border>
                <el-table-column prop="name" label="姓名" width="110" />
                <el-table-column prop="position" label="职位" width="120" />
                <el-table-column prop="role_type" label="角色" width="110" />
                <el-table-column prop="mobile_masked" label="手机号" width="130" />
                <el-table-column label="关键决策人" width="110">
                  <template #default="{ row }">
                    <el-tag :type="row.is_key ? 'success' : 'info'" size="small">{{ row.is_key ? '是' : '否' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="influence" label="影响力" width="90" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="商机" name="opportunities">
              <el-table :data="detail.opportunities" size="small" border>
                <el-table-column prop="opportunity_no" label="商机编号" width="170" />
                <el-table-column prop="name" label="名称" min-width="150" />
                <el-table-column prop="stage" label="阶段" width="90" />
                <el-table-column prop="amount" label="金额" width="120" />
                <el-table-column prop="status" label="状态" width="100" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="跟进" name="activities">
              <el-table :data="detail.activities" size="small" border>
                <el-table-column prop="activity_type" label="类型" width="90" />
                <el-table-column prop="subject" label="主题" min-width="140" />
                <el-table-column prop="result" label="结果" min-width="120" />
                <el-table-column label="时间" width="160">
                  <template #default="{ row }">{{ row.occurred_at ? new Date(row.occurred_at).toLocaleString() : '-' }}</template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="合同" name="contracts">
              <el-table :data="contracts" size="small" border>
                <el-table-column prop="contract_no" label="合同编号" width="180" />
                <el-table-column prop="sla_level" label="SLA" width="80" />
                <el-table-column prop="amount" label="金额" width="120" />
                <el-table-column prop="start_date" label="开始" width="120" />
                <el-table-column prop="end_date" label="结束" width="120" />
                <el-table-column prop="status" label="状态" width="100" />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="contactVisible" title="新增联系人" width="520px">
      <el-alert v-if="contactError" type="error" :closable="false" show-icon :title="contactError" style="margin-bottom: 12px" />
      <el-form label-width="110px">
        <el-form-item label="姓名" required>
          <el-input v-model="contactForm.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="contactForm.position" maxlength="64" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="contactForm.role_type" style="width: 100%">
            <el-option label="决策人" value="DECISION" />
            <el-option label="影响者" value="INFLUENCER" />
            <el-option label="使用者" value="USER" />
            <el-option label="财务" value="FINANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="contactForm.mobile" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="contactForm.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="影响力">
          <el-input-number v-model="contactForm.influence" :min="0" :max="5" />
        </el-form-item>
        <el-form-item label="关键决策人">
          <el-switch v-model="contactForm.is_key" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactVisible = false">取消</el-button>
        <el-button type="primary" :loading="contactSaving" @click="submitContact">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ownerVisible" title="变更负责人" width="460px">
      <el-alert v-if="ownerError" type="error" :closable="false" show-icon :title="ownerError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="新负责人 ID">
          <el-input-number v-model="ownerForm.owner_id" :min="1" />
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="ownerForm.reason" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ownerVisible = false">取消</el-button>
        <el-button type="primary" :loading="ownerSaving" @click="submitOwner">确认变更</el-button>
      </template>
    </el-dialog>

    <ActivityDialog
      v-model="activityVisible"
      :biz-type="'CUSTOMER'"
      :biz-id="detail?.customer.customer_id ?? ''"
      @saved="refreshDetail"
    />
  </div>
</template>

<style scoped>
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0 4px;
}
</style>
