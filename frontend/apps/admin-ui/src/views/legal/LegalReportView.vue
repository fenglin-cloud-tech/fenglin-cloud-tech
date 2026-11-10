<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import { legalApi, type LegalRecord } from '@fenglin/api-client'
import { describeError } from '@/utils/error'
import { contractStatus, dataLevel, fmtAmount, label } from './labels'

const contractDim = ref('STATUS')
const dlpDim = ref('CHANNEL')

const contractReport = ref<LegalRecord | null>(null)
const contractLoading = ref(false)
const contractError = ref('')

const dlpReport = ref<LegalRecord | null>(null)
const dlpLoading = ref(false)
const dlpError = ref('')

const signReport = ref<LegalRecord | null>(null)
const signLoading = ref(false)
const signError = ref('')

const gapItems = [
  '报表权限点来自 hub_permission 真实值：admin:legal:report:contract、admin:legal:report:dlp、admin:legal:report:sign。',
  '报表为后端实时聚合（legal_contract / legal_dlp_event / legal_evidence），不落报表快照表，因此不支持历史区间回看。',
  '图表为表格化呈现，未引入图表库；比例字段由后端返回小数（0.5 表示 50%）。',
]

function pct(value: unknown): string {
  const num = Number(value)
  if (Number.isNaN(num)) return '-'
  return `${(num * 100).toFixed(1)}%`
}

function contractDimLabel(row: LegalRecord): string {
  const key = row.status ?? row.key ?? row.dim_value ?? '-'
  if (contractDim.value === 'CATEGORY') return row.category ?? key
  return label(contractStatus, String(key))
}

function dlpDimLabel(row: LegalRecord): string {
  const key = row.key ?? row.channel_code ?? row.data_level ?? '-'
  if (dlpDim.value === 'DATA_LEVEL') return label(dataLevel, String(key))
  return String(key)
}

async function loadContracts() {
  contractLoading.value = true
  contractError.value = ''
  try {
    contractReport.value = await legalApi.reports.contracts(contractDim.value)
  } catch (err) {
    contractReport.value = null
    contractError.value = describeError(err)
  } finally {
    contractLoading.value = false
  }
}

async function loadDlp() {
  dlpLoading.value = true
  dlpError.value = ''
  try {
    dlpReport.value = await legalApi.reports.dlp(dlpDim.value)
  } catch (err) {
    dlpReport.value = null
    dlpError.value = describeError(err)
  } finally {
    dlpLoading.value = false
  }
}

async function loadSign() {
  signLoading.value = true
  signError.value = ''
  try {
    signReport.value = await legalApi.reports.signCompliance()
  } catch (err) {
    signReport.value = null
    signError.value = describeError(err)
  } finally {
    signLoading.value = false
  }
}

const stats = reactive([
  { key: 'contract_total', label: '合同总数', value: 0 },
  { key: 'contract_amount', label: '合同总额', value: 0 },
  { key: 'dlp_events', label: 'DLP 事件数', value: 0 },
  { key: 'block_rate', label: '阻断率', value: 0 },
])

onMounted(async () => {
  await Promise.all([loadContracts(), loadDlp(), loadSign()])
  stats[0].value = contractReport.value?.summary?.total ?? 0
  stats[1].value = contractReport.value?.summary?.amount ?? 0
  stats[2].value = (dlpReport.value?.items ?? []).reduce((sum: number, item: LegalRecord) => sum + Number(item.events ?? 0), 0)
  stats[3].value = dlpReport.value?.metrics?.block_rate ?? 0
})
</script>

