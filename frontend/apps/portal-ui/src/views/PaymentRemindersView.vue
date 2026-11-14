<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { crmApi, type PaymentReminderView } from '@fenglin/api-client'
import { formatMoney } from '@fenglin/ui-admin'
import { useAuthStore } from '@/stores/auth'
import { describeError } from '@/utils/error'

const auth = useAuthStore()
const rows = ref<PaymentReminderView[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const query = reactive({ upcoming_days: 30, page: 1, size: 10 })

function stateLabel(value?: string) {
  if (value === 'OVERDUE') return '已逾期'
  if (value === 'DUE_SOON') return '即将到期'
  return value || '-'
}
function stateTag(value?: string) {
  return value === 'OVERDUE' ? 'danger' : 'warning'
}
function flagLabel(value?: string) {
  const map: Record<string, string> = { NONE: '未催收', REMINDED: '已提醒', PROMISED: '承诺回款', ESCALATED: '已升级' }
  return value ? map[value] ?? value : '-'
}

async function load() {
  if (!auth.userId) {
    error.value = '登录上下文缺少用户 ID，无法过滤“我的回款提醒”'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await crmApi.payments.myReminders({
      upcoming_days: query.upcoming_days,
      page: query.page,
      size: query.size,
    })
    rows.value = result.records
    total.value = result.total
  } catch (err) {
    error.value = describeError(err)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="提醒口径"
      description="后端按“未结清且到期日不晚于未来 N 天（含已逾期）”返回；本页只展示当前账号负责的合同回款计划，不伪造数据。"
      style="margin-bottom: 12px"
    />

    <el-card shadow="never">
      <div class="fl-toolbar">
        <span class="fl-muted">未来天数</span>
        <el-select v-model="query.upcoming_days" style="width: 120px" @change="query.page = 1; load()">
          <el-option label="7 天" :value="7" />
          <el-option label="30 天" :value="30" />
          <el-option label="60 天" :value="60" />
          <el-option label="90 天" :value="90" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <span class="fl-muted">负责人：{{ auth.displayName }} · 共 {{ total }} 条</span>
      </div>

      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" style="margin-bottom: 12px" />

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="contract_no" label="合同编号" width="160" />
        <el-table-column prop="customer_name" label="客户" min-width="160" />
        <el-table-column prop="period_no" label="期次" width="80" />
        <el-table-column prop="due_date" label="到期日" width="120" />
        <el-table-column prop="amount" label="计划金额" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="paid_amount" label="已回款" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.paid_amount) }}</template>
        </el-table-column>
        <el-table-column prop="remaining_amount" label="未回款" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.remaining_amount) }}</template>
        </el-table-column>
        <el-table-column label="提醒状态" width="110">
          <template #default="{ row }">
            <el-tag :type="stateTag(row.state)" size="small">{{ stateLabel(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="overdue_days" label="逾期天数" width="90" />
        <el-table-column label="催收状态" width="110">
          <template #default="{ row }">{{ flagLabel(row.collection_flag) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="load"
      />
    </el-card>
  </div>
</template>
