<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  financeApi,
  type FinanceLedger,
  type FinanceSubject,
  type FinanceVoucher,
  type FinanceVoucherEntry,
  type FinanceVoucherTemplate,
} from '@fenglin/api-client'
import { describeError } from '@/utils/error'

const PAGE_SIZE = 50

const ledgers = ref<FinanceLedger[]>([])
const subjects = ref<FinanceSubject[]>([])
const vouchers = ref<FinanceVoucher[]>([])
const templates = ref<FinanceVoucherTemplate[]>([])
const selectedLedgerId = ref<number | ''>('')
const query = reactive({ periodCode: '', status: '', page: 1 })
const activeTab = ref('vouchers')
const loading = ref(false)
const error = ref('')

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<FinanceVoucher | null>(null)

const createVisible = ref(false)
const createSaving = ref(false)
const createError = ref('')
const voucherForm = reactive({
  voucher_date: new Date().toISOString().slice(0, 10),
  summary: '',
  currency_code: 'CNY',
})
const entryRows = ref<Array<{ summary: string; subject_code: string; direction: 'DEBIT' | 'CREDIT'; amount: string }>>([])

const templateVisible = ref(false)
const templateError = ref('')
const templateForm = reactive({
  template_code: '',
  template_name: '',
  biz_type: 'EXPENSE',
  debit_subject_code: '',
  credit_subject_code: '',
})

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审核',
  AUDITED: '已审核',
  POSTED: '已记账',
  REVERSED: '已冲销',
}
const statusTagTypes: Record<string, string> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  AUDITED: 'primary',
  POSTED: 'success',
  REVERSED: 'danger',
}

const entryTotals = computed(() => {
  let debit = 0
  let credit = 0
  for (const row of entryRows.value) {
    const amount = Number(row.amount || 0)
    if (row.direction === 'DEBIT') debit += amount
    else credit += amount
  }
  return { debit, credit, balanced: Math.abs(debit - credit) < 0.005 && debit > 0 }
})

const gapItems = [
  '凭证列表接口只返回数组、不返回总数，无法做精确分页；本页固定每页 50 条，用“上一页/下一页”按返回条数判断是否还有数据。',
  '后端没有凭证反审核接口（只有 audit），审核后只能通过 reverse 冲销；页面按真实状态机收敛按钮。',
  '凭证附件/影像接口缺失，本页不展示附件；批量导入接口要求后端内部 JSON 结构，前端不臆造格式，暂不提供导入表单。',
]