<template>
  <div>
    <PageHeader
      module="云法 Legal"
      title="法务报表"
      description="合同、DLP 与签署合规实时聚合；数据来自 /api/legal/v1/admin/reports/*。"
    >
      <template #actions>
        <el-button :loading="contractLoading || dlpLoading || signLoading" @click="loadContracts(); loadDlp(); loadSign()">刷新</el-button>
      </template>
    </PageHeader>

    <GapNotice :items="gapItems" />

    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :span="6" v-for="item in stats" :key="item.key">
        <el-card shadow="never">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ item.key === 'contract_amount' ? fmtAmount(item.value) : item.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>合同分布</span>
              <el-radio-group v-model="contractDim" size="small" @change="loadContracts">
                <el-radio-button value="STATUS">按状态</el-radio-button>
                <el-radio-button value="CATEGORY">按类别</el-radio-button>
                <el-radio-button value="ORG">按组织</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-alert v-if="contractError" type="error" :closable="false" show-icon :title="contractError" style="margin-bottom: 8px" />
          <el-table :data="contractReport?.items ?? []" v-loading="contractLoading" size="small" border>
            <el-table-column label="维度" min-width="120">
              <template #default="{ row }">{{ contractDimLabel(row) }}</template>
            </el-table-column>
            <el-table-column prop="total" label="合同数" width="100" />
            <el-table-column label="金额" width="150" align="right">
              <template #default="{ row }">{{ fmtAmount(row.amount) }}</template>
            </el-table-column>
          </el-table>
          <div class="summary">
            合计 {{ contractReport?.summary?.total ?? 0 }} 份 / {{ fmtAmount(contractReport?.summary?.amount) }}
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>DLP 事件分布</span>
              <el-radio-group v-model="dlpDim" size="small" @change="loadDlp">
                <el-radio-button value="CHANNEL">按通道</el-radio-button>
                <el-radio-button value="DATA_LEVEL">按级别</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-alert v-if="dlpError" type="error" :closable="false" show-icon :title="dlpError" style="margin-bottom: 8px" />
          <el-table :data="dlpReport?.items ?? []" v-loading="dlpLoading" size="small" border>
            <el-table-column label="维度" min-width="120">
              <template #default="{ row }">{{ dlpDimLabel(row) }}</template>
            </el-table-column>
            <el-table-column prop="events" label="事件" width="80" />
            <el-table-column prop="blocked" label="阻断" width="80" />
            <el-table-column prop="released" label="放行" width="80" />
            <el-table-column prop="false_positive" label="误报" width="80" />
          </el-table>
          <el-descriptions :column="3" border size="small" style="margin-top: 10px">
            <el-descriptions-item label="阻断率">{{ pct(dlpReport?.metrics?.block_rate) }}</el-descriptions-item>
            <el-descriptions-item label="放行率">{{ pct(dlpReport?.metrics?.release_rate) }}</el-descriptions-item>
            <el-descriptions-item label="误报率">{{ pct(dlpReport?.metrics?.false_positive_rate) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div class="card-head">
          <span>签署合规（{{ signReport?.period ?? '-' }}）</span>
          <el-button size="small" :loading="signLoading" @click="loadSign">刷新</el-button>
        </div>
      </template>
      <el-alert v-if="signError" type="error" :closable="false" show-icon :title="signError" style="margin-bottom: 8px" />
      <el-descriptions v-if="signReport" :column="4" border size="small">
        <el-descriptions-item label="已签署合同">{{ signReport.signed_contracts ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="实名覆盖率">{{ pct(signReport.real_name_coverage) }}</el-descriptions-item>
        <el-descriptions-item label="意愿确认覆盖率">{{ pct(signReport.will_confirm_coverage) }}</el-descriptions-item>
        <el-descriptions-item label="时间戳覆盖率">{{ pct(signReport.tsa_coverage) }}</el-descriptions-item>
        <el-descriptions-item label="存证完整率">{{ pct(signReport.evidence_complete_rate) }}</el-descriptions-item>
        <el-descriptions-item label="已核验存证">{{ signReport.verified_evidence ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="缺失存证合同">{{ signReport.contracts_without_evidence ?? 0 }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!signLoading" description="暂无数据" :image-size="60" />
    </el-card>
  </div>
</template>

<style scoped>
.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.summary {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: right;
}
</style>
