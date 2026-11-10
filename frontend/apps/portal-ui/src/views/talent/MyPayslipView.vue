<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { GapNotice, PageHeader } from '@fenglin/ui-admin'
import {
  talentApi,
  type TalentId,
  type TalentPayPeriod,
  type TalentPayrollRecord,
} from '@fenglin/api-client'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const periods = ref<TalentPayPeriod[]>([])
const selectedPeriodId = ref<TalentId | ''>('')
const myRecords = ref<TalentPayrollRecord[]>([])
const loading = ref(false)
const recordLoading = ref(false)
const error = ref('')
const recordError = ref('')

const gapItems = [
  '后端暂无员工自助薪酬接口（没有 /payroll/slips/my）：薪酬周期与核算明细仅提供管理端接口，需要 talent:admin:payroll:list。',
  '金额字段另需 talent:payroll:amount:view：未持有时服务端返回 null 且 amount_masked=true，页面显示“已脱敏”，不返回完整金额。',
  '本页仍按登录用户 ID 在核算明细中过滤本人记录；普通员工账号调用会返回真实 403。缺少用户与员工档案的绑定关系，暂无法做服务端本人收敛。',
  '发放单 /payroll/slips 是按周期汇总的，不含员工个人维度，也没有工资条 PDF 下载接口，因此本页不展示汇总发放单。',
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
    const page = await talentApi.payroll.periods({ page: 1, size: 50 })
    periods.value = page.records
    if (!selectedPeriodId.value && page.records.length) {
      selectedPeriodId.value = page.records[0].id
    }
    await loadRecords()
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  if (!selectedPeriodId.value) {
    myRecords.value = []
    return
  }
  if (!auth.userId) {
    recordError.value = '当前令牌未返回用户 ID，无法定位本人薪酬记录'
    return
  }
  recordLoading.value = true
  recordError.value = ''
  try {
    const page = await talentApi.payroll.records(selectedPeriodId.value, { page: 1, size: 200 })
    myRecords.value = page.records.filter((item) => String(item.employee_id) === String(auth.userId))
  } catch (err) {
    recordError.value = describeError(err)
    myRecords.value = []
  } finally {
    recordLoading.value = false
  }
}

onMounted(loadPeriods)
</script>

<template>
  <div>
    <PageHeader module="云才人才" title="我的薪酬单" description="按薪资周期查看本人核算明细；数据直接来自薪酬核算接口。" />

    <GapNotice :items="gapItems" />

    <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <el-select
          v-model="selectedPeriodId"
          placeholder="选择薪资周期"
          style="width: 260px"
          @change="loadRecords"
        >
          <el-option
            v-for="period in periods"
            :key="String(period.id)"
            :label="`${period.period_code}（${period.start_date} ~ ${period.end_date}）`"
            :value="period.id"
          />
        </el-select>
        <el-button :loading="loading || recordLoading" @click="loadPeriods">刷新</el-button>
        <span class="fl-muted">{{ auth.displayName }}</span>
      </div>

      <el-alert
        v-if="recordError"
        type="error"
        :closable="false"
        show-icon
        :title="recordError"
        style="margin-bottom: 12px"
      />

      <div v-loading="recordLoading">
        <el-empty v-if="!myRecords.length && !recordError" description="该周期暂无本人薪酬记录" :image-size="80" />
        <el-table v-else :data="myRecords" border stripe>
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
