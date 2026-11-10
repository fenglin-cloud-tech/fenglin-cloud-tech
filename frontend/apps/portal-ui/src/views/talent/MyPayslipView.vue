<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentId,
  type TalentMyPayslip,
  type TalentMyPayslipPeriod,
} from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const periods = ref<TalentMyPayslipPeriod[]>([])
const selectedPeriodId = ref<TalentId | ''>('')
const mySlips = ref<TalentMyPayslip[]>([])
const loading = ref(false)
const slipLoading = ref(false)
const error = ref('')
const slipError = ref('')

const gapItems = [
  '本页调用员工自助接口 /payroll/slips/my，服务端只按当前登录用户返回本人工资条，不接收 employee_id 参数。',
  '金额字段需 talent:payroll:amount:view：未持有时服务端不返回金额字段并标记 amount_masked=true，页面显示“已脱敏”。',
  '暂无工资条 PDF 下载接口，本页仅做在线查看；查看全员薪酬请走管理端薪酬页面。',
]

const recStatusLabels: Record<string, string> = {
  DRAFT: '待发放',
  PENDING: '待发放',
  PAID: '已发放',
  CONFIRMED: '已确认',
}

function recStatusLabel(value?: string) {
  return value ? recStatusLabels[value] ?? value : '-'
}

function money(value?: number, masked?: boolean) {
  if (masked) return '已脱敏'
  return value === undefined || value === null ? '-' : Number(value).toFixed(2)
}

async function loadPeriods() {
  loading.value = true
  error.value = ''
  try {
    periods.value = await talentApi.payroll.mySlipPeriods()
    if (!selectedPeriodId.value && periods.value.length) {
      selectedPeriodId.value = periods.value[0].id
    }
    await loadSlips()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadSlips() {
  if (!selectedPeriodId.value) {
    mySlips.value = []
    return
  }
  slipLoading.value = true
  slipError.value = ''
  try {
    const page = await talentApi.payroll.mySlips({ periodId: selectedPeriodId.value, page: 1, size: 50 })
    mySlips.value = page.records
  } catch (err) {
    slipError.value = describeError(err)
    mySlips.value = []
  } finally {
    slipLoading.value = false
  }
}

onMounted(loadPeriods)
</script>

<template>
  <div>
    <PageHeader module="云才人才" title="我的薪酬单" description="查看本人各薪资周期的工资条；数据由员工自助接口按登录用户返回。" />

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select
          v-model="selectedPeriodId"
          placeholder="选择薪资周期"
          style="width: 260px"
          @change="loadSlips"
        >
          <el-option
            v-for="period in periods"
            :key="String(period.id)"
            :label="`${period.period_code}（${period.start_date} ~ ${period.end_date}）`"
            :value="period.id"
          />
        </el-select>
        <el-button :loading="loading || slipLoading" @click="loadPeriods">刷新</el-button>
        <span class="fl-muted">{{ auth.displayName }}</span>
      </div>

      <el-alert
        v-if="slipError"
        type="error"
        :closable="false"
        show-icon
        :title="slipError"
        style="margin-bottom: 12px"
      />

      <div v-loading="slipLoading">
        <el-empty v-if="!mySlips.length && !slipError" description="该周期暂无本人工资条" :image-size="80" />
        <el-table v-else :data="mySlips" border stripe>
          <el-table-column prop="period_code" label="薪资周期" width="120" />
          <el-table-column prop="pay_date" label="发放日期" width="120" />
          <el-table-column prop="employee_name" label="员工" width="130" />
          <el-table-column label="基本工资" width="130">
            <template #default="{ row }">{{ money(row.base_salary, row.amount_masked) }}</template>
          </el-table-column>
          <el-table-column label="应发合计" width="130">
            <template #default="{ row }">{{ money(row.earning_total, row.amount_masked) }}</template>
          </el-table-column>
          <el-table-column label="扣款合计" width="130">
            <template #default="{ row }">{{ money(row.deduction_total, row.amount_masked) }}</template>
          </el-table-column>
          <el-table-column label="税前工资" width="130">
            <template #default="{ row }">{{ money(row.gross_pay, row.amount_masked) }}</template>
          </el-table-column>
          <el-table-column label="实发工资" width="130">
            <template #default="{ row }">
              <strong>{{ money(row.net_pay, row.amount_masked) }}</strong>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ recStatusLabel(row.rec_status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>
