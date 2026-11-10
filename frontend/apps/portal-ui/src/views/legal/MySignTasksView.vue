<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime, label, signTaskStatus } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const error = ref('')

const signVisible = ref(false)
const signSaving = ref(false)
const current = ref<LegalRecord | null>(null)
const signForm = reactive({
  step_up: true,
  real_name_verified: true,
  will_confirmed: true,
  auth_mode: 'OTP',
  tsa_token: '',
})

const rejectVisible = ref(false)
const rejectSaving = ref(false)
const rejectReason = ref('')

const gapItems = [
  '本页调用 /sign-tasks；无 legal:sign:view 时后端强制 signer 为当前用户，只有 legal:sign:self:execute 也能看到本人任务。',
  '签署执行要求 step_up 二次认证、实名与意愿确认，并携带 TSA 时间戳 token；tsa_token 必须由时间戳服务真实签发，页面不做伪造。',
  '签署发起（initiate）仍受工作流定义 legalContractSign 的 signerId 解析问题影响，管理端发起可能失败；本页只处理已下发到本人的任务。',
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.sign.tasks({ page: page.value, size: 20 })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    rows.value = []
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

function openSign(row: LegalRecord) {
  current.value = row
  signForm.tsa_token = ''
  signVisible.value = true
}

async function submitSign() {
  if (!current.value) return
  if (!signForm.tsa_token.trim()) {
    ElMessage.warning('请填写 TSA 时间戳 token')
    return
  }
  signSaving.value = true
  try {
    await legalApi.sign.sign(Number(current.value.id), {
      step_up: signForm.step_up,
      real_name_verified: signForm.real_name_verified,
      will_confirmed: signForm.will_confirmed,
      auth_mode: signForm.auth_mode,
      tsa_token: signForm.tsa_token.trim(),
    })
    ElMessage.success('签署完成')
    signVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    signSaving.value = false
  }
}

function openReject(row: LegalRecord) {
  current.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function submitReject() {
  if (!current.value) return
  rejectSaving.value = true
  try {
    await legalApi.sign.reject(Number(current.value.id), { reason: rejectReason.value || undefined })
    ElMessage.success('已拒签')
    rejectVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    rejectSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="我的签署任务"
      description="仅展示指派给本人的签署记录；数据来自 /api/legal/v1/sign-tasks。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="sign_task_no" label="签署任务号" width="180" />
        <el-table-column prop="contract_id" label="合同ID" width="170" />
        <el-table-column prop="signatory_id" label="签署方ID" width="170" />
        <el-table-column prop="provider_code" label="服务商" width="100" />
        <el-table-column prop="auth_mode" label="认证方式" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ label(signTaskStatus, row.status) }}</template>
        </el-table-column>
        <el-table-column label="签署时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.signed_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="primary" @click="openSign(row)">签署</el-button>
              <el-button link type="danger" @click="openReject(row)">拒签</el-button>
            </template>
            <span v-else class="muted">-</span>
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

    <el-dialog v-model="signVisible" title="执行签署" width="520px">
      <el-form label-width="110px">
        <el-form-item label="二次认证"><el-switch v-model="signForm.step_up" /></el-form-item>
        <el-form-item label="实名已核验"><el-switch v-model="signForm.real_name_verified" /></el-form-item>
        <el-form-item label="意愿已确认"><el-switch v-model="signForm.will_confirmed" /></el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="signForm.auth_mode" style="width: 100%">
            <el-option label="短信 OTP" value="OTP" />
            <el-option label="人脸识别" value="FACE" />
            <el-option label="CA 证书" value="CA" />
          </el-select>
        </el-form-item>
        <el-form-item label="TSA token" required>
          <el-input v-model="signForm.tsa_token" type="textarea" :rows="3" placeholder="时间戳服务签发的 token" />
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" show-icon title="后端会校验 tsa_token 非空；页面不生成或伪造时间戳。" />
      <template #footer>
        <el-button @click="signVisible = false">取消</el-button>
        <el-button type="primary" :loading="signSaving" @click="submitSign">确认签署</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="拒签" width="480px">
      <el-form label-width="80px">
        <el-form-item label="原因"><el-input v-model="rejectReason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSaving" @click="submitReject">确认拒签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
