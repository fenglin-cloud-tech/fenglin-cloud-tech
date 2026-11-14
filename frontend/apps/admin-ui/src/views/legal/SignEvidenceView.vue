<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime, label, signTaskStatus } from './labels'

const tab = ref('records')

const records = ref<LegalRecord[]>([])
const recordTotal = ref(0)
const recordPage = ref(1)
const recordLoading = ref(false)
const recordError = ref('')
const recordFilter = reactive({ contract_id: '', status: '' })

const evidences = ref<LegalRecord[]>([])
const evidenceTotal = ref(0)
const evidencePage = ref(1)
const evidenceLoading = ref(false)
const evidenceError = ref('')
const evidenceFilter = reactive({ contract_id: '', evidence_type: '' })
const chainResult = ref<LegalRecord | null>(null)

const signatories = ref<LegalRecord[]>([])
const signatoryContractId = ref('')
const signatoryLoading = ref(false)
const signatoryError = ref('')

const initiateVisible = ref(false)
const initiateSaving = ref(false)
const initiateForm = reactive({ contract_id: undefined as string | undefined, provider_code: 'ESIGN' })

const gapItems = [
  '签署发起 /contracts/{id}/sign/initiate 依赖工作流定义 legalContractSign；后端当前报 "Cannot resolve identifier \'signerId\'"，发起会失败，页面如实回显错误。',
  '签署记录查看需 legal:sign:view；存证查看需 legal:evidence:view，核验需 legal:evidence:verify。',
  '存证内容哈希与链式校验由后端计算，前端只展示结果，不构造哈希。',
]

async function loadRecords() {
  recordLoading.value = true
  recordError.value = ''
  try {
    const result = await legalApi.sign.records({
      contract_id: recordFilter.contract_id ? recordFilter.contract_id : undefined,
      status: recordFilter.status || undefined,
      page: recordPage.value,
      size: 20,
    })
    records.value = result.records
    recordTotal.value = result.total
  } catch (err) {
    records.value = []
    recordError.value = describeError(err)
  } finally {
    recordLoading.value = false
  }
}

async function loadEvidences() {
  evidenceLoading.value = true
  evidenceError.value = ''
  try {
    const result = await legalApi.evidences.list({
      contract_id: evidenceFilter.contract_id ? evidenceFilter.contract_id : undefined,
      evidence_type: evidenceFilter.evidence_type || undefined,
      page: evidencePage.value,
      size: 20,
    })
    evidences.value = result.records
    evidenceTotal.value = result.total
  } catch (err) {
    evidences.value = []
    evidenceError.value = describeError(err)
  } finally {
    evidenceLoading.value = false
  }
}

async function loadSignatories() {
  if (!signatoryContractId.value) {
    ElMessage.warning('请填写合同ID')
    return
  }
  signatoryLoading.value = true
  signatoryError.value = ''
  try {
    signatories.value = await legalApi.signatories.list(signatoryContractId.value)
  } catch (err) {
    signatories.value = []
    signatoryError.value = describeError(err)
  } finally {
    signatoryLoading.value = false
  }
}

