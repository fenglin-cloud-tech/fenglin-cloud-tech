<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime, label, sealAppStatus } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')
const status = ref('')

const createVisible = ref(false)
const saving = ref(false)
const seals = ref<LegalRecord[]>([])
const sealsLoading = ref(false)
const form = reactive({
  contract_id: undefined as number | undefined,
  seal_id: undefined as number | undefined,
  copies: 1,
  purpose: '',
})

const gapItems = [
  '本页调用 /seal-applications；无 legal:seal:view 时后端强制 applicant 为当前用户，只有 legal:seal:apply 也能看到本人申请。',
  '印章下拉来自 /seals/available，仅返回 ACTIVE 印章；有 legal:seal:apply 即可读取，无需管理权限。',
  '申请要求合同状态为 APPROVED（用印中不可重复申请）；后端会真实校验并返回错误。',
]

async function loadSeals() {
  sealsLoading.value = true
  try {
    seals.value = await legalApi.seals.available()
  } catch (err) {
    seals.value = []
    error.value = describeError(err)
  } finally {
    sealsLoading.value = false
  }
}

function openCreate() {
  createVisible.value = true
  loadSeals()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.sealApplications.list({ status: status.value || undefined, page: page.value, size: 20 })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.contract_id || !form.seal_id) {
    ElMessage.warning('合同ID与印章ID必填')
    return
  }
  saving.value = true
  try {
    await legalApi.sealApplications.create({
      contract_id: form.contract_id,
      seal_id: form.seal_id,
      copies: form.copies,
      purpose: form.purpose || undefined,
    })
    ElMessage.success('用印申请已提交')
    createVisible.value = false
    form.purpose = ''
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="用印申请"
      description="提交与查看本人的用印申请；数据来自 /api/legal/v1/seal-applications。"
    >
      <template #actions>
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px" @change="load">
          <el-option v-for="(text, code) in sealAppStatus" :key="code" :label="text" :value="code" />
        </el-select>
        <el-button type="primary" @click="openCreate">发起用印</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="apply_no" label="申请编号" width="170" />
        <el-table-column prop="contract_id" label="合同ID" width="170" />
        <el-table-column prop="seal_id" label="印章ID" width="170" />
        <el-table-column prop="copies" label="份数" width="80" />
        <el-table-column prop="purpose" label="用途" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ label(sealAppStatus, row.status) }}</template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="20"
        :current-page="page"
        @current-change="(next: number) => { page = next; load() }"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="发起用印申请" width="520px">
      <el-form label-width="100px">
        <el-form-item label="合同ID" required><el-input-number v-model="form.contract_id" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="印章" required>
          <el-select v-model="form.seal_id" :loading="sealsLoading" placeholder="选择可用印章" style="width: 100%">
            <el-option
              v-for="seal in seals"
              :key="String(seal.id)"
              :label="`${seal.name}（${seal.seal_type || seal.seal_no}）`"
              :value="seal.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="份数"><el-input-number v-model="form.copies" :min="1" :max="99" style="width: 100%" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="form.purpose" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon title="仅可选择状态为 ACTIVE 的印章；提交后进入审批流，可在本页查看状态。" />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>
