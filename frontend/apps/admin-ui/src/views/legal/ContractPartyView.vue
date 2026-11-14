<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { fmtTime, label, riskLevel } from './labels'

const rows = ref<LegalRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
const error = ref('')
const keyword = ref('')

const createVisible = ref(false)
const saving = ref(false)
const form = reactive({
  name: '',
  uscc: '',
  contact_name: '',
  contact_mobile: '',
  email: '',
  legal_rep: '',
  address: '',
  risk_level: 'LOW',
  remark: '',
})

const gapItems = [
  '统一社会信用代码按哈希去重，重复 USCC 创建会返回真实 4xx 错误。',
]

const piiVisible = ref(false)
const piiSaving = ref(false)
const piiReason = ref('')
const piiParty = ref<LegalRecord | null>(null)

const requestsVisible = ref(false)
const requestsLoading = ref(false)
const myRequests = ref<LegalRecord[]>([])
const reviewRequests = ref<LegalRecord[]>([])
const approvedPartyIds = computed(
  () => new Set(myRequests.value.filter((row) => row.status === 'APPROVED').map((row) => row.party_id)),
)

const plainVisible = ref(false)
const plainParty = ref('')
const plainMobile = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await legalApi.parties.list({ keyword: keyword.value || undefined, page: page.value, size: size.value })
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
  if (!form.name.trim()) {
    ElMessage.warning('相对方名称必填')
    return
  }
  saving.value = true
  try {
    await legalApi.parties.create({
      name: form.name.trim(),
      uscc: form.uscc || undefined,
      contact_name: form.contact_name || undefined,
      contact_mobile: form.contact_mobile || undefined,
      email: form.email || undefined,
      legal_rep: form.legal_rep || undefined,
      address: form.address || undefined,
      risk_level: form.risk_level,
      remark: form.remark || undefined,
    })
    ElMessage.success('相对方已创建')
    createVisible.value = false
    form.name = ''
    form.uscc = ''
    form.contact_name = ''
    form.contact_mobile = ''
    form.email = ''
    form.legal_rep = ''
    form.address = ''
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

function openPii(row: LegalRecord) {
  piiParty.value = row
  piiReason.value = ''
  piiVisible.value = true
}

async function submitPii() {
  if (!piiParty.value) {
    return
  }
  if (!piiReason.value.trim()) {
    ElMessage.warning('请填写申请理由')
    return
  }
  piiSaving.value = true
  try {
    await legalApi.parties.applyMobile(piiParty.value.id, { reason: piiReason.value.trim() })
    ElMessage.success('已提交，等待审批')
    piiVisible.value = false
    await loadRequests()
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    piiSaving.value = false
  }
}

async function loadRequests() {
  requestsLoading.value = true
  try {
    const mine = await legalApi.parties.myMobileRequests({ page: 1, size: 50 })
    myRequests.value = mine.records
  } catch (err) {
    myRequests.value = []
  }
  try {
    const review = await legalApi.parties.mobileRequests({ status: 'PENDING', page: 1, size: 50 })
    reviewRequests.value = review.records
  } catch (err) {
    reviewRequests.value = []
  } finally {
    requestsLoading.value = false
  }
}

async function openRequests() {
  requestsVisible.value = true
  await loadRequests()
}

async function review(row: LegalRecord, approve: boolean) {
  try {
    let comment = ''
    if (!approve) {
      const result = await ElMessageBox.prompt('请填写驳回意见', '驳回申请', {
        inputValidator: (value: string) => (!!value && !!value.trim()) || '驳回意见必填',
      })
      comment = result.value
    }
    await legalApi.parties.reviewMobileRequest(row.id, { approve, comment })
    ElMessage.success(approve ? '已批准' : '已驳回')
    await loadRequests()
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(describeError(err))
    }
  }
}

