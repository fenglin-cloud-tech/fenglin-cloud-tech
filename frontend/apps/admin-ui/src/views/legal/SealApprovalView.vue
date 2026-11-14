<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader, ID_COLUMN_WIDTH } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime, label, sealAppStatus } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')
const status = ref('')

const approveVisible = ref(false)
const approveSaving = ref(false)
const approveForm = reactive({ id: 0, action: 'APPROVE', comment: '' })

const executeVisible = ref(false)
const executeSaving = ref(false)
const executeForm = reactive({ id: 0, image_ref: '' })

const gapItems = [
  '列表需 legal:seal:view；无该权限但具备 legal:seal:apply 时后端只返回本人申请。',
  '审批要求 legal:seal:approve，执行要求 legal:seal:execute；写操作均校验 Idempotency-Key。',
]

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

function openApprove(row: LegalRecord, action: string) {
  approveForm.id = row.id
  approveForm.action = action
  approveForm.comment = ''
  approveVisible.value = true
}

async function submitApprove() {
  approveSaving.value = true
  try {
    await legalApi.sealApplications.approve(approveForm.id, { action: approveForm.action, comment: approveForm.comment || undefined })
    ElMessage.success(approveForm.action === 'APPROVE' ? '用印申请已通过' : '用印申请已驳回')
    approveVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    approveSaving.value = false
  }
}

function openExecute(row: LegalRecord) {
  executeForm.id = row.id
  executeForm.image_ref = ''
  executeVisible.value = true
}

async function submitExecute() {
  if (!executeForm.image_ref.trim()) {
    ElMessage.warning('请填写用印结果图片引用 image_ref')
    return
  }
  executeSaving.value = true
  try {
    await legalApi.sealApplications.execute(executeForm.id, { image_ref: executeForm.image_ref.trim() })
    ElMessage.success('用印已执行')
    executeVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    executeSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="用印审批"
      description="用印申请审批与执行；数据来自 /api/legal/v1/seal-applications。"
    >
      <template #actions>
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px" @change="(page = 1), load()">
          <el-option v-for="(text, code) in sealAppStatus" :key="code" :label="text" :value="code" />
        </el-select>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="apply_no" label="申请编号" width="130" show-overflow-tooltip />
        <el-table-column prop="contract_id" label="合同ID" width="170" />
        <el-table-column prop="seal_id" label="印章ID" width="170" />
        <el-table-column prop="seal_type" label="印章类型" width="90" />
        <el-table-column prop="copies" label="份数" width="70" />
        <el-table-column prop="applicant_id" label="申请人" :width="ID_COLUMN_WIDTH" show-overflow-tooltip />
        <el-table-column prop="purpose" label="用途" min-width="100" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small">{{ label(sealAppStatus, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button v-perm="'legal:seal:approve'" link type="success" @click="openApprove(row, 'APPROVE')">通过</el-button>
              <el-button v-perm="'legal:seal:approve'" link type="danger" @click="openApprove(row, 'REJECT')">驳回</el-button>
            </template>
            <el-button v-if="row.status === 'APPROVED'" v-perm="'legal:seal:execute'" link type="primary" @click="openExecute(row)">执行用印</el-button>
          </template>
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

    <el-dialog v-model="approveVisible" :title="approveForm.action === 'APPROVE' ? '通过用印申请' : '驳回用印申请'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="意见"><el-input v-model="approveForm.comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approveSaving" @click="submitApprove">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="executeVisible" title="执行用印" width="480px">
      <el-form label-width="100px">
        <el-form-item label="结果图片" required><el-input v-model="executeForm.image_ref" placeholder="文件中台 file_ref" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executeVisible = false">取消</el-button>
        <el-button type="primary" :loading="executeSaving" @click="submitExecute">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>