function money(value?: string | number | null) {
  if (value === undefined || value === null || value === '') return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusLabel(value?: string) {
  return value ? statusLabels[value] ?? value : '-'
}

function statusTag(value?: string) {
  return (value && statusTagTypes[value]) || 'info'
}

function directionLabel(value?: string) {
  return value === 'CREDIT' ? '贷' : '借'
}

async function loadLedgers() {
  try {
    ledgers.value = await financeApi.ledgers.list()
    if (!selectedLedgerId.value && ledgers.value.length) {
      selectedLedgerId.value = ledgers.value[0].id
    }
    await loadSubjects()
    await loadVouchers()
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadSubjects() {
  if (!selectedLedgerId.value) return
  try {
    subjects.value = await financeApi.ledgers.subjects(Number(selectedLedgerId.value))
  } catch (err) {
    error.value = describeError(err)
  }
}

async function loadVouchers() {
  if (!selectedLedgerId.value) {
    vouchers.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    vouchers.value = await financeApi.vouchers.list({
      ledgerId: Number(selectedLedgerId.value),
      periodCode: query.periodCode || undefined,
      status: query.status || undefined,
      page: query.page,
      size: PAGE_SIZE,
    })
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  try {
    templates.value = await financeApi.vouchers.templates()
  } catch (err) {
    templates.value = []
    error.value = describeError(err)
  }
}

async function openDetail(row: FinanceVoucher) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await financeApi.vouchers.detail(row.id)
  } catch (err) {
    ElMessage.error(describeError(err))
  } finally {
    detailLoading.value = false
  }
}

async function runAction(row: FinanceVoucher, action: 'submit' | 'audit' | 'post' | 'reverse') {
  const labels = { submit: '提交', audit: '审核', post: '记账', reverse: '冲销' }
  try {
    await ElMessageBox.confirm(`确认对凭证 ${row.voucher_no} 执行「${labels[action]}」？`, '凭证操作', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const result = await financeApi.vouchers[action](row.id)
    ElMessage.success(`凭证 ${result.voucher_no} 已${labels[action]}，当前状态 ${statusLabel(result.status)}`)
    await loadVouchers()
  } catch (err) {
    ElMessage.error(describeError(err))
  }
}

function addEntry() {
  entryRows.value.push({
    summary: '',
    subject_code: subjects.value[0]?.subject_code ?? '',
    direction: 'DEBIT',
    amount: '',
  })
}

function removeEntry(index: number) {
  entryRows.value.splice(index, 1)
}

function openCreate() {
  voucherForm.voucher_date = new Date().toISOString().slice(0, 10)
  voucherForm.summary = ''
  createError.value = ''
  entryRows.value = []
  addEntry()
  addEntry()
  createVisible.value = true
}

async function submitCreate() {
  if (!selectedLedgerId.value) {
    createError.value = '请先选择账套'
    return
  }
  if (!voucherForm.voucher_date) {
    createError.value = '请选择凭证日期'
    return
  }
  if (!entryTotals.value.balanced) {
    createError.value = `借贷必须平衡且大于 0，当前借方 ${money(entryTotals.value.debit)} / 贷方 ${money(entryTotals.value.credit)}`
    return
  }
  if (entryRows.value.some((row) => !row.subject_code || !row.amount)) {
    createError.value = '每条分录都需要选择科目并填写金额'
    return
  }
  createSaving.value = true
  createError.value = ''
  try {
    const created = await financeApi.vouchers.create({
      ledger_id: Number(selectedLedgerId.value),
      voucher_date: voucherForm.voucher_date,
      summary: voucherForm.summary.trim() || undefined,
      currency_code: voucherForm.currency_code,
      entries: entryRows.value.map((row) => ({
        summary: row.summary.trim() || voucherForm.summary.trim() || undefined,
        subject_code: row.subject_code,
        direction: row.direction,
        amount: row.amount,
      })),
    })
    ElMessage.success(`凭证 ${created.voucher_no} 已创建（草稿）`)
    createVisible.value = false
    activeTab.value = 'vouchers'
    await loadVouchers()
  } catch (err) {
    createError.value = describeError(err)
  } finally {
    createSaving.value = false
  }
}

async function submitTemplate() {
  if (!templateForm.template_code.trim() || !templateForm.template_name.trim()) {
    templateError.value = '模板编码与名称不能为空'
    return
  }
  if (!templateForm.debit_subject_code || !templateForm.credit_subject_code) {
    templateError.value = '借方与贷方科目不能为空'
    return
  }
  try {
    await financeApi.vouchers.createTemplate({
      template_code: templateForm.template_code.trim(),
      template_name: templateForm.template_name.trim(),
      biz_type: templateForm.biz_type,
      debit_subject_code: templateForm.debit_subject_code,
      credit_subject_code: templateForm.credit_subject_code,
    })
    ElMessage.success('凭证模板已保存')
    templateVisible.value = false
    await loadTemplates()
  } catch (err) {
    templateError.value = describeError(err)
  }
}

watch(selectedLedgerId, async () => {
  query.page = 1
  await loadSubjects()
  await loadVouchers()
})

onMounted(async () => {
  await loadLedgers()
  await loadTemplates()
})
</script>

<template>
  <div>
    <PageHeader
      module="云财 Finance"
      title="凭证工作台"
      description="凭证录入、提交、审核、记账与冲销，完整审计链（哈希链）随凭证一起查询。"
    >
      <template #actions>
        <el-select v-model="selectedLedgerId" placeholder="选择账套" style="width: 220px">
          <el-option v-for="ledger in ledgers" :key="ledger.id" :label="`${ledger.ledger_code} ${ledger.ledger_name}`" :value="ledger.id" />
        </el-select>
        <el-button v-perm="'biz:finance:voucher:create'" type="primary" @click="openCreate">录入凭证</el-button>
        <el-button :loading="loading" @click="loadVouchers">刷新</el-button>
      </template>
    </PageHeader>

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />
    <GapNotice :items="gapItems" />

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="凭证列表" name="vouchers">
          <div class="fl-toolbar">
            <el-input v-model="query.periodCode" placeholder="期间，如 202611" clearable style="width: 160px" @keyup.enter="query.page = 1; loadVouchers()" />
            <el-select v-model="query.status" placeholder="凭证状态" clearable style="width: 140px" @change="query.page = 1; loadVouchers()">
              <el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" />
            </el-select>
            <el-button type="primary" plain @click="query.page = 1; loadVouchers()">查询</el-button>
            <span class="fl-muted">当前第 {{ query.page }} 页，共 {{ vouchers.length }} 条</span>
          </div>

          <el-table :data="vouchers" v-loading="loading" border stripe>
            <el-table-column prop="voucher_no" label="凭证号" width="160" />
            <el-table-column prop="period_code" label="期间" width="100" />
            <el-table-column prop="voucher_date" label="凭证日期" width="120" />
            <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
            <el-table-column label="借方合计" width="130" align="right">
              <template #default="{ row }">{{ money(row.total_debit) }}</template>
            </el-table-column>
            <el-table-column label="贷方合计" width="130" align="right">
              <template #default="{ row }">{{ money(row.total_credit) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button link @click="openDetail(row)">详情</el-button>
                <el-button v-if="row.status === 'DRAFT'" v-perm="'biz:finance:voucher:submit'" link type="primary" @click="runAction(row, 'submit')">提交</el-button>
                <el-button v-if="row.status === 'SUBMITTED'" v-perm="'biz:finance:voucher:audit'" link type="primary" @click="runAction(row, 'audit')">审核</el-button>
                <el-button v-if="row.status === 'AUDITED'" v-perm="'biz:finance:voucher:post'" link type="success" @click="runAction(row, 'post')">记账</el-button>
                <el-button v-if="row.status === 'POSTED'" v-perm="'biz:finance:voucher:reverse'" link type="danger" @click="runAction(row, 'reverse')">冲销</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="fl-toolbar" style="margin-top: 12px">
            <el-button :disabled="query.page <= 1" @click="query.page -= 1; loadVouchers()">上一页</el-button>
            <el-button :disabled="vouchers.length < 50" @click="query.page += 1; loadVouchers()">下一页</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="凭证模板" name="templates">
          <div class="fl-toolbar">
            <el-button v-perm="'tenant:finance:ledger:manage'" type="primary" plain @click="templateVisible = true">新增模板</el-button>
            <span class="fl-muted">共 {{ templates.length }} 个模板（业务单据生成凭证时使用）</span>
          </div>
          <el-table :data="templates" border stripe>
            <el-table-column prop="template_code" label="模板编码" width="160" />
            <el-table-column prop="template_name" label="模板名称" min-width="180" />
            <el-table-column prop="biz_type" label="业务类型" width="140" />
            <el-table-column prop="debit_subject_code" label="借方科目" width="140" />
            <el-table-column prop="credit_subject_code" label="贷方科目" width="140" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-drawer v-model="detailVisible" title="凭证详情" size="720px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="凭证号">{{ detail.voucher_no }}</el-descriptions-item>
            <el-descriptions-item label="期间">{{ detail.period_code }}</el-descriptions-item>
            <el-descriptions-item label="凭证日期">{{ detail.voucher_date }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="借方合计">{{ money(detail.total_debit) }}</el-descriptions-item>
            <el-descriptions-item label="贷方合计">{{ money(detail.total_credit) }}</el-descriptions-item>
            <el-descriptions-item label="链哈希" :span="2">{{ detail.chain_hash || '-' }}</el-descriptions-item>
          </el-descriptions>

          <h4 style="margin: 16px 0 8px">分录</h4>
          <el-table :data="detail.entries" border size="small">
            <el-table-column prop="line_no" label="#" width="50" />
            <el-table-column prop="summary" label="摘要" min-width="150" />
            <el-table-column prop="subject_code" label="科目" width="110" />
            <el-table-column prop="subject_name" label="科目名称" min-width="120" />
            <el-table-column label="方向" width="70">
              <template #default="{ row }">{{ directionLabel(row.direction) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="currency_code" label="币种" width="70" />
            <el-table-column label="本位币金额" width="130" align="right">
              <template #default="{ row }">{{ money(row.amount_base) }}</template>
            </el-table-column>
          </el-table>

          <h4 style="margin: 16px 0 8px">审计链</h4>
          <el-table :data="detail.audit_trail" border size="small" empty-text="暂无审计链记录">
            <el-table-column prop="chain_seq" label="序号" width="70" />
            <el-table-column prop="action" label="动作" width="110" />
            <el-table-column prop="operator_name" label="操作人" width="120" />
            <el-table-column prop="occurred_at" label="时间" width="180" />
            <el-table-column prop="record_hash" label="记录哈希" min-width="200" show-overflow-tooltip />
          </el-table>
        </template>
        <el-empty v-else-if="!detailLoading" description="未获取到凭证详情" />
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" title="录入凭证" width="860px">
      <el-alert v-if="createError" type="error" :closable="false" show-icon :title="createError" style="margin-bottom: 12px" />
      <el-form label-width="90px">
        <el-form-item label="凭证日期" required>
          <el-date-picker v-model="voucherForm.voucher_date" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
        </el-form-item>
        <el-form-item label="摘要"><el-input v-model="voucherForm.summary" maxlength="200" /></el-form-item>
        <el-form-item label="币种"><el-input v-model="voucherForm.currency_code" maxlength="8" style="width: 120px" /></el-form-item>
      </el-form>

      <el-table :data="entryRows" border size="small">
        <el-table-column label="摘要" min-width="170">
          <template #default="{ row }"><el-input v-model="row.summary" maxlength="200" /></template>
        </el-table-column>
        <el-table-column label="科目" width="200">
          <template #default="{ row }">
            <el-select v-model="row.subject_code" filterable style="width: 100%">
              <el-option v-for="subject in subjects" :key="subject.subject_code" :label="`${subject.subject_code} ${subject.subject_name}`" :value="subject.subject_code" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="100">
          <template #default="{ row }">
            <el-select v-model="row.direction" style="width: 100%">
              <el-option label="借" value="DEBIT" />
              <el-option label="贷" value="CREDIT" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140">
          <template #default="{ row }"><el-input v-model="row.amount" placeholder="0.00" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }"><el-button link type="danger" @click="removeEntry($index)">删除</el-button></template>
        </el-table-column>
      </el-table>
      <div class="fl-toolbar" style="margin-top: 10px">
        <el-button plain @click="addEntry">增加分录</el-button>
        <span class="fl-muted">
          借方 {{ money(entryTotals.debit) }} / 贷方 {{ money(entryTotals.credit) }}
          <el-tag size="small" :type="entryTotals.balanced ? 'success' : 'warning'" style="margin-left: 8px">
            {{ entryTotals.balanced ? '平衡' : '不平衡' }}
          </el-tag>
        </span>
      </div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSaving" :disabled="!entryTotals.balanced" @click="submitCreate">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateVisible" title="新增凭证模板" width="520px">
      <el-alert v-if="templateError" type="error" :closable="false" show-icon :title="templateError" style="margin-bottom: 12px" />
      <el-form label-width="100px">
        <el-form-item label="模板编码" required><el-input v-model="templateForm.template_code" maxlength="32" /></el-form-item>
        <el-form-item label="模板名称" required><el-input v-model="templateForm.template_name" maxlength="64" /></el-form-item>
        <el-form-item label="业务类型"><el-input v-model="templateForm.biz_type" maxlength="32" /></el-form-item>
        <el-form-item label="借方科目" required><el-input v-model="templateForm.debit_subject_code" maxlength="32" /></el-form-item>
        <el-form-item label="贷方科目" required><el-input v-model="templateForm.credit_subject_code" maxlength="32" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
