<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { contractCategory, contractStatus, fmtAmount, fmtTime, label, riskLevel, sealStatus as sealStatusName, signStatusMap as signStatusMapName } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref('')
const filters = reactive({ keyword: '', status: '', category: '', owner_id: '' })

const detailVisible = ref(false)
const detail = ref<LegalRecord | null>(null)
const detailLoading = ref(false)

const createVisible = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  category: 'SALES',
  amount: undefined as number | undefined,
  currency: 'CNY',
  start_date: '',
  end_date: '',
  owner_id: undefined as number | undefined,
  org_id: undefined as number | undefined,
  risk_level: 'LOW',
  remark: '',
})

const gapItems = [
  '合同列表 /contracts 已做本人过滤：无 legal:contract:view 时后端强制 owner_id=当前用户，仅 legal:contract:view:self 可用。',
  '提交/审批会调用工作流服务（18084），工作流不可用时返回真实错误，页面不做假成功。',
  '附件上传、条款全文检索依赖文件中台，本页只展示后端已返回的版本与条款数量。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.contracts.list({
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      category: filters.category || undefined,
      owner_id: filters.owner_id ? Number(filters.owner_id) : undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: LegalRecord) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await legalApi.contracts.get(Number(row.id))
  } catch (err) {
    detail.value = null
    ElMessage.error(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function submit() {
  if (!detail.value) return
  try {
    await legalApi.contracts.submit(Number(detail.value.id), {})
    ElMessage.success('已提交审批')
    await openDetail(detail.value)
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function approve() {
  if (!detail.value) return
  try {
    await legalApi.contracts.approve(Number(detail.value.id), { action: 'APPROVE' })
    ElMessage.success('审批已通过')
    await openDetail(detail.value)
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function archive() {
  if (!detail.value) return
  try {
    await legalApi.contracts.archive(Number(detail.value.id))
    ElMessage.success('已归档')
    await openDetail(detail.value)
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function terminate() {
  if (!detail.value) return
  try {
    await legalApi.contracts.terminate(Number(detail.value.id), { reason: '管理端终止' })
    ElMessage.success('已终止')
    await openDetail(detail.value)
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function verifyIntegrity() {
  if (!detail.value) return
  try {
    const result = await legalApi.contracts.verifyIntegrity(Number(detail.value.id))
    ElMessage.success(`完整性校验：${result.valid === false ? '不一致' : '通过'}`)
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function removeDraft(row: LegalRecord) {
  try {
    await ElMessageBox.confirm(`确认删除草稿合同 ${row.contract_no}？`, '删除确认', { type: 'warning' })
    await legalApi.contracts.remove(Number(row.id))
    ElMessage.success('已删除')
    await load()
  } catch (err) {
    if (err !== 'cancel') ElMessage.error(describeError(err))
  }
}

async function create() {
  if (!form.name.trim()) {
    ElMessage.warning('合同名称必填')
    return
  }
  saving.value = true
  try {
    await legalApi.contracts.create({
      name: form.name.trim(),
      category: form.category,
      amount: form.amount ?? undefined,
      currency: form.currency,
      start_date: form.start_date || undefined,
      end_date: form.end_date || undefined,
      owner_id: form.owner_id ?? undefined,
      org_id: form.org_id ?? undefined,
      risk_level: form.risk_level,
      remark: form.remark || undefined,
    })
    ElMessage.success('合同草稿已创建')
    createVisible.value = false
    form.name = ''
    form.amount = undefined
    form.remark = ''
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

function changePage(next: number) {
  page.value = next
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="合同台账"
      description="合同查询、起草、提交审批、归档与完整性校验；数据来自 /api/legal/v1/contracts。"
    >
      <template #actions>
        <el-input v-model="filters.keyword" placeholder="合同名称/编号" clearable style="width: 180px" @keyup.enter="changePage(1)" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 130px" @change="changePage(1)">
          <el-option v-for="(text, code) in contractStatus" :key="code" :label="text" :value="code" />
        </el-select>
        <el-select v-model="filters.category" placeholder="全部类别" clearable style="width: 130px" @change="changePage(1)">
          <el-option v-for="(text, code) in contractCategory" :key="code" :label="text" :value="code" />
        </el-select>
        <el-input v-model="filters.owner_id" placeholder="负责人ID" clearable style="width: 120px" @keyup.enter="changePage(1)" />
        <el-button v-perm="'legal:contract:create'" type="primary" @click="createVisible = true">起草合同</el-button>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe max-height="560" @row-click="openDetail">
        <el-table-column prop="contract_no" label="合同编号" width="150" />
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="类别" width="110">
          <template #default="{ row }">{{ label(contractCategory, row.category) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ label(contractStatus, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ fmtAmount(row.amount, row.currency) }}</template>
        </el-table-column>
        <el-table-column label="风险" width="80">
          <template #default="{ row }">{{ label(riskLevel, row.risk_level) }}</template>
        </el-table-column>
        <el-table-column prop="owner_id" label="负责人" width="90" />
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" v-perm="'legal:contract:edit'" link type="danger" @click.stop="removeDraft(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="changePage"
      />
    </el-card>

    <el-drawer v-model="detailVisible" title="合同详情" size="720px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="合同编号">{{ detail.contract_no }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ label(contractStatus, detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="名称" :span="2">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ label(contractCategory, detail.category) }}</el-descriptions-item>
            <el-descriptions-item label="金额">{{ fmtAmount(detail.amount, detail.currency) }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ detail.owner_id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="组织">{{ detail.org_id ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="用印状态">{{ label(sealStatusName, detail.seal_status) }}</el-descriptions-item>
            <el-descriptions-item label="签署状态">{{ label(signStatusMapName, detail.sign_status) }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ fmtTime(detail.created_at) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ fmtTime(detail.updated_at) }}</el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">操作</el-divider>
          <el-space wrap>
            <el-button v-perm="'legal:contract:submit'" type="primary" plain @click="submit">提交审批</el-button>
            <el-button v-perm="'legal:contract:approve'" type="success" plain @click="approve">审批通过</el-button>
            <el-button v-perm="'legal:contract:archive'" plain @click="archive">归档</el-button>
            <el-button v-perm="'legal:contract:terminate'" type="danger" plain @click="terminate">终止</el-button>
            <el-button v-perm="'legal:contract:integrity'" plain @click="verifyIntegrity">完整性校验</el-button>
          </el-space>

          <el-divider content-position="left">版本（{{ (detail.versions ?? []).length }}）</el-divider>
          <el-table :data="detail.versions ?? []" size="small" border max-height="200">
            <el-table-column prop="version_no" label="版本" width="70" />
            <el-table-column prop="created_stage" label="阶段" width="100" />
            <el-table-column prop="change_summary" label="变更说明" min-width="160" />
            <el-table-column prop="content_hash" label="内容哈希" width="160" show-overflow-tooltip />
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
          </el-table>

          <el-divider content-position="left">审批记录（{{ (detail.approvals ?? []).length }}）</el-divider>
          <el-table :data="detail.approvals ?? []" size="small" border max-height="180">
            <el-table-column prop="approver_id" label="审批人" width="90" />
            <el-table-column prop="action" label="动作" width="100" />
            <el-table-column prop="node_name" label="节点" width="130" />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.acted_at) }}</template>
            </el-table-column>
          </el-table>

          <el-divider content-position="left">签署方（{{ (detail.signatories ?? []).length }}）</el-divider>
          <el-table :data="detail.signatories ?? []" size="small" border max-height="200">
            <el-table-column prop="signer_name" label="签署人" width="120" />
            <el-table-column prop="party_type" label="类型" width="100" />
            <el-table-column prop="signer_mobile_tail" label="手机尾号" width="100" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" title="起草合同" width="600px">
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="(text, code) in contractCategory" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额"><el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="币种"><el-input v-model="form.currency" /></el-form-item>
        <el-form-item label="开始日期"><el-date-picker v-model="form.start_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="form.end_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="负责人ID"><el-input-number v-model="form.owner_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="组织ID"><el-input-number v-model="form.org_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="form.risk_level" style="width: 100%">
            <el-option v-for="(text, code) in riskLevel" :key="code" :label="text" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