async function reveal(row: LegalRecord) {
  try {
    const result = await legalApi.parties.mobile(row.party_id ?? row.id)
    plainParty.value = row.party_name || row.name || String(row.party_id ?? row.id)
    plainMobile.value = result.contact_mobile || '-'
    plainVisible.value = true
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="相对方"
      description="合同相对方档案与风险等级；数据来自 /api/legal/v1/contract-parties。"
    >
      <template #actions>
        <el-input v-model="keyword" placeholder="名称关键字" clearable style="width: 180px" @keyup.enter="changePage(1)" />
        <el-button v-perm="'legal:party:manage'" type="primary" @click="createVisible = true">新建相对方</el-button>
        <el-button v-perm="'legal:party:pii:apply'" @click="openRequests">明文申请记录</el-button>
        <el-button :loading="loading" @click="load">查询</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="uscc" label="统一社会信用代码" width="170" show-overflow-tooltip />
        <el-table-column prop="contact_name" label="联系人" width="100" />
        <el-table-column label="手机号" width="110">
          <template #default="{ row }">{{ row.contact_mobile_tail ? `****${row.contact_mobile_tail}` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" width="150" show-overflow-tooltip />
        <el-table-column prop="legal_rep" label="法定代表人" width="100" />
        <el-table-column label="风险" width="70">
          <template #default="{ row }">{{ label(riskLevel, row.risk_level) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" />
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-perm="'legal:party:pii:apply'" link type="primary" @click="openPii(row)">
              申请明文
            </el-button>
            <el-button
              v-if="approvedPartyIds.has(row.id)"
              v-perm="'legal:party:pii:view'"
              link
              type="success"
              @click="reveal(row)"
            >
              查看明文
            </el-button>
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

    <el-dialog v-model="createVisible" title="新建相对方" width="600px">
      <el-form label-width="120px">
        <el-form-item label="名称" required><el-input v-model="form.name" maxlength="200" /></el-form-item>
        <el-form-item label="统一社会信用代码"><el-input v-model="form.uscc" maxlength="32" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact_name" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.contact_mobile" placeholder="后端加密存储，仅回显后四位" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="法定代表人"><el-input v-model="form.legal_rep" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
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

    <el-dialog v-model="piiVisible" title="申请查看手机号明文" width="520px">
      <el-form label-width="90px">
        <el-form-item label="相对方">{{ piiParty?.name }}</el-form-item>
        <el-form-item label="申请理由" required>
          <el-input v-model="piiReason" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="说明业务用途，审批通过后方可查看明文" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="piiVisible = false">取消</el-button>
        <el-button type="primary" :loading="piiSaving" @click="submitPii">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="requestsVisible" title="手机号明文申请" width="900px">
      <div v-loading="requestsLoading">
        <h4 style="margin: 0 0 8px">我的申请</h4>
        <el-table :data="myRequests" border stripe size="small">
          <el-table-column prop="party_name" label="相对方" min-width="180" show-overflow-tooltip />
          <el-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="decision_comment" label="审批意见" min-width="160" show-overflow-tooltip />
          <el-table-column label="申请时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'APPROVED'"
                v-perm="'legal:party:pii:view'"
                link
                type="success"
                @click="reveal(row)"
              >
                查看明文
              </el-button>
              <span v-else class="fl-muted">-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!requestsLoading && !myRequests.length" description="暂无申请" :image-size="60" />

        <template v-if="reviewRequests.length">
          <h4 style="margin: 20px 0 8px">待我审批</h4>
          <el-table :data="reviewRequests" border stripe size="small">
            <el-table-column prop="party_name" label="相对方" min-width="180" show-overflow-tooltip />
            <el-table-column prop="contact_mobile_tail" label="手机号后四位" width="130">
              <template #default="{ row }">{{ row.contact_mobile_tail ? `****${row.contact_mobile_tail}` : '-' }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="申请理由" min-width="200" show-overflow-tooltip />
            <el-table-column prop="applied_by" label="申请人" width="180" />
            <el-table-column label="申请时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="success" @click="review(row, true)">批准</el-button>
                <el-button link type="danger" @click="review(row, false)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="plainVisible" title="手机号明文" width="420px">
      <el-form label-width="90px">
        <el-form-item label="相对方">{{ plainParty }}</el-form-item>
        <el-form-item label="手机号">{{ plainMobile }}</el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="plainVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