async function verifyEvidence(row: LegalRecord) {
  try {
    const result = await legalApi.evidences.verify(row.id)
    ElMessage.success(`核验完成：${result.verified === false ? '不一致' : '通过'}`)
    await loadEvidences()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

async function chainVerify() {
  if (!evidenceFilter.contract_id) {
    ElMessage.warning('请填写合同ID')
    return
  }
  try {
    chainResult.value = await legalApi.evidences.chainVerify(evidenceFilter.contract_id)
  } catch (err) {
    chainResult.value = null
    ElMessage.error(describeError(err))
  }
}

async function initiateSign() {
  if (!initiateForm.contract_id) {
    ElMessage.warning('合同ID必填')
    return
  }
  initiateSaving.value = true
  try {
    await legalApi.sign.initiate(initiateForm.contract_id, { provider_code: initiateForm.provider_code })
    ElMessage.success('签署任务已发起')
    initiateVisible.value = false
    await loadRecords()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    initiateSaving.value = false
  }
}

function switchTab(value: string) {
  tab.value = value
  if (value === 'evidences' && evidences.value.length === 0) loadEvidences()
}

onMounted(loadRecords)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="签署与存证"
      description="签署记录、签署方与存证核验；数据来自 /api/legal/v1/sign-records、/contract-signatories、/evidences。"
    >
      <template #actions>
        <el-button v-perm="'legal:sign:initiate'" type="primary" @click="initiateVisible = true">发起签署</el-button>
        <el-button v-perm="'legal:evidence:verify'" plain @click="chainVerify">按合同链式核验</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-tabs v-model="tab" @tab-change="switchTab">
      <el-tab-pane label="签署记录" name="records">
        <el-alert v-if="recordError" type="error" :closable="false" show-icon :title="recordError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="recordFilter.contract_id" placeholder="合同ID" clearable style="width: 160px" />
            <el-select v-model="recordFilter.status" placeholder="全部状态" clearable style="width: 130px">
              <el-option v-for="(text, code) in signTaskStatus" :key="code" :label="text" :value="code" />
            </el-select>
            <el-button :loading="recordLoading" @click="loadRecords">查询</el-button>
          </div>
          <el-table :data="records" v-loading="recordLoading" border stripe max-height="520">
            <el-table-column prop="sign_task_no" label="签署任务号" width="160" show-overflow-tooltip />
            <el-table-column prop="contract_id" label="合同ID" width="170" />
            <el-table-column prop="signatory_id" label="签署方ID" width="170" />
            <el-table-column prop="provider_code" label="服务商" width="100" />
            <el-table-column label="实名" width="80">
              <template #default="{ row }">{{ row.real_name_verified ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="意愿" width="80">
              <template #default="{ row }">{{ row.will_confirmed ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="auth_mode" label="认证方式" width="100" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ label(signTaskStatus, row.status) }}</template>
            </el-table-column>
            <el-table-column label="签署时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.signed_at) }}</template>
            </el-table-column>
            <el-table-column prop="doc_hash" label="文档哈希" width="160" show-overflow-tooltip />
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="recordTotal"
            :page-size="20"
            :current-page="recordPage"
            @current-change="(next: number) => { recordPage = next; loadRecords() }"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="签署方" name="signatories">
        <el-alert v-if="signatoryError" type="error" :closable="false" show-icon :title="signatoryError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="signatoryContractId" placeholder="合同ID" clearable style="width: 160px" @keyup.enter="loadSignatories" />
            <el-button :loading="signatoryLoading" @click="loadSignatories">查询签署方</el-button>
          </div>
          <el-table :data="signatories" v-loading="signatoryLoading" border stripe max-height="520">
            <el-table-column prop="signer_name" label="签署人" width="130" />
            <el-table-column prop="party_type" label="类型" width="100" />
            <el-table-column prop="party_id" label="主体ID" width="170" />
            <el-table-column prop="signer_mobile_tail" label="手机尾号" width="100" />
            <el-table-column prop="seal_mode" label="用印方式" width="110" />
            <el-table-column prop="sign_mode" label="签署顺序" width="100" />
            <el-table-column prop="sign_order" label="序号" width="80" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="存证" name="evidences">
        <el-alert v-if="evidenceError" type="error" :closable="false" show-icon :title="evidenceError" style="margin-bottom: 12px" />
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="evidenceFilter.contract_id" placeholder="合同ID" clearable style="width: 160px" />
            <el-select v-model="evidenceFilter.evidence_type" placeholder="全部类型" clearable style="width: 150px">
              <el-option label="签署" value="SIGN" />
              <el-option label="用印" value="SEAL" />
              <el-option label="归档" value="ARCHIVE" />
            </el-select>
            <el-button :loading="evidenceLoading" @click="loadEvidences">查询</el-button>
          </div>
          <el-table :data="evidences" v-loading="evidenceLoading" border stripe max-height="520">
            <el-table-column prop="evidence_no" label="存证编号" width="180" />
            <el-table-column prop="contract_id" label="合同ID" width="170" />
            <el-table-column prop="evidence_type" label="类型" width="100" />
            <el-table-column prop="content_hash" label="内容哈希" width="180" show-overflow-tooltip />
            <el-table-column prop="worm_ref" label="WORM 引用" min-width="180" show-overflow-tooltip />
            <el-table-column label="已核验" width="90">
              <template #default="{ row }">{{ row.verified ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="核验时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.verified_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-perm="'legal:evidence:verify'" link type="primary" @click="verifyEvidence(row)">核验</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            style="margin-top: 12px; justify-content: flex-end"
            layout="total, prev, pager, next"
            :total="evidenceTotal"
            :page-size="20"
            :current-page="evidencePage"
            @current-change="(next: number) => { evidencePage = next; loadEvidences() }"
          />
          <el-descriptions v-if="chainResult" :column="2" border size="small" style="margin-top: 12px">
            <el-descriptions-item label="链式校验">{{ chainResult.valid === false ? '不一致' : '通过' }}</el-descriptions-item>
            <el-descriptions-item label="存证数量">{{ chainResult.count ?? chainResult.evidence_count ?? '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="initiateVisible" title="发起签署" width="480px">
      <el-form label-width="100px">
        <el-form-item label="合同ID" required><el-input v-model="initiateForm.contract_id" placeholder="19 位数字合同ID" maxlength="25" /></el-form-item>
        <el-form-item label="服务商"><el-input v-model="initiateForm.provider_code" /></el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon title="签署发起依赖工作流定义 legalContractSign，当前后端定义缺少 signerId 解析，提交可能失败。" />
      <template #footer>
        <el-button @click="initiateVisible = false">取消</el-button>
        <el-button type="primary" :loading="initiateSaving" @click="initiateSign">发起</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
